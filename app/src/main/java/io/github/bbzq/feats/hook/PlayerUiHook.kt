package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore

class PlayerUiHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var currentVideoDetailActivity: java.lang.ref.WeakReference<Activity>? = null

    override fun startHook() {
        if (env.processName != env.packageName) return
        val application = env.hostContext as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (isVideoDetailActivity(activity) || isStoryActivity(activity)) {
                    currentVideoDetailActivity = java.lang.ref.WeakReference(activity)
                    if (ModuleSettings.isPlayerTransparentStatusBarEnabled(prefs)) {
                        applyTransparentStatusBar(activity)
                    }
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                } else if (isPotentialPlayerActivity(activity)) {
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentVideoDetailActivity?.get() === activity) {
                    currentVideoDetailActivity = null
                }
            }
        })

        application.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                val activity = currentVideoDetailActivity?.get() ?: return
                if (!activity.isFinishing && !activity.isDestroyed) {
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                }
            }
            override fun onLowMemory() = Unit
            override fun onTrimMemory(level: Int) = Unit
        })

        installPlayerWidgetHook()

        log("startHook: PlayerUi installed (portrait story button control & gesture safe)")
        isInstalled = true
    }

    @Suppress("DEPRECATION")
    private fun applyTransparentStatusBar(activity: Activity) {
        val window = activity.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    // 清理播放器向系统注册的边缘手势排除区（systemGestureExclusionRects），防止侧滑返回失效
    private fun ensureSystemGestureExclusionSafe(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val decor = activity.window?.decorView ?: return@runCatching
                decor.systemGestureExclusionRects = emptyList<Rect>()
                decor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (decor.systemGestureExclusionRects.isNotEmpty()) {
                        decor.systemGestureExclusionRects = emptyList<Rect>()
                    }
                }
            }
        }
    }

    private fun installPlayerWidgetHook() {
        runCatching {
            val storyWidgetClass = classLoader.findClassOrNull("com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullStoryWidget")
            if (storyWidgetClass != null) {
                storyWidgetClass.declaredMethods
                    .filter { it.name == "setVisibility" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
                    .forEach { method ->
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.args[0] = View.GONE
                            }
                        }
                    }
            }
        }.onFailure {
            log("PlayerUi: installPlayerWidgetHook failed", it)
        }
    }

    private fun schedulePlayerUiTuning(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        applyTuningInternal(activity, decor)
        CONTROL_RECHECK_DELAYS_MS.forEach { delay ->
            decor.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && decor.isAttachedToWindow) {
                    applyTuningInternal(activity, decor)
                }
            }, delay)
        }
    }

    private fun applyTuningInternal(activity: Activity, decor: View) {
        val isStory = isStoryActivity(activity)

        // 隐藏特定“转短视频流/看一看”跳转按键
        if (!isStory && ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
            revealPortraitControls(decor)
        }

        // 伴随广告跳过指示条
        // SkipVideoAdProgress
    }

    private fun revealPortraitControls(view: View) {
        if (isPortraitControl(view)) {
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            view.layoutParams?.let { lp ->
                if (lp.width != 0 || lp.height != 0) {
                    lp.width = 0
                    lp.height = 0
                    view.layoutParams = lp
                }
            }
            view.isClickable = false
            view.setOnClickListener(null)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index) ?: continue
                revealPortraitControls(child)
            }
        }
    }

    private fun isPortraitControl(view: View): Boolean {
        val className = view.javaClass.name
        if (className.contains("FullStoryWidget", ignoreCase = true) ||
            className.contains("GeminiPlayerFullStoryWidget", ignoreCase = true)
        ) {
            return true
        }

        if (view is ViewGroup) {
            if (className.startsWith("android.view.")) return false
            if (view.childCount > 3 || (view.width > 300 && view.height > 300)) return false
        }

        val description = view.contentDescription?.toString().orEmpty()
        if (description.isNotBlank() && PORTRAIT_DESCRIPTION_MARKERS.any { description.contains(it, ignoreCase = true) }) {
            return true
        }

        val text = (view as? TextView)?.text?.toString().orEmpty()
        if (text.isNotBlank() && PORTRAIT_DESCRIPTION_MARKERS.any { text.contains(it, ignoreCase = true) }) {
            return true
        }

        if (view.id != View.NO_ID) {
            val entry = runCatching { view.resources.getResourceEntryName(view.id) }
                .getOrNull()?.lowercase() ?: return false

            if (EXACT_PORTRAIT_IDS.contains(entry)) {
                return true
            }
        }
        return false
    }

    private fun isStoryActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val name = activity.javaClass.name
        return name.contains("Story", ignoreCase = true)
    }

    private fun isVideoDetailActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return (name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("DetailActivity", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true)) &&
            !name.contains("Story", ignoreCase = true)
    }

    private fun isPotentialPlayerActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return isVideoDetailActivity(activity) ||
            isStoryActivity(activity) ||
            name.contains("Player", ignoreCase = true) ||
            name.contains("Bangumi", ignoreCase = true)
    }

    private companion object {
        private val CONTROL_RECHECK_DELAYS_MS = longArrayOf(50L, 200L, 500L, 1_000L, 2_000L)
        private val PORTRAIT_DESCRIPTION_MARKERS = listOf(
            "进入看一看",
            "看一看",
            "竖屏模式",
            "展开竖屏",
            "切换竖屏",
            "切为竖屏",
        )
        private val EXACT_PORTRAIT_IDS = setOf(
            "bbplayer_halfscreen_story",
            "gemini_halfscreen_story",
            "preloading_landscape_portrait_toggle",
            "story_ctrl_screen",
            "story_fullscreen",
            "outside_portrait",
        )
    }
}

