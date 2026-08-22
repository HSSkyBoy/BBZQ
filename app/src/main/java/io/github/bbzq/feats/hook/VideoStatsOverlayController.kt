package io.github.bbzq.feats.hook

import android.app.Activity
import io.github.bbzq.feats.HostAccountResolver
import android.app.AlertDialog
import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import kotlin.collections.ArrayDeque
import kotlin.math.max

internal class VideoStatsOverlayController(
    private val application: Application,
    private val resolveIdentity: () -> UserWatermarkIdentity,
    private val reportFailure: (String, Throwable) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var topActivity = WeakReference<Activity>(null)
    private var activeStatsContent = WeakReference<LinearLayout>(null)
    @Volatile private var latestStats: VideoStreamStats? = null

    fun install() {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                topActivity = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (topActivity.get() === activity) topActivity.clear()
                if (activeStatsContent.get()?.context === activity) activeStatsContent.clear()
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (topActivity.get() === activity) topActivity.clear()
            }
        })
    }

    fun update(stats: VideoStreamStats) {
        latestStats = stats
        mainHandler.post {
            val activity = topActivity.get()?.takeUnless { it.isFinishing || it.isDestroyed }
                ?: return@post
            if (!isVideoDetailActivity(activity)) return@post
            runCatching {
                activeStatsContent.get()?.let { renderStats(it, stats) }
            }.onFailure { reportFailure("failed to update statistics overlay", it) }
        }
    }

    // The floating icon logic (attachPlayerComponent, StatsIconView etc.) has been removed 
    // as per user request to integrate into the video description hyperlink instead.

    internal fun showStats(activity: Activity) {
        runCatching {
            val identity = resolveIdentity()
            val watermark = listOfNotNull(
                identity.userName.takeIf { it.isNotBlank() },
                identity.uid.takeIf { it.isNotBlank() }?.let { "UID $it" },
            ).joinToString(" · ").ifBlank { "未登录用户 · UID 未知" }
            
            val statsContent = latestStats?.let { createStatsContent(activity, it) }

            if (statsContent != null) {
                activeStatsContent = WeakReference(statsContent)
            }
            val content = FrameLayout(activity).apply {
                setPadding(dp(24), dp(18), dp(24), dp(12))
                setBackgroundColor(Color.rgb(46, 46, 46))
                addView(
                    ScrollView(activity).apply {
                        isFillViewport = true
                        val root = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            if (statsContent != null) addView(statsContent)
                            else addView(statLine(activity, "状态", "暂无流信息，请先播放视频"))
                        }
                        addView(root)
                    }
                )
                addView(RepeatingWatermarkView(activity, watermark))
            }
            val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth)
                .setView(content)
                .setPositiveButton("关闭", null)
                .show()
            dialog.setOnDismissListener { activeStatsContent.clear() }
            dialog.window?.apply {
                setGravity(Gravity.BOTTOM)
                setBackgroundDrawable(
                    GradientDrawable().apply {
                        setColor(Color.rgb(46, 46, 46))
                        cornerRadius = dp(16).toFloat()
                    },
                )
                val metrics = activity.resources.displayMetrics
                setLayout(metrics.widthPixels, (metrics.heightPixels * 0.70f).toInt())
            }
        }.onFailure { reportFailure("failed to show stats dialog", it) }
    }

    internal fun showDownload(activity: Activity) {
        runCatching {
            val identity = resolveIdentity()
            val watermark = listOfNotNull(
                identity.userName.takeIf { it.isNotBlank() },
                identity.uid.takeIf { it.isNotBlank() }?.let { "UID $it" },
            ).joinToString(" · ").ifBlank { "未登录用户 · UID 未知" }
            
            // DOWNLOAD FEATURE
            val downloadContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(16), 0, 0)
                addView(sectionTitle(activity, "下载视频", 22f))
                
                val statusText = statLine(activity, "状态", "解析中...")
                addView(statusText)
                val buttonContainer = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(buttonContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

                // Extract BVID
                val bvid = extractBvid(activity)
                if (bvid.isNullOrEmpty()) {
                    statusText.text = "状态：未获取到 BVID"
                } else {
                    statusText.text = "状态：获取 BVID $bvid，正在拉取流信息..."
                    val cookies = getBiliCookies(activity)
                    io.github.bbzq.feats.download.VideoDownloadManager.fetchVideoInfo(activity, bvid, cookies) { list, error ->
                        if (list.isNullOrEmpty()) {
                            statusText.text = "状态：${error ?: "获取流信息失败"}"
                        } else {
                            statusText.text = "状态：就绪 (BVID: $bvid)"
                            
                            val qualityButtons = mutableListOf<android.widget.Button>()
                            // Add a button for each available quality
                            list.forEach { quality ->
                                val btn = android.widget.Button(activity).apply {
                                    text = "下载并导出 MP4 (${quality.description})"
                                    setBackgroundColor(Color.rgb(251, 114, 153))
                                    setTextColor(Color.WHITE)
                                    setOnClickListener {
                                        qualityButtons.forEach { it.isEnabled = false }
                                        io.github.bbzq.feats.download.VideoDownloadManager.downloadAndMux(activity, bvid, quality.videoUrl, quality.audioUrl) { msg, pct ->
                                            statusText.text = if (pct in 0..99) "状态：$msg ($pct%)" else "状态：$msg"
                                            if (pct == 100 || pct == -1 || msg.contains("成功") || msg.contains("失败")) {
                                                qualityButtons.forEach { it.isEnabled = true }
                                            }
                                        }
                                    }
                                }
                                qualityButtons.add(btn)
                                buttonContainer.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
                            }
                        }
                    }
                }
            }

            val content = FrameLayout(activity).apply {
                setPadding(dp(24), dp(18), dp(24), dp(12))
                setBackgroundColor(Color.rgb(46, 46, 46))
                addView(
                    ScrollView(activity).apply {
                        isFillViewport = true
                        val root = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(downloadContainer)
                        }
                        addView(root)
                    }
                )
                addView(
                    RepeatingWatermarkView(activity, watermark),
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth)
                .setView(content)
                .setPositiveButton("关闭", null)
                .show()
            dialog.setOnDismissListener { activeStatsContent.clear() }
            dialog.window?.apply {
                setGravity(Gravity.BOTTOM)
                setBackgroundDrawable(
                    GradientDrawable().apply {
                        setColor(Color.rgb(46, 46, 46))
                        cornerRadius = dp(16).toFloat()
                    },
                )
                val metrics = activity.resources.displayMetrics
                setLayout(metrics.widthPixels, (metrics.heightPixels * 0.70f).toInt())
            }
        }.onFailure { reportFailure("failed to show statistics", it) }
    }

    private fun extractBvid(activity: Activity): String? {
        val captured = currentBvid
        if (!captured.isNullOrBlank() && isValidBvid(captured)) return captured

        val intent = activity.intent
        if (intent != null) {
            // 1. Check intent extras
            for (key in listOf("bvid", "bv_id", "video_bvid", "url", "link")) {
                val value = runCatching { intent.getStringExtra(key) }.getOrNull()
                if (!value.isNullOrBlank()) {
                    val match = Regex("""BV1[a-zA-Z0-9]{9}""").find(value)
                    if (match != null) return match.value
                }
            }

            // Check aid in intent
            val aid = runCatching { intent.getLongExtra("aid", 0L) }.getOrDefault(0L)
            if (aid > 0L) return SkipVideoAdState.bvidFromAid(aid)

            // 2. Check intent data uri
            val dataStr = intent.data?.toString().orEmpty()
            if (dataStr.isNotBlank()) {
                val match = Regex("""BV1[a-zA-Z0-9]{9}""").find(dataStr)
                if (match != null) return match.value
                val avMatch = Regex("""av([0-9]+)""", RegexOption.IGNORE_CASE).find(dataStr)
                if (avMatch != null) {
                    val avNum = avMatch.groupValues[1].toLongOrNull()
                    if (avNum != null && avNum > 0L) return SkipVideoAdState.bvidFromAid(avNum)
                }
            }
        }

        // 3. Fallback: search view tree
        val decor = activity.window?.decorView as? ViewGroup ?: return null
        return searchBvidInViews(decor)
    }

    private fun searchBvidInViews(view: View): String? {
        if (view is TextView) {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                val match = Regex("""BV1[a-zA-Z0-9]{9}""").find(text)
                if (match != null) return match.value
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val res = searchBvidInViews(view.getChildAt(i))
                if (res != null) return res
            }
        }
        return null
    }

    private fun isValidBvid(id: String): Boolean {
        return id.length == 12 && (id.startsWith("BV1") || id.startsWith("bv1"))
    }

    private fun getBiliCookies(context: android.content.Context): String {
        return try {
            val biliAccountsClass = context.classLoader.loadClass("com.bilibili.lib.accounts.BiliAccounts")
            val getMethod = biliAccountsClass.getMethod("get", android.content.Context::class.java)
            val biliAccountsInstance = getMethod.invoke(null, context)
            val getAccountCookieMethod = biliAccountsClass.getMethod("getAccountCookie")
            val cookieInfoInstance = getAccountCookieMethod.invoke(biliAccountsInstance)
            val cookiesField = cookieInfoInstance.javaClass.getField("cookies")
            val cookiesList = cookiesField.get(cookieInfoInstance) as? List<*> ?: return ""
            
            val validCookies = mutableMapOf<String, String>()
            for (cookieBean in cookiesList) {
                if (cookieBean == null) continue
                val name = cookieBean.javaClass.getField("name").get(cookieBean)?.toString()?.trim()
                val value = cookieBean.javaClass.getField("value").get(cookieBean)?.toString()?.trim()
                if (!name.isNullOrBlank() && !value.isNullOrBlank()) {
                    validCookies[name] = value
                }
            }
            validCookies.map { "${it.key}=${it.value}" }.joinToString("; ")
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun createStatsContent(activity: Activity, stats: VideoStreamStats): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            renderStats(this, stats)
        }

    private fun renderStats(content: LinearLayout, stats: VideoStreamStats) {
        val activity = content.context as? Activity ?: return
        content.removeAllViews()
        content.addView(sectionTitle(activity, "视频信息", 22f))
        content.addView(sectionTitle(activity, "视频", 19f))
        content.addView(statLine(activity, "请求画质", "QN 127（最高档）"))
        content.addView(statLine(activity, "实际分辨率", resolution(stats)))
        content.addView(statLine(activity, "流元数据码率", bitrate(stats.bandwidth)))
        content.addView(statLine(activity, "实际画质 QN", stats.quality.toString()))
        content.addView(statLine(activity, "编码 ID", codecLabel(stats.codecId)))
        if (stats.frameRate.isNotBlank()) content.addView(statLine(activity, "帧率", stats.frameRate))

        content.addView(sectionTitle(activity, "音频", 19f))
        if (stats.audioBandwidth > 0 || stats.audioCodecId > 0) {
            content.addView(statLine(activity, "音频码率", bitrate(stats.audioBandwidth)))
            content.addView(statLine(activity, "音频编码", codecLabel(stats.audioCodecId)))
            if (stats.audioSampleRate > 0) {
                val srLabel = if (stats.audioSampleRate >= 1000) {
                    "%.1f kHz".format(stats.audioSampleRate / 1000.0)
                } else "${stats.audioSampleRate} Hz"
                content.addView(statLine(activity, "采样率", srLabel))
            }
            if (stats.audioChannels > 0) {
                val chLabel = when (stats.audioChannels) {
                    1 -> "单声道"
                    2 -> "立体声"
                    6 -> "5.1 环绕"
                    8 -> "7.1 环绕"
                    else -> "${stats.audioChannels} 声道"
                }
                content.addView(statLine(activity, "声道", chLabel))
            }
        } else {
            content.addView(statLine(activity, "音频信息", "响应未提供"))
        }
    }

    private fun codecLabel(id: Long): String = when (id.toInt()) {
        7 -> "AVC / H.264"
        12 -> "HEVC / H.265"
        13 -> "AV1"
        0, 1 -> "AAC"
        2 -> "MP3"
        3 -> "FLAC"
        4 -> "Opus"
        5 -> "AC-3 / Dolby Digital"
        6 -> "E-AC-3 / Dolby Digital Plus"
        else -> "ID $id"
    }

    private fun statLine(activity: Activity, label: String, value: String) = TextView(activity).apply {
        text = "$label：$value"
        textSize = 15f
        setTextColor(Color.rgb(238, 238, 238))
        setPadding(0, dp(9), 0, dp(9))
    }

    private fun sectionTitle(activity: Activity, title: String, size: Float) = TextView(activity).apply {
        text = title
        textSize = size
        setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun resolution(stats: VideoStreamStats): String = when {
        stats.width > 0 && stats.height > 0 -> "${stats.width} × ${stats.height}"
        else -> "响应未提供"
    }

    private fun bitrate(value: Long): String = when {
        value <= 0 -> "响应未提供"
        value >= 1_000_000 -> String.format(Locale.US, "%.2f Mbps (%d kbps)", value / 1_000_000.0, value / 1000)
        else -> "${value / 1000} kbps"
    }

    private fun dp(value: Int): Int = (value * application.resources.displayMetrics.density + 0.5f).toInt()

    private fun isVideoDetailActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("DetailActivity", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true)
    }

    companion object {
        @Volatile var instance: VideoStatsOverlayController? = null
        @Volatile var currentBvid: String? = null
        @Volatile var currentCid: Long? = null

        fun getOrCreate(context: android.content.Context): VideoStatsOverlayController {
            return instance ?: synchronized(this) {
                instance ?: VideoStatsOverlayController(
                    application = context.applicationContext as Application,
                    resolveIdentity = {
                        val snapshot = HostAccountResolver.resolve(context, context.classLoader)
                        UserWatermarkIdentity(uid = snapshot.uid, userName = snapshot.userName)
                    },
                    reportFailure = { _, _ -> },
                ).also {
                    it.install()
                    instance = it
                }
            }
        }
    }
}

internal data class UserWatermarkIdentity(val uid: String, val userName: String)


private class RepeatingWatermarkView(
    context: android.content.Context,
    private val watermark: String,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 200, 60, 90)
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val xStep = 220f * resources.displayMetrics.density
        val yStep = 88f * resources.displayMetrics.density
        var y = -height.toFloat()
        while (y < height * 2f) {
            var x = -width.toFloat()
            while (x < width * 2f) {
                canvas.save()
                canvas.rotate(-24f, x, y)
                canvas.drawText(watermark, x, y, paint)
                canvas.restore()
                x += xStep
            }
            y += yStep
        }
    }
}
