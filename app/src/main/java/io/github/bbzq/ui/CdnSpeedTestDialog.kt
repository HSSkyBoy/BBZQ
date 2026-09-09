package io.github.bbzq.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.hook.CustomCdnProcessor
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class CdnSpeedResult(
    val name: String,
    val host: String,
    var pingMs: Long = -1,
    var speedMb: Double = -1.0,
    var speedKbps: Long = -1,
    var statusText: String = "等待中",
    var isDone: Boolean = false,
    var isRunning: Boolean = false,
)

class CdnSpeedTestDialog(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onApplied: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private val isCancelled = AtomicBoolean(false)

    @Volatile
    private var currentCall: Call? = null

    private var alertDialog: AlertDialog? = null
    private var statusHeader: TextView? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .connectTimeout(3500, TimeUnit.MILLISECONDS)
            .readTimeout(5000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun createInitialItems(): MutableList<CdnSpeedResult> = buildList {
        add(CdnSpeedResult(name = "系统默认（官方直连）", host = ""))
        addAll(ModuleSettings.cdnEndpoints.map {
            CdnSpeedResult(name = it.name, host = it.host)
        })
    }.toMutableList()

    private val items = createInitialItems()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): CdnSpeedResult = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = getItem(position)
            val isCustomEnabled = ModuleSettings.isCustomCdnEnabled(prefs)
            val currentHost = ModuleSettings.getCustomCdnHost(prefs)
            val isSelected = if (item.host.isBlank()) {
                !isCustomEnabled
            } else {
                isCustomEnabled && item.host.equals(currentHost, ignoreCase = true)
            }

            val layout = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            layout.removeAllViews()

            val leftLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleView = TextView(context).apply {
                text = if (isSelected) "★ ${item.name}" else item.name
                textSize = 15f
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isSelected) context.getColor(R.color.accent_pink) else context.getColor(R.color.title_text))
            }

            val hostView = TextView(context).apply {
                text = if (item.host.isBlank()) "直连 Bilibili 官方动态调度节点" else item.host
                textSize = 12f
                setTextColor(context.getColor(R.color.summary_text))
                setPadding(0, dp(2), 0, 0)
            }

            leftLayout.addView(titleView)
            leftLayout.addView(hostView)
            layout.addView(leftLayout)

            val rightLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val speedView = TextView(context).apply {
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.END
                when {
                    item.speedMb >= 1.0 -> {
                        text = String.format(Locale.US, "%.2f MB/s", item.speedMb)
                        setTextColor(Color.parseColor("#4CAF50"))
                    }
                    item.speedMb > 0.0 -> {
                        text = String.format(Locale.US, "%.0f KB/s", item.speedMb * 1024)
                        setTextColor(Color.parseColor("#2196F3"))
                    }
                    item.isRunning -> {
                        text = "测速中…"
                        setTextColor(Color.parseColor("#FF9800"))
                    }
                    item.isDone -> {
                        if (item.statusText.isNotBlank() && item.statusText != "等待中") {
                            text = item.statusText
                            setTextColor(Color.parseColor("#9E9E9E"))
                        } else if (item.pingMs >= 0) {
                            text = context.getString(R.string.cdn_speed_test_unavailable)
                            setTextColor(Color.parseColor("#9E9E9E"))
                        } else {
                            text = context.getString(R.string.cdn_speed_test_timeout)
                            setTextColor(Color.parseColor("#F44336"))
                        }
                    }
                    else -> {
                        text = item.statusText
                        setTextColor(context.getColor(R.color.summary_text))
                    }
                }
            }

            val pingView = TextView(context).apply {
                textSize = 11f
                gravity = Gravity.END
                setPadding(0, dp(2), 0, 0)
                if (item.pingMs >= 0) {
                    text = "${item.pingMs} ms"
                    setTextColor(
                        when {
                            item.pingMs < 80 -> Color.parseColor("#4CAF50")
                            item.pingMs < 180 -> Color.parseColor("#FF9800")
                            else -> Color.parseColor("#F44336")
                        }
                    )
                } else {
                    text = ""
                }
            }

            rightLayout.addView(speedView)
            rightLayout.addView(pingView)
            layout.addView(rightLayout)

            return layout
        }
    }

    fun show() {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        statusHeader = TextView(context).apply {
            text = context.getString(R.string.cdn_speed_testing)
            textSize = 13f
            setTextColor(context.getColor(R.color.summary_text))
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        rootLayout.addView(statusHeader)

        val listView = ListView(context).apply {
            adapter = this@CdnSpeedTestDialog.adapter
            dividerHeight = dp(1)
            setOnItemClickListener { _, _, position, _ ->
                val selected = items.getOrNull(position) ?: return@setOnItemClickListener
                applyNode(selected)
                alertDialog?.dismiss()
            }
        }
        rootLayout.addView(listView)

        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.cdn_speed_test_dialog_title)
            .setView(rootLayout)
            .setPositiveButton(R.string.cdn_speed_test_close, null)
            .setNegativeButton(R.string.cdn_speed_test_retest, null)
            .setNeutralButton(R.string.cdn_speed_test_apply_fastest, null)
            .setOnDismissListener {
                stopSpeedTest()
            }
            .create()

        alertDialog = dialog
        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
            startSpeedTest()
        }
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
            val fastest = items.firstOrNull { it.speedMb > 0 }
                ?: items.firstOrNull { it.pingMs > 0 }
            if (fastest != null) {
                applyNode(fastest)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "暂无可用测速结果", Toast.LENGTH_SHORT).show()
            }
        }

        startSpeedTest()
    }

    private fun applyNode(node: CdnSpeedResult) {
        if (node.host.isBlank()) {
            prefs.edit()
                .remove(ModuleSettings.KEY_CUSTOM_CDN_HOST)
                .putBoolean(ModuleSettings.KEY_CUSTOM_CDN_ENABLED, false)
                .apply()
            Toast.makeText(
                context,
                "已应用系统默认官方直连",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            prefs.edit()
                .putString(ModuleSettings.KEY_CUSTOM_CDN_HOST, node.host)
                .putBoolean(ModuleSettings.KEY_CUSTOM_CDN_ENABLED, true)
                .apply()
            Toast.makeText(
                context,
                context.getString(R.string.cdn_speed_test_applied, node.name),
                Toast.LENGTH_SHORT
            ).show()
        }
        onApplied()
    }

    private fun stopSpeedTest() {
        isCancelled.set(true)
        runCatching { currentCall?.cancel() }
        currentCall = null
        runCatching {
            executor?.shutdownNow()
        }
        executor = null
    }

    private fun startSpeedTest() {
        stopSpeedTest()
        isCancelled.set(false)

        items.clear()
        items.addAll(createInitialItems())
        adapter.notifyDataSetChanged()
        statusHeader?.text = context.getString(R.string.cdn_speed_testing)

        val pool = Executors.newSingleThreadExecutor()
        executor = pool

        pool.execute {
            val sampleMediaUrl = fetchSamplePlayUrl()
            val testingItems = ArrayList(items)

            for (item in testingItems) {
                if (isCancelled.get()) break

                item.isRunning = true
                mainHandler.post { adapter.notifyDataSetChanged() }

                testEndpoint(item, sampleMediaUrl)

                if (isCancelled.get()) break

                mainHandler.post {
                    adapter.notifyDataSetChanged()
                }
            }

            if (!isCancelled.get()) {
                mainHandler.post {
                    sortItems()
                    val best = items.firstOrNull { it.speedMb > 0 }
                    if (best != null) {
                        val speedStr = if (best.speedMb >= 1.0) {
                            String.format(Locale.US, "%.2f MB/s", best.speedMb)
                        } else {
                            String.format(Locale.US, "%.0f KB/s", best.speedMb * 1024)
                        }
                        statusHeader?.text = context.getString(R.string.cdn_speed_test_best_recommend, best.name, speedStr)
                    } else {
                        statusHeader?.text = context.getString(R.string.cdn_speed_test_complete)
                    }
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun sortItems() {
        items.sortWith(Comparator { a, b ->
            when {
                a.speedMb > 0 && b.speedMb > 0 -> b.speedMb.compareTo(a.speedMb)
                a.speedMb > 0 -> -1
                b.speedMb > 0 -> 1
                a.pingMs > 0 && b.pingMs > 0 -> a.pingMs.compareTo(b.pingMs)
                a.pingMs > 0 -> -1
                b.pingMs > 0 -> 1
                else -> 0
            }
        })
    }

    private fun testEndpoint(item: CdnSpeedResult, sampleMediaUrl: String?) {
        try {
            if (!sampleMediaUrl.isNullOrBlank()) {
                val downloadUrl = if (item.host.isBlank()) {
                    sampleMediaUrl
                } else {
                    CustomCdnProcessor.replaceHost(sampleMediaUrl, item.host)
                }
                val targetSize = 8 * 1024 * 1024L
                val maxTestTimeUs = 5_000_000L

                val req = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()

                val call = client.newCall(req)
                currentCall = call

                val reqStartMs = System.currentTimeMillis()
                val response = call.execute()
                val pingMs = System.currentTimeMillis() - reqStartMs
                item.pingMs = maxOf(1L, pingMs)

                if (response.isSuccessful) {
                    val stream = response.body.byteStream()
                    val buffer = ByteArray(65536)
                    var totalBytes = 0L

                    val streamStartUs = System.nanoTime() / 1000
                    var windowStartUs = streamStartUs
                    var windowBytes = 0L
                    var peakSpeedMBs = 0.0

                    while (!isCancelled.get()) {
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        windowBytes += read

                        val nowUs = System.nanoTime() / 1000
                        val windowElapsedUs = nowUs - windowStartUs
                        if (windowElapsedUs >= 150_000L) {
                            val curMBs = windowBytes.toDouble() / windowElapsedUs.toDouble()
                            if (curMBs > peakSpeedMBs) {
                                peakSpeedMBs = curMBs
                            }
                            windowBytes = 0L
                            windowStartUs = nowUs
                        }

                        val elapsedUs = nowUs - streamStartUs
                        if (totalBytes >= targetSize || elapsedUs >= maxTestTimeUs) {
                            break
                        }
                    }
                    stream.close()

                    val totalDurationUs = (System.nanoTime() / 1000) - streamStartUs
                    val avgSpeedMBs = if (totalDurationUs > 0) totalBytes.toDouble() / totalDurationUs.toDouble() else 0.0
                    val finalSpeedMBs = maxOf(peakSpeedMBs, avgSpeedMBs)

                    if (totalBytes > 0 && finalSpeedMBs > 0.0) {
                        item.speedMb = finalSpeedMBs
                        item.speedKbps = (finalSpeedMBs * 1024).toLong()
                        item.statusText = if (finalSpeedMBs >= 1.0) {
                            String.format(Locale.US, "%.2f MB/s", finalSpeedMBs)
                        } else {
                            String.format(Locale.US, "%.0f KB/s", finalSpeedMBs * 1024)
                        }
                    } else {
                        item.speedMb = 0.0
                        item.speedKbps = 0
                        item.statusText = "测速失败"
                    }
                    response.close()
                } else if (response.code in 400..499) {
                    item.speedMb = 0.0
                    item.speedKbps = 0
                    item.statusText = "此视频可能无法替换为该CDN"
                    response.close()
                } else {
                    item.speedMb = 0.0
                    item.speedKbps = 0
                    item.statusText = "HTTP ${response.code}"
                    response.close()
                }
            } else {
                item.speedMb = 0.0
                item.speedKbps = 0
                item.statusText = "未获取到测试源"
            }
        } catch (e: Throwable) {
            if (!isCancelled.get()) {
                if (item.pingMs < 0) item.pingMs = -1
                item.speedMb = 0.0
                item.speedKbps = 0
                item.statusText = "连接超时"
            }
        } finally {
            currentCall = null
            item.isRunning = false
            item.isDone = true
        }
    }

    private fun fetchSamplePlayUrl(): String? {
        val dashApis = listOf(
            "https://api.bilibili.com/x/player/playurl?bvid=BV1fK4y1t7hj&cid=196018899&qn=80&fnval=4048&fourk=1",
            "https://api.bilibili.com/x/player/playurl?avid=170001&cid=279786&qn=80&fnval=4048",
            "https://api.bilibili.com/x/player/playurl?avid=8&cid=9686&qn=80&fnval=4048",
        )

        for (apiUrl in dashApis) {
            val result = runCatching {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                val response = client.newCall(req).execute()
                val jsonStr = response.body?.string().orEmpty()
                response.close()

                val json = JSONObject(jsonStr)
                val dash = json.optJSONObject("data")?.optJSONObject("dash") ?: json.optJSONObject("dash")

                val allUrls = mutableListOf<String>()
                val videoArray = dash?.optJSONArray("video")
                if (videoArray != null) {
                    for (i in 0 until videoArray.length()) {
                        val obj = videoArray.optJSONObject(i)
                        obj?.optString("baseUrl")?.takeIf { it.isNotBlank() }?.let { allUrls.add(it) }
                        val backups = obj?.optJSONArray("backupUrl") ?: obj?.optJSONArray("backup_url")
                        if (backups != null) {
                            for (j in 0 until backups.length()) {
                                backups.optString(j).takeIf { it.isNotBlank() }?.let { allUrls.add(it) }
                            }
                        }
                    }
                }
                val audioArray = dash?.optJSONArray("audio")
                if (audioArray != null) {
                    for (i in 0 until audioArray.length()) {
                        val obj = audioArray.optJSONObject(i)
                        obj?.optString("baseUrl")?.takeIf { it.isNotBlank() }?.let { allUrls.add(it) }
                        val backups = obj?.optJSONArray("backupUrl") ?: obj?.optJSONArray("backup_url")
                        if (backups != null) {
                            for (j in 0 until backups.length()) {
                                backups.optString(j).takeIf { it.isNotBlank() }?.let { allUrls.add(it) }
                            }
                        }
                    }
                }

                allUrls.firstOrNull { it.contains("/upgcxcode/") && !CustomCdnProcessor.isPCdn(it) && it.startsWith("http") }
                    ?: allUrls.firstOrNull { !CustomCdnProcessor.isPCdn(it) && it.startsWith("http") }
            }.getOrNull()

            if (!result.isNullOrBlank()) return result
        }

        val html5Apis = listOf(
            "https://api.bilibili.com/x/player/playurl?bvid=BV1fK4y1t7hj&cid=196018899&qn=16&type=mp4&platform=html5",
            "https://api.bilibili.com/x/player/playurl?avid=170001&cid=279786&qn=16&type=mp4&platform=html5",
        )

        for (apiUrl in html5Apis) {
            val result = runCatching {
                val req = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()
                val response = client.newCall(req).execute()
                val jsonStr = response.body?.string().orEmpty()
                response.close()

                val json = JSONObject(jsonStr)
                val durl = json.optJSONObject("data")?.optJSONArray("durl")
                durl?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
            }.getOrNull()

            if (!result.isNullOrBlank()) return result
        }

        return null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
