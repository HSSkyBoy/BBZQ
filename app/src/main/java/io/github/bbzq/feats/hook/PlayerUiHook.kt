package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Application
import android.graphics.Color
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
import io.github.bbzq.feats.hookBefore
import java.util.Collections
import java.util.WeakHashMap

/** Player window tweaks that avoid dependencies on obfuscated host classes. */
class PlayerUiHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val hiddenPortraitControls = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    override fun startHook() {
        if (env.processName != env.packageName) return
        val transparentStatusBar = ModuleSettings.isPlayerTransparentStatusBarEnabled(prefs)
        val hidePortraitControl = ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)
        if (!transparentStatusBar && !hidePortraitControl) return
        val application = env.hostContext as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (transparentStatusBar && isVideoDetailActivity(activity)) {
                    applyTransparentStatusBar(activity)
                }
                if (hidePortraitControl && isPotentialPlayerActivity(activity)) {
                    schedulePortraitControlScan(activity.window.decorView)
                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        if (hidePortraitControl) installVisibilityGuard()
        log("startHook: PlayerUi transparentStatusBar=$transparentStatusBar hidePortraitControl=$hidePortraitControl")
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

    private fun installVisibilityGuard() {
        runCatching {
            val setVisibilityMethod = View::class.java.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)
            env.hookBefore(setVisibilityMethod) { param ->
                val view = param.thisObject as? View ?: return@hookBefore
                val visibility = param.args.firstOrNull() as? Int ?: return@hookBefore
                if (visibility != View.GONE && isPortraitControl(view)) {
                    hiddenPortraitControls.add(view)
                    param.args[0] = View.GONE
                }
            }
        }
    }

    private fun revealPortraitControls(view: View) {
        if (isPortraitControl(view)) {
            hiddenPortraitControls.add(view)
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index) ?: continue
                revealPortraitControls(child)
            }
        }
    }

    private fun schedulePortraitControlScan(decor: View) {
        revealPortraitControls(decor)
        CONTROL_RECHECK_DELAYS_MS.forEach { delay ->
            decor.postDelayed({
                if (decor.isAttachedToWindow) {
                    revealPortraitControls(decor)
                    hiddenPortraitControls.toList().forEach {
                        if (it.isAttachedToWindow && it.visibility != View.GONE) {
                            it.visibility = View.GONE
                        }
                    }
                }
            }, delay)
        }
    }

    private fun isPortraitControl(view: View): Boolean {
        // Exclude root/container layouts so we never hide player frames or controller groups
        if (view is ViewGroup && view::class.java.name.startsWith("android.view.")) {
            return false
        }

        val className = view.javaClass.name
        if (PORTRAIT_CLASS_MARKERS.any { className.contains(it, ignoreCase = true) }) {
            return true
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

            if (EXCLUDED_ID_MARKERS.any { entry.contains(it) }) {
                return false
            }

            if (EXACT_PORTRAIT_IDS.contains(entry) || entry.contains("halfscreen_story")) {
                return true
            }

            val hasPortraitTarget = PORTRAIT_ID_MARKERS.any { entry.contains(it) }
            val hasControlTarget = CONTROL_ID_MARKERS.any { entry.contains(it) }
            if (hasPortraitTarget && hasControlTarget) {
                return true
            }
        }
        return false
    }

    private fun isVideoDetailActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("DetailActivity", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true)
    }

    private fun isPotentialPlayerActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return isVideoDetailActivity(activity) ||
            name.contains("Player", ignoreCase = true) ||
            name.contains("Bangumi", ignoreCase = true) ||
            name.contains("Story", ignoreCase = true)
    }

    private companion object {
        private val CONTROL_RECHECK_DELAYS_MS = longArrayOf(50L, 200L, 500L, 1_000L, 2_000L, 3_500L)
        private val PORTRAIT_CLASS_MARKERS = listOf(
            "FullStoryWidget",
            "GeminiPlayerFullStoryWidget",
            "PlayerFullStory",
        )
        private val PORTRAIT_DESCRIPTION_MARKERS = listOf(
            "竖屏",
            "进入看一看",
            "看一看",
            "竖屏模式",
            "展开竖屏",
            "切换竖屏",
            "切为竖屏",
            "竖屏全屏",
            "竖屏播放",
        )
        private val EXACT_PORTRAIT_IDS = setOf(
            "bbplayer_halfscreen_story",
            "gemini_halfscreen_story",
            "preloading_landscape_portrait_toggle",
            "story_ctrl_screen",
            "story_fullscreen",
            "bbplayer_portrait_fullscreen",
            "outside_portrait",
        )
        private val PORTRAIT_ID_MARKERS = listOf("portrait", "vertical")
        private val CONTROL_ID_MARKERS = listOf(
            "halfscreen",
            "screen",
            "fullscreen",
            "orientation",
            "control",
            "button",
            "btn",
            "toggle",
            "switch",
        )
        private val EXCLUDED_ID_MARKERS = listOf(
            "guideline",
            "divider",
            "line",
            "assist",
            "layout",
            "container",
            "controller",
            "recycler",
            "scroll",
            "panel",
            "view",
            "title",
            "group",
            "coupon",
            "invalid",
            "remind",
            "gift",
            "paywall",
            "history",
            "live",
            "ad",
        )
    }
}
