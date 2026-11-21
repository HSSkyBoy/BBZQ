package io.github.bbzq.feats

import android.content.Context
import io.github.bbzq.ModuleSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Picks the fastest CDN line for a resolver playurl result by downloading the first few hundred KB
 * of the same segment from each candidate host.
 *
 * 同一個分片在各 UPOS 鏡像上的位元組完全相同、簽名互通，所以可以直接換 host 比速度；
 * Akamai 等非 UPOS host 只認發給自己的位址，只能用伺服器下發的 backup 位址比。
 * 平時用歷史測速結果選線並在背景補測（不拖慢起播）；完全沒有歷史時才同步測一輪。
 */
object ResolverCdnSelector {
    private const val PROBE_BYTES = 512 * 1024
    private const val PROBE_TIMEOUT_MILLIS = 2_500L
    private const val MAX_PROBES = 4
    private const val PROBE_INTERVAL_MILLIS = 10 * 60_000L
    private const val HISTORY_MAX_AGE_MILLIS = 3 * 24 * 60 * 60_000L
    private const val MIN_SAMPLE_BYTES = 16 * 1024
    private const val PREFS_NAME = "bbzq_resolver_cdn"
    private const val PREF_SCORES = "scores"
    private const val PREF_UPDATED_AT = "updated_at"

    private val uposHostRegex = Regex("^upos-[a-z0-9-]+\\.bilivideo\\.com$")

