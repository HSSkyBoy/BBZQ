package io.github.biliz.feats.hook

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.github.biliz.ModuleSettings
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfter
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

class StoryFullscreenHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isStoryVideoImmersiveFullscreenEnabled(prefs)) {
            log("startHook: StoryFullscreen disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val symbols = env.symbols?.storyFullscreen?.restore(classLoader)
        if (symbols == null) {
            log("startHook: StoryFullscreen skipped because symbols are unavailable")
            return
        }

        var hookCount = 0
        hookCount += installActivityResumeWatcher(symbols.onCreate.declaringClass.name)
        hookCount += installDialogWindowWatcher()
        hookCount += installSystemUiVisibilityWatcher()
        hookCount += installApplyAfter(symbols.onCreate)
        symbols.onResume?.let { hookCount += installApplyAfter(it) }
        hookCount += installFocusHook(symbols.onWindowFocusChanged)
        log("startHook: StoryFullscreen methods=$hookCount")
    }

    private fun installActivityResumeWatcher(storyActivityClassName: String): Int {
        synchronized(StoryFullscreenHook::class.java) {
            if (activityResumeWatcherInstalled) return 0
            activityResumeWatcherInstalled = true
        }
        val method = Activity::class.java.getDeclaredMethod("onResume")
        env.hookAfter(method) { param ->
            val activity = param.thisObject as? Activity ?: return@hookAfter
            if (activity.javaClass.name == storyActivityClassName) {
                applyStoryFullscreen(activity)
            } else {
                clearActiveStoryActivity(activity)
            }
        }
        log("startHook: StoryFullscreen at android.app.Activity.onResume")
        return 1
    }

    private fun installDialogWindowWatcher(): Int {
        synchronized(StoryFullscreenHook::class.java) {
            if (dialogWindowWatcherInstalled) return 0
            dialogWindowWatcherInstalled = true
        }
        var count = 0
        val show = Dialog::class.java.getDeclaredMethod("show")
        env.hookAfter(show) { param ->
            val dialog = param.thisObject as? Dialog ?: return@hookAfter
            val activity = activeStoryActivity() ?: return@hookAfter
            applyStoryFullscreen(dialog.window, activity)
        }
        log("startHook: StoryFullscreen at android.app.Dialog.show")
        count++

        val focus = Dialog::class.java.getDeclaredMethod(
            "onWindowFocusChanged",
            Boolean::class.javaPrimitiveType,
        )
        env.hookAfter(focus) { param ->
            if (param.args.firstOrNull() != true) return@hookAfter
            val dialog = param.thisObject as? Dialog ?: return@hookAfter
            val activity = activeStoryActivity() ?: return@hookAfter
            applyStoryFullscreen(dialog.window, activity)
        }
        log("startHook: StoryFullscreen at android.app.Dialog.onWindowFocusChanged")
        count++
        return count
    }

    private fun installSystemUiVisibilityWatcher(): Int {
        synchronized(StoryFullscreenHook::class.java) {
            if (systemUiVisibilityWatcherInstalled) return 0
            systemUiVisibilityWatcherInstalled = true
        }
        val method = View::class.java.getDeclaredMethod(
            "setSystemUiVisibility",
            Int::class.javaPrimitiveType,
        )
        env.hookAfter(method) { param ->
            val decor = param.thisObject as? View ?: return@hookAfter
            val target = synchronized(storyDecorTargets) {
                storyDecorTargets[decor]
            } ?: return@hookAfter
            @Suppress("DEPRECATION")
            val flags = decor.systemUiVisibility
            if (!hasStoryFullscreenFlags(flags)) {
                scheduleReapply(target, decor)
            }
        }
        log("startHook: StoryFullscreen at android.view.View.setSystemUiVisibility")
        return 1
    }

