package io.github.bbzq.feats.hook

import android.net.Uri
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

internal object ShareLinkPurifier {
    fun purifyText(text: String, transformAv: Boolean): String =
        URL_REGEX.replace(text) { match ->
            val raw = match.value
            val suffix = raw.takeLastWhile { it in TRAILING_PUNCTUATION }
            val url = raw.dropLast(suffix.length)
            purifyLink(url, transformAv) + suffix
        }

    fun purifyLink(url: String, transformAv: Boolean): String {
        val normalized = url.trim()
        if (normalized.isEmpty()) return url
        if (normalized.isBilibiliShortLink()) {
            val shortLink = normalized.withoutQueryAndFragment()
            val resolved = resolveShortLink(shortLink) ?: return shortLink
            return transformUrl(resolved, transformAv)
        }
        return transformUrl(normalized, transformAv)
    }

    fun containsShortLink(text: String): Boolean =
        URL_REGEX.findAll(text).any { it.value.trimEnd { c -> c in TRAILING_PUNCTUATION }.isBilibiliShortLink() }

    // Re-runs purifyText off the main thread so short links can be expanded, and reports the
    // result only when it differs from [text].
    fun purifyTextAsync(text: String, transformAv: Boolean, onPurified: (String) -> Unit) {
        resolveExecutor.execute {
            runCatching {
                val purified = purifyText(text, transformAv)
                if (purified != text) onPurified(purified)
            }
        }
    }

    // b23.tv/bili2233.cn hide the tracking parameters behind a 302 redirect, so the short link has
    // to be expanded before it can be purified. That needs a network request: off the main thread
    // (e.g. while the share API response is parsed) it runs inline, while on the main thread only
    // the cache is consulted and a background lookup is queued for the next call, to avoid an ANR.
    private fun resolveShortLink(shortLink: String): String? {
        resolvedShortLinks[shortLink]?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (pendingShortLinks.add(shortLink)) {
                resolveExecutor.execute {
                    runCatching { fetchShortLinkTarget(shortLink) }
                    pendingShortLinks.remove(shortLink)
                }
            }
            return null
        }
        return runCatching { fetchShortLinkTarget(shortLink) }.getOrNull()
    }

    private fun fetchShortLinkTarget(shortLink: String): String? {
        resolvedShortLinks[shortLink]?.let { return it }
        val conn = URL(shortLink).openConnection() as? HttpURLConnection ?: return null
        val location = try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = RESOLVE_TIMEOUT_MS
            conn.readTimeout = RESOLVE_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "BiliApp/7.44.0")
            if (conn.responseCode !in 300..399) return null
            conn.getHeaderField("Location")
        } finally {
            conn.disconnect()
        }
        val target = location?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val host = runCatching { Uri.parse(target).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        if (!host.isBilibiliHost()) return null
        if (resolvedShortLinks.size >= MAX_RESOLVED_SHORT_LINKS) resolvedShortLinks.clear()
        resolvedShortLinks[shortLink] = target
        return target
    }

    private fun transformUrl(url: String, transformAv: Boolean): String {
        val target = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = target.host?.lowercase(Locale.ROOT).orEmpty()
        if (!host.isBilibiliHost()) return url

        val bv = target.pathSegments.firstOrNull { it.isBvId() }
        val av = bv?.takeIf { transformAv }?.let { "av${bv2av(it)}" }
        val builder = target.buildUpon()
            .clearQuery()
            .fragment(null)

        if (av != null) {
            builder.encodedPath(target.encodedPath.orEmpty().replace(bv, av))
        }

        appendAllowedQuery(target, builder)
        builder.appendQueryParameter(UNIQUE_KEY, BBZQ_UNIQUE_KEY)
        return builder.build().toString()
    }

    private fun appendAllowedQuery(source: Uri, target: Uri.Builder) {
        val appended = linkedSetOf<String>()
        KEEP_QUERY_KEYS.forEach { key ->
            source.getQueryParameter(key)
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    target.appendQueryParameter(key, value)
                    appended += key
                }
        }

        source.getQueryParameter(START_PROGRESS)
            ?.takeIf { it.isNotBlank() }
            ?.let { progress ->
                target.appendQueryParameter(START_PROGRESS, progress)
                if (TIME !in appended) {
                    progress.toLongOrNull()
                        ?.let { target.appendQueryParameter(TIME, (it / 1000L).toString()) }
                }
            }
    }

    private fun String.isBilibiliShortLink(): Boolean {
        val host = runCatching { Uri.parse(this).host?.lowercase(Locale.ROOT) }.getOrNull()
        return host == "b23.tv" || host == "bili2233.cn"
    }

    private fun String.withoutQueryAndFragment(): String =
        runCatching { Uri.parse(this).buildUpon().query(null).fragment(null).build().toString() }
            .getOrDefault(this)

    private fun String.isBilibiliHost(): Boolean =
        this == "bilibili.com" || endsWith(".bilibili.com")

    private fun String.isBvId(): Boolean =
        length == BV_ID_LENGTH && startsWith("BV") && all { it.isLetterOrDigit() }

    private fun bv2av(bv: String): Long {
        var result = 0L
        BV_POSITIONS.forEachIndexed { index, position ->
            result += (BV_TABLE[bv[position]] ?: 0) * pow58(index)
        }
        return result.and(BV_MASK).xor(BV_XOR)
    }

    private fun pow58(index: Int): Long {
        var result = 1L
        repeat(index) { result *= 58L }
        return result
    }

    private const val BV_ID_LENGTH = 12
    private const val BV_MASK = 2251799813685247L
    private const val BV_XOR = 23442827791579L
    private const val UNIQUE_KEY = "unique_k"
    private const val BBZQ_UNIQUE_KEY = "2333"
    private const val START_PROGRESS = "start_progress"
    private const val TIME = "t"
    private const val RESOLVE_TIMEOUT_MS = 2000
    private const val MAX_RESOLVED_SHORT_LINKS = 64

    private val resolvedShortLinks = ConcurrentHashMap<String, String>()
    private val pendingShortLinks: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val resolveExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BBZQ-ShareLink").apply { isDaemon = true }
    }

    private val URL_REGEX = Regex("""https?://\S+""")
    private val KEEP_QUERY_KEYS = listOf("p", TIME)
    private val TRAILING_PUNCTUATION = setOf(
        ')',
        ']',
        '}',
        '>',
        ',',
        '.',
        ';',
        ':',
        '!',
        '?',
        '，',
        '。',
        '；',
        '：',
        '！',
        '？',
        '、',
        '「',
        '」',
        '（',
        '）',
    )
    private val BV_POSITIONS = intArrayOf(11, 10, 3, 8, 4, 6, 5, 7, 9)
    private val BV_TABLE = HashMap<Char, Int>().apply {
        "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
            .forEachIndexed { index, char -> this[char] = index }
    }
}