    private val mirrors: List<String> by lazy {
        ModuleSettings.cdnEndpoints.map { it.host.lowercase() }.filter(::isUposHost)
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .callTimeout(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val executor by lazy { Executors.newCachedThreadPool() }
    private val background by lazy { Executors.newSingleThreadExecutor() }
    private val lastProbeAt = ConcurrentHashMap<String, Long>()
    private val lock = Any()

    /** host -> Mbps；首次使用時從宿主私有 prefs 載入。 */
    @Volatile
    private var scores: MutableMap<String, Double>? = null

    private class Candidate(val host: String, val url: String, val issued: Boolean)

    private class Probe(val host: String, val millis: Long, val bytes: Int)

    /** The host to put streams of [result] on, or null to keep what the server gave. */
    fun choose(context: Context, result: JSONObject, log: (String, Throwable?) -> Unit): String? {
        val sample = sampleVideo(result) ?: return null
        val candidates = candidates(sample.first, sample.second)
        if (candidates.size < 2) return null
        val history = loadScores(context)

        val best = candidates.filter { history.containsKey(it.host) }.maxByOrNull { history.getValue(it.host) }
        if (best != null) {
            probeInBackground(context, candidates, history, log)
            log("ResolverCdn: picked ${best.host} from history (${"%.1f".format(history.getValue(best.host))} Mbps)", null)
            return best.host.takeIf { it != hostOf(sample.first) }
        }

        // 沒有任何歷史：同步測一輪，最多拖慢起播 PROBE_TIMEOUT_MILLIS
        val targets = pick(candidates, history)
        val now = System.currentTimeMillis()
        targets.forEach { lastProbeAt[it.host] = now }
        val probes = probeAll(targets, log)
        if (probes.isEmpty()) return null
        record(context, probes, log)
        val fastest = probes.minByOrNull { it.millis * PROBE_BYTES.toDouble() / it.bytes } ?: return null
        log("ResolverCdn: picked ${fastest.host} after probing ${probes.size} line(s)", null)
        return fastest.host.takeIf { it != hostOf(sample.first) }
    }

    /**
     * The address of one stream on [host]: an address the server issued for that host, else the
     * base address moved to it when both are UPOS mirrors, else null (keep the original).
     */
    fun rehost(base: String, backups: List<String>, host: String): String? {
        (listOf(base) + backups).firstOrNull { hostOf(it) == host }?.let { return it }
        return if (isUposHost(hostOf(base)) && isUposHost(host)) replaceHost(base, host) else null
    }

    private fun sampleVideo(result: JSONObject): Pair<String, List<String>>? {
        val videos = result.optJSONObject("dash")?.optJSONArray("video") ?: return null
        for (i in 0 until videos.length()) {
            val video = videos.optJSONObject(i) ?: continue
            val base = video.optString("base_url").ifEmpty { video.optString("baseUrl") }
            if (!base.startsWith("http")) continue
            val array = video.optJSONArray("backup_url") ?: video.optJSONArray("backupUrl")
            val backups = (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it)?.takeIf(String::isNotEmpty) }
            return base to backups
        }
        return null
    }

    private fun candidates(base: String, backups: List<String>): List<Candidate> {
        val byHost = LinkedHashMap<String, Candidate>()
        (listOf(base) + backups).forEach { url ->
            val host = hostOf(url)
            if (host.isNotEmpty() && host !in byHost) byHost[host] = Candidate(host, url, issued = true)
        }
        if (isUposHost(hostOf(base))) {
            mirrors.forEach { host ->
                if (host !in byHost) byHost[host] = Candidate(host, replaceHost(base, host), issued = false)
            }
        }
        return byHost.values.toList()
    }

    /** 下發的位址優先佔名額；剩下的給歷史分高的鏡像，再輪到還沒測過的。 */
    private fun pick(candidates: List<Candidate>, history: Map<String, Double>): List<Candidate> {
        if (candidates.size <= MAX_PROBES) return candidates
        val issued = candidates.filter { it.issued }
        val mirrorsByScore = candidates.filterNot { it.issued }.sortedByDescending { history[it.host] ?: -1.0 }
        return (issued + mirrorsByScore).take(MAX_PROBES)
    }

    private fun probeInBackground(
        context: Context,
        candidates: List<Candidate>,
        history: Map<String, Double>,
        log: (String, Throwable?) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        // 剛測過的線路不重測，讓名額輪到其他鏡像，久了整個鏡像池都會有分數
        val fresh = candidates.filter { now - (lastProbeAt[it.host] ?: 0L) >= PROBE_INTERVAL_MILLIS }
        val targets = pick(fresh, history)
        if (targets.isEmpty()) return
        targets.forEach { lastProbeAt[it.host] = now }
        background.execute {
            runCatching {
                val probes = probeAll(targets, log)
                if (probes.isNotEmpty()) record(context, probes, log)
            }.onFailure { log("ResolverCdn: background probe failed", it) }
        }
    }

    private fun probeAll(targets: List<Candidate>, log: (String, Throwable?) -> Unit): List<Probe> =
        targets.map { target -> executor.submit<Probe?> { probe(target, log) } }
            .mapNotNull { runCatching { it.get(PROBE_TIMEOUT_MILLIS * 2, TimeUnit.MILLISECONDS) }.getOrNull() }

    /** 從送出請求算到最後一塊資料抵達，首位元組延遲也算在內：播放器每取一段都要付這筆。 */
    private fun probe(target: Candidate, log: (String, Throwable?) -> Unit): Probe? {
        val started = System.nanoTime()
        return runCatching {
            val request = Request.Builder()
                .url(target.url)
                .header("Range", "bytes=0-${PROBE_BYTES - 1}")
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val stream = response.body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0
                while (total < PROBE_BYTES) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, PROBE_BYTES - total))
                    if (read <= 0) break
                    total += read
                }
                val millis = (System.nanoTime() - started) / 1_000_000
                if (millis <= 0 || total < MIN_SAMPLE_BYTES) null else Probe(target.host, millis, total)
            }
        }.getOrElse {
            log("ResolverCdn: probe ${target.host} failed: ${it.message}", null)
            null
        }
    }

    private fun loadScores(context: Context): Map<String, Double> = synchronized(lock) { loadLocked(context).toMap() }

    private fun loadLocked(context: Context): MutableMap<String, Double> {
        scores?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = mutableMapOf<String, Double>()
        if (System.currentTimeMillis() - prefs.getLong(PREF_UPDATED_AT, 0L) < HISTORY_MAX_AGE_MILLIS) {
            runCatching {
                val json = JSONObject(prefs.getString(PREF_SCORES, null) ?: "{}")
                json.keys().forEach { key -> loaded[key] = json.optDouble(key) }
            }
        }
        scores = loaded
        return loaded
    }

    private fun record(context: Context, probes: List<Probe>, log: (String, Throwable?) -> Unit) {
        synchronized(lock) {
            val map = loadLocked(context)
            probes.forEach { probe ->
                val mbps = probe.bytes * 8.0 / probe.millis / 1000.0
                log("ResolverCdn: ${probe.host} ${probe.bytes / 1024} KB in ${probe.millis} ms (${"%.1f".format(mbps)} Mbps)", null)
                // 對半平均：跟得上線路變化，又不會被單次抖動帶走
                map[probe.host] = map[probe.host]?.let { (it + mbps) / 2 } ?: mbps
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(PREF_SCORES, JSONObject(map as Map<*, *>).toString())
                .putLong(PREF_UPDATED_AT, System.currentTimeMillis())
                .apply()
        }
    }

    private fun isUposHost(host: String): Boolean = uposHostRegex.matches(host)

    private fun hostOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return ""
        return url.substring(schemeEnd + 3).substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
    }

    private fun replaceHost(url: String, host: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return url
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return url.substring(0, schemeEnd + 3) + host + if (pathStart < 0) "" else url.substring(pathStart)
    }

    private const val USER_AGENT = "Bilibili Freedoooooom/MarkII"
}