    private fun isStoryActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val name = activity.javaClass.name
        return name == STORY_VIDEO_ACTIVITY || name.contains("Story", ignoreCase = true)
    }

    private fun installApplyAfter(method: Method): Int {
        env.hookAfter(method) { param ->
            val activity = param.thisObject as? Activity ?: return@hookAfter
            if (!isStoryActivity(activity)) return@hookAfter
            applyStoryFullscreen(activity)
        }
        log("startHook: StoryFullscreen at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installFocusHook(method: Method): Int {
        env.hookAfter(method) { param ->
            if (param.args.firstOrNull() == true) {
                val activity = param.thisObject as? Activity ?: return@hookAfter
                if (!isStoryActivity(activity)) return@hookAfter
                applyStoryFullscreen(activity)
            }
        }
        log("startHook: StoryFullscreen at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun applyStoryFullscreen(activity: Activity?) {
        if (!isStoryActivity(activity)) return
        val window = activity?.window ?: return
        setActiveStoryActivity(activity)
        applyStoryFullscreen(window, activity)
    }

    private fun applyStoryFullscreen(window: Window?, activity: Activity) {
        if (window == null || activity.isFinishing || activity.isDestroyed) return
        runCatching {
            val decor = window.decorView ?: return
            val target = StoryWindowTarget(WeakReference(window), WeakReference(activity))
            trackStoryWindow(target, decor)
            allowDisplayCutout(window)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            applyLegacyFullscreenFlags(decor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    trackStoryInsetsController(target, controller)
                    installInsetsControllerWatcher(controller)
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or STORY_FULLSCREEN_FLAGS
            }
        }.onFailure {
            log("StoryFullscreen apply failed", it)
        }
    }

    private fun trackStoryWindow(target: StoryWindowTarget, decor: View) {
        synchronized(storyDecorTargets) {
            storyDecorTargets[decor] = target
        }
    }

    private fun trackStoryInsetsController(target: StoryWindowTarget, controller: WindowInsetsController) {
        synchronized(storyInsetsControllerTargets) {
            storyInsetsControllerTargets[controller] = target
        }
    }

    private fun installInsetsControllerWatcher(controller: WindowInsetsController) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val controllerClass = controller.javaClass
        synchronized(storyInsetsControllerClasses) {
            if (storyInsetsControllerClasses.containsKey(controllerClass)) return
            storyInsetsControllerClasses[controllerClass] = true
        }
        runCatching {
            val show = controllerClass.getMethod("show", Int::class.javaPrimitiveType)
            env.hookAfter(show) { param ->
                val types = param.args.firstOrNull() as? Int ?: return@hookAfter
                if (types and SYSTEM_BARS_INSET_TYPES == 0) return@hookAfter
                val target = synchronized(storyInsetsControllerTargets) {
                    storyInsetsControllerTargets[param.thisObject]
                } ?: return@hookAfter
                scheduleReapply(target, target.decorView())
            }
            log("startHook: StoryFullscreen at ${controllerClass.name}.show")
        }.onFailure {
            synchronized(storyInsetsControllerClasses) {
                storyInsetsControllerClasses.remove(controllerClass)
            }
            log("StoryFullscreen insets controller watcher skipped: ${controllerClass.name}", it)
        }
    }

    private fun scheduleReapply(target: StoryWindowTarget, decor: View?) {
        val view = decor ?: return
        synchronized(pendingReapplyDecorViews) {
            if (pendingReapplyDecorViews.containsKey(view)) return
            pendingReapplyDecorViews[view] = true
        }
        val posted = view.postDelayed(
            {
                synchronized(pendingReapplyDecorViews) {
                    pendingReapplyDecorViews.remove(view)
                }
                val window = target.windowRef.get() ?: return@postDelayed
                val activity = target.activityRef.get() ?: return@postDelayed
                applyStoryFullscreen(window, activity)
            },
            REAPPLY_DELAY_MS,
        )
        if (!posted) {
            synchronized(pendingReapplyDecorViews) {
                pendingReapplyDecorViews.remove(view)
            }
        }
    }

    private fun applyLegacyFullscreenFlags(decor: View) {
        @Suppress("DEPRECATION")
        val current = decor.systemUiVisibility
        if (hasStoryFullscreenFlags(current)) return
        @Suppress("DEPRECATION")
        decor.systemUiVisibility = current or STORY_FULLSCREEN_FLAGS
    }

    private fun allowDisplayCutout(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val attributes = window.attributes
        attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = attributes
    }

    private fun setActiveStoryActivity(activity: Activity) {
        synchronized(StoryFullscreenHook::class.java) {
            activeStoryActivity = WeakReference(activity)
        }
    }

    private fun activeStoryActivity(): Activity? =
        synchronized(StoryFullscreenHook::class.java) {
            activeStoryActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
        }

    private fun clearActiveStoryActivity(resumedActivity: Activity) {
        synchronized(StoryFullscreenHook::class.java) {
            val active = activeStoryActivity?.get() ?: return
            if (active !== resumedActivity) {
                activeStoryActivity = null
            }
        }
    }

    private data class StoryWindowTarget(
        val windowRef: WeakReference<Window>,
        val activityRef: WeakReference<Activity>,
    ) {
        fun decorView(): View? = windowRef.get()?.decorView
    }

    private companion object {
        private const val STORY_VIDEO_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity"
        private const val REAPPLY_DELAY_MS = 80L
        private val SYSTEM_BARS_INSET_TYPES =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            } else {
                0
            }

        @Suppress("DEPRECATION")
        private const val STORY_FULLSCREEN_FLAGS =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        private val storyDecorTargets = WeakHashMap<View, StoryWindowTarget>()
        private val storyInsetsControllerTargets = WeakHashMap<Any, StoryWindowTarget>()
        private val storyInsetsControllerClasses = WeakHashMap<Class<*>, Boolean>()
        private val pendingReapplyDecorViews = WeakHashMap<View, Boolean>()
        private var activeStoryActivity: WeakReference<Activity>? = null
        private var activityResumeWatcherInstalled = false
        private var dialogWindowWatcherInstalled = false
        private var systemUiVisibilityWatcherInstalled = false

        private fun hasStoryFullscreenFlags(flags: Int): Boolean =
            (flags and STORY_FULLSCREEN_FLAGS) == STORY_FULLSCREEN_FLAGS
    }
}
