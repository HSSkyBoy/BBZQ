package io.github.bbzq.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.hook.CustomCdnProcessor
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CountDownLatch
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

    private var alertDialog: AlertDialog? = null
    private var statusHeader: TextView? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(25, 5, TimeUnit.MINUTES))
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
            val root = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(10), dp(16), dp(10))

                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    tag = "col"
                }

                val title = TextView(context).apply {
                    textSize = 14f
                    setTextColor(context.getColor(R.color.title_text))
                    tag = "title"
                }
                val sub = TextView(context).apply {
                    textSize = 11f
                    setTextColor(context.getColor(R.color.summary_text))
                    tag = "sub"
                }
                textCol.addView(title)
                textCol.addView(sub)
                addView(textCol)

                val statusCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                    tag = "statusCol"
                }
                val status = TextView(context).apply {
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.title_text))
                    tag = "status"
                }
                val ping = TextView(context).apply {
                    textSize = 11f
                    setTextColor(context.getColor(R.color.summary_text))
                    tag = "ping"
                }
                statusCol.addView(status)
                statusCol.addView(ping)
                addView(statusCol)

                val progress = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                    tag = "progress"
                    visibility = View.GONE
                    layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                        marginStart = dp(8)
                    }
                }
                addView(progress)
            }

            val title = root.findViewWithTag<TextView>("title")
            val sub = root.findViewWithTag<TextView>("sub")
            val status = root.findViewWithTag<TextView>("status")
            val ping = root.findViewWithTag<TextView>("ping")
            val progress = root.findViewWithTag<ProgressBar>("progress")

            title?.text = item.name
            sub?.text = if (item.host.isBlank()) "直连官方 UPOS" else item.host
            status?.text = item.statusText
            progress?.visibility = if (item.isRunning) View.VISIBLE else View.GONE

            if (item.pingMs > 0) {
                ping?.text = context.getString(R.string.cdn_speed_test_ping, item.pingMs)
                ping?.visibility = View.VISIBLE
            } else {
                ping?.visibility = View.GONE
            }

            if (item.speedMb > 0) {
                status?.setTextColor(0xFF00A050.toInt())
            } else if (item.isDone && item.speedMb <= 0) {
                status?.setTextColor(context.getColor(R.color.summary_text))
            } else {
                status?.setTextColor(context.getColor(R.color.title_text))
            }

            return root
        }
    }

    fun show() {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(8))
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
            .setNeutralButton(R.string.cdn_speed_test_apply_chain, null)
            .setOnDismissListener {
                stopSpeedTest()
            }
            .create()

        alertDialog = dialog
        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.setOnClickListener {
            showParamsDialog()
        }
        dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
            val fastNodes = items.filter { it.speedMb > 0 && it.host.isNotBlank() }
            if (fastNodes.isNotEmpty()) {
                applyAsChain(fastNodes)
                dialog.dismiss()
            } else {
                Toast.makeText(context, context.getString(R.string.cdn_speed_test_no_result), Toast.LENGTH_SHORT).show()
            }
        }

        startSpeedTest()
    }

    private fun showParamsDialog() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.cdn_speed_test_size_label) + " (MiB):"
            textSize = 14f
        })
        val sizeEdit = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(ModuleSettings.getCdnSpeedTestSizeMb(prefs).toString())
        }
        root.addView(sizeEdit)

        root.addView(TextView(context).apply {
            text = context.getString(R.string.cdn_speed_test_warmup_label) + " (MiB):"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })
        val warmupEdit = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(ModuleSettings.getCdnSpeedTestWarmupMb(prefs).toString())
        }
        root.addView(warmupEdit)

        root.addView(TextView(context).apply {
            text = context.getString(R.string.cdn_speed_test_cooldown_label) + " (秒):"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })
        val cooldownEdit = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(ModuleSettings.getCdnSpeedTestCooldownSec(prefs).toString())
        }
        root.addView(cooldownEdit)

        root.addView(TextView(context).apply {
            text = context.getString(R.string.cdn_speed_test_mode_label) + ":"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
        })

        val radioGroup = RadioGroup(context)
        val rbParallel = RadioButton(context).apply {
            text = context.getString(R.string.cdn_speed_test_mode_parallel) + " (" + context.getString(R.string.cdn_speed_test_mode_parallel_hint) + ")"
            isChecked = ModuleSettings.isCdnSpeedTestParallel(prefs)
        }
        val rbSequential = RadioButton(context).apply {
            text = context.getString(R.string.cdn_speed_test_mode_sequential) + " (" + context.getString(R.string.cdn_speed_test_mode_sequential_hint) + ")"
            isChecked = !ModuleSettings.isCdnSpeedTestParallel(prefs)
        }
        radioGroup.addView(rbParallel)
        radioGroup.addView(rbSequential)
        root.addView(radioGroup)

        AlertDialog.Builder(context)
            .setTitle(R.string.cdn_speed_test_params_title)
            .setView(root)
            .setPositiveButton(R.string.cdn_speed_test_start) { _, _ ->
                val size = sizeEdit.text.toString().toIntOrNull() ?: 16
                val warmup = warmupEdit.text.toString().toIntOrNull() ?: 4
                val cooldown = cooldownEdit.text.toString().toIntOrNull() ?: 0
                val parallel = rbParallel.isChecked

                prefs.edit()
                    .putInt(ModuleSettings.KEY_CDN_SPEED_TEST_SIZE_MB, size)
                    .putInt(ModuleSettings.KEY_CDN_SPEED_TEST_WARMUP_MB, warmup)
                    .putInt(ModuleSettings.KEY_CDN_SPEED_TEST_COOLDOWN_SEC, cooldown)
                    .putBoolean(ModuleSettings.KEY_CDN_SPEED_TEST_PARALLEL, parallel)
                    .apply()

                startSpeedTest()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun applyNode(node: CdnSpeedResult) {
        if (node.host.isBlank()) {
            prefs.edit()
                .remove(ModuleSettings.KEY_CUSTOM_CDN_HOST)
                .putBoolean(ModuleSettings.KEY_CUSTOM_CDN_ENABLED, false)
                .apply()
            Toast.makeText(context, "已应用系统默认官方直连", Toast.LENGTH_SHORT).show()
        } else {
            prefs.edit()
                .putString(ModuleSettings.KEY_CUSTOM_CDN_HOST, node.host)
                .putBoolean(ModuleSettings.KEY_CUSTOM_CDN_ENABLED, true)
                .apply()
            Toast.makeText(context, context.getString(R.string.cdn_speed_test_applied, node.name), Toast.LENGTH_SHORT).show()
        }
        onApplied()
    }

    private fun applyAsChain(nodes: List<CdnSpeedResult>) {
        val topHosts = nodes.sortedByDescending { it.speedMb }
            .take(3)
            .map { it.host }

        val choices = arrayOf(
            context.getString(R.string.cdn_speed_test_apply_to_wifi),
            context.getString(R.string.cdn_speed_test_apply_to_cellular),
            context.getString(R.string.cdn_speed_test_apply_to_both),
        )

        AlertDialog.Builder(context)
            .setTitle(R.string.cdn_speed_test_apply_to_title)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> {
                        ModuleSettings.saveCdnPriorityList(prefs, ModuleSettings.KEY_CDN_WIFI_PRIORITY, topHosts)
                        prefs.edit().putBoolean(ModuleSettings.KEY_CDN_WIFI_ENABLED, true).apply()
                    }
                    1 -> {
                        ModuleSettings.saveCdnPriorityList(prefs, ModuleSettings.KEY_CDN_CELLULAR_PRIORITY, topHosts)
                        prefs.edit().putBoolean(ModuleSettings.KEY_CDN_CELLULAR_ENABLED, true).apply()
                    }
                    2 -> {
                        ModuleSettings.saveCdnPriorityList(prefs, ModuleSettings.KEY_CDN_WIFI_PRIORITY, topHosts)
                        ModuleSettings.saveCdnPriorityList(prefs, ModuleSettings.KEY_CDN_CELLULAR_PRIORITY, topHosts)
                        prefs.edit()
                            .putBoolean(ModuleSettings.KEY_CDN_WIFI_ENABLED, true)
                            .putBoolean(ModuleSettings.KEY_CDN_CELLULAR_ENABLED, true)
                            .apply()
                    }
                }
                val hostNames = topHosts.map { h -> ModuleSettings.cdnEndpoints.firstOrNull { it.host == h }?.name ?: h }
                Toast.makeText(context, context.getString(R.string.cdn_speed_test_chain_applied, hostNames.joinToString(" → ")), Toast.LENGTH_LONG).show()
                onApplied()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun stopSpeedTest() {
        isCancelled.set(true)
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

        val isParallel = ModuleSettings.isCdnSpeedTestParallel(prefs)
        val sizeMb = ModuleSettings.getCdnSpeedTestSizeMb(prefs)
        val warmupMb = ModuleSettings.getCdnSpeedTestWarmupMb(prefs)
        val cooldownSec = ModuleSettings.getCdnSpeedTestCooldownSec(prefs)

        val pool = if (isParallel) {
            Executors.newFixedThreadPool(minOf(items.size, 16))
        } else {
            Executors.newSingleThreadExecutor()
        }
        executor = pool

        pool.execute {
            val sampleMediaUrl = fetchSamplePlayUrl()
            val testingItems = ArrayList(items)

            if (isParallel) {
                val latch = CountDownLatch(testingItems.size)
                for (item in testingItems) {
                    if (isCancelled.get()) break
                    pool.execute {
                        try {
                            if (!isCancelled.get()) {
                                item.isRunning = true
                                mainHandler.post { adapter.notifyDataSetChanged() }
                                testEndpoint(item, sampleMediaUrl, sizeMb, warmupMb)
                                mainHandler.post { adapter.notifyDataSetChanged() }
                            }
                        } finally {
                            latch.countDown()
                        }
                    }
                }
                runCatching { latch.await(60, TimeUnit.SECONDS) }
            } else {
                for (item in testingItems) {
                    if (isCancelled.get()) break
                    item.isRunning = true
                    mainHandler.post { adapter.notifyDataSetChanged() }
                    testEndpoint(item, sampleMediaUrl, sizeMb, warmupMb)
                    mainHandler.post { adapter.notifyDataSetChanged() }

                    if (cooldownSec > 0 && !isCancelled.get()) {
                        runCatching { Thread.sleep(cooldownSec * 1000L) }
                    }
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

    private fun testEndpoint(item: CdnSpeedResult, sampleMediaUrl: String?, sizeMb: Int, warmupMb: Int) {
        try {
            if (!sampleMediaUrl.isNullOrBlank()) {
                val downloadUrl = if (item.host.isBlank()) {
                    sampleMediaUrl
                } else {
                    CustomCdnProcessor.replaceHost(sampleMediaUrl, item.host)
                }
                val targetSize = sizeMb * 1024 * 1024L
                val warmupBytes = warmupMb * 1024 * 1024L
                val maxTestTimeUs = 8_000_000L

                val req = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.2 Safari/605.1.15")
                    .header("Referer", "https://www.bilibili.com/")
                    .build()

                val call = client.newCall(req)
                val reqStartMs = System.currentTimeMillis()
                val response = call.execute()
                val pingMs = System.currentTimeMillis() - reqStartMs
                item.pingMs = maxOf(1L, pingMs)

                if (response.isSuccessful) {
                    val stream = response.body?.byteStream()
                    if (stream != null) {
                        val buffer = ByteArray(65536)
                        var totalBytes = 0L
                        var timedBytes = 0L

                        val streamStartUs = System.nanoTime() / 1000
                        var timedStartUs = streamStartUs
                        var peakSpeedMBs = 0.0

                        while (!isCancelled.get()) {
                            val read = stream.read(buffer)
                            if (read <= 0) break
                            totalBytes += read

                            if (totalBytes > warmupBytes) {
                                if (timedBytes == 0L) {
                                    timedStartUs = System.nanoTime() / 1000
                                }
                                timedBytes += read
                            }

                            val nowUs = System.nanoTime() / 1000
                            val elapsedUs = nowUs - streamStartUs
                            if (totalBytes >= targetSize || elapsedUs >= maxTestTimeUs) {
                                break
                            }
                        }
                        stream.close()

                        val timedDurationUs = (System.nanoTime() / 1000) - timedStartUs
                        val avgSpeedMBs = if (timedDurationUs > 0 && timedBytes > 0) timedBytes.toDouble() / timedDurationUs.toDouble() else (if (totalBytes > 0) totalBytes.toDouble() / ((System.nanoTime() / 1000) - streamStartUs).toDouble() else 0.0)
                        val finalSpeedMBs = avgSpeedMBs

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
                    } else {
                        item.statusText = "空响应体"
                    }
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

        return null
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
