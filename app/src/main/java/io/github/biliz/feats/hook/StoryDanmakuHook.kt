package io.github.biliz.feats.hook

import io.github.biliz.ModuleSettings
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import java.lang.reflect.Method

class StoryDanmakuHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isStoryVideoKeepDanmakuOnCommentEnabled(prefs)) {
            log("startHook: StoryDanmaku disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val symbols = env.symbols?.storyDanmaku?.restore(classLoader)
        if (symbols == null) {
            log("startHook: StoryDanmaku skipped because symbols are unavailable")
            return
        }

        var hookCount = 0
        symbols.commentShowMethods.forEach { method ->
            hookCount += installCommentStateHook(method, true)
        }
        symbols.commentHideMethods.forEach { method ->
            hookCount += installCommentStateHook(method, false)
        }
        symbols.introCommentShowMethod?.let { method ->
            hookCount += installCommentStateHook(method, true)
        }
        symbols.introCommentDismissMethod?.let { method ->
            hookCount += installCommentStateHook(method, false)
        }
        hookCount += installSetDanmakuOpacityHook(symbols.setDanmakuOpacity)
        hookCount += installUpdateCanvasHook(symbols.updateCanvas)
        log("startHook: StoryDanmaku methods=$hookCount")
    }

    private fun installCommentStateHook(method: Method, showing: Boolean): Int {
        if (showing) {
            env.hookBefore(method) {
                setStoryCommentShowing(true)
            }
        } else {
            env.hookAfter(method) {
                setStoryCommentShowing(false)
            }
        }
        log("startHook: StoryDanmaku at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installSetDanmakuOpacityHook(method: Method): Int {
        env.hookBefore(method) { param ->
            val savePreference = param.args.getOrNull(1) as? Boolean ?: return@hookBefore
            if (!savePreference && (isStoryCommentShowing() || isStoryCanvasUpdating())) {
                param.result = null
            }
        }
        log("startHook: StoryDanmaku at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installUpdateCanvasHook(method: Method): Int {
        env.hookBefore(method) { param ->
            val popperHeight = param.args.getOrNull(0) as? Int ?: return@hookBefore
            val peekHeight = param.args.getOrNull(1) as? Int ?: return@hookBefore
            if (popperHeight > 0 && peekHeight > 0) {
                enterStoryCanvasUpdate()
            }
        }
        env.hookAfter(method) { param ->
            val popperHeight = param.args.getOrNull(0) as? Int ?: return@hookAfter
            val peekHeight = param.args.getOrNull(1) as? Int ?: return@hookAfter
            if (popperHeight > 0 && peekHeight > 0) {
                exitStoryCanvasUpdate()
            }
        }
        log("startHook: StoryDanmaku at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private companion object {
        @Volatile
        private var storyCommentShowing = false

        @Volatile
        private var storyCanvasUpdateDepth = 0

        private fun setStoryCommentShowing(showing: Boolean) {
            storyCommentShowing = showing
        }

        private fun isStoryCommentShowing(): Boolean = storyCommentShowing

        private fun enterStoryCanvasUpdate() {
            storyCanvasUpdateDepth += 1
        }

        private fun exitStoryCanvasUpdate() {
            storyCanvasUpdateDepth = (storyCanvasUpdateDepth - 1).coerceAtLeast(0)
        }

        private fun isStoryCanvasUpdating(): Boolean = storyCanvasUpdateDepth > 0
    }
}
