package io.github.bbzq.feats.hook

import android.app.Activity
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter

class SplitScreenFullscreenHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSplitScreenFullscreenEnabled(prefs)) {
            log("startHook: SplitScreenFullscreen disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val method = runCatching { Activity::class.java.getDeclaredMethod("isInMultiWindowMode") }.getOrNull()
        if (method == null) {
            log("startHook: SplitScreenFullscreen skipped, Activity.isInMultiWindowMode not found")
            return
        }

        env.hookAfter(method) { param ->
            if (param.result != true) return@hookAfter
            if (!ModuleSettings.isSplitScreenFullscreenEnabled(prefs)) return@hookAfter
            if (isCalledFromFullscreenWidget()) param.result = false
        }
        log("startHook: SplitScreenFullscreen hooked Activity.isInMultiWindowMode")
    }

    private fun isCalledFromFullscreenWidget(): Boolean =
        Throwable().stackTrace.asSequence()
            .take(STACK_SCAN_DEPTH)
            .any { it.className == FULLSCREEN_WIDGET_CLASS }

    private companion object {
        private const val FULLSCREEN_WIDGET_CLASS =
            "com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullscreenWidget"
        private const val STACK_SCAN_DEPTH = 12
    }
}
