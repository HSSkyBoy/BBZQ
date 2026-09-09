package io.github.biliz.feats.hook

import android.view.View
import android.view.ViewGroup
import io.github.biliz.ModuleSettings
import io.github.biliz.ModuleSettingsBridge
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.min

class StoryComponentAlphaHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        val alpha = ModuleSettings.getStoryVideoComponentAlpha(prefs)
        if (alpha >= 1f) {
            log("startHook: StoryComponentAlpha disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val symbols = env.symbols?.storyComponentAlpha?.restore(classLoader)
        if (symbols == null) {
            log("startHook: StoryComponentAlpha skipped because symbols are unavailable")
            return
        }

        var hookCount = 0
        hookCount += installModuleConstructorHooks(symbols.infoConstructors, alpha, "StoryInfoModule")
        hookCount += installModuleConstructorHooks(symbols.rightConstructors, alpha, "StoryRightModule")
        hookCount += installModuleConstructorHooks(symbols.bottomConstructors, alpha, "StoryBottomModule")
        hookCount += installModuleConstructorHooks(symbols.seekBarConstructors, alpha, "StorySeekBar")
        hookCount += installTopControlsHook(symbols.fragmentOnCreateView, alpha)
        hookCount += installComponentAlphaWatcher(alpha)
        log("startHook: StoryComponentAlpha methods=$hookCount alpha=$alpha")
    }

    private fun installModuleConstructorHooks(
        constructors: List<Constructor<*>>,
        alpha: Float,
        label: String,
    ): Int {
        constructors.forEach { constructor ->
            env.hookAfter(constructor) { param ->
                (param.thisObject as? View)?.trackAndApplyComponentAlpha(alpha)
            }
            log("startHook: StoryComponentAlpha at ${constructor.declaringClass.name}.$label")
        }
        return constructors.size
    }

    private fun installTopControlsHook(method: Method, alpha: Float): Int {
        env.hookAfter(method) { param ->
            val topControls = (param.result as? ViewGroup)?.findStoryTopControls() ?: return@hookAfter
            topControls
                .trackAndApplyComponentAlpha(alpha)
        }
        log("startHook: StoryComponentAlpha at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installComponentAlphaWatcher(alpha: Float): Int {
        synchronized(StoryComponentAlphaHook::class.java) {
            if (componentAlphaWatcherInstalled) return 0
            componentAlphaWatcherInstalled = true
        }
        val method = View::class.java.getDeclaredMethod(
            "setAlpha",
            Float::class.javaPrimitiveType,
        )
        env.hookBefore(method) { param ->
            val view = param.thisObject as? View ?: return@hookBefore
            if (!isTrackedComponentView(view)) return@hookBefore
            val requested = param.args.firstOrNull() as? Float ?: return@hookBefore
            if (requested > alpha) {
                param.args[0] = alpha
            }
        }
        log("startHook: StoryComponentAlpha at android.view.View.setAlpha")
        return 1
    }

    private fun View.trackAndApplyComponentAlpha(alpha: Float) {
        trackComponentView(this)
        this.alpha = min(this.alpha, alpha)
    }

    private fun ViewGroup.findStoryTopControls(): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.javaClass.name == TOP_CONTROLS_CLASS_NAME) {
                return child
            }
        }
        return null
    }

    private companion object {
        private const val TOP_CONTROLS_CLASS_NAME = "androidx.constraintlayout.widget.ConstraintLayout"
        private val trackedComponentViews = WeakHashMap<View, Boolean>()
        private var componentAlphaWatcherInstalled = false

        private fun trackComponentView(view: View) {
            synchronized(trackedComponentViews) {
                trackedComponentViews[view] = true
            }
        }

        private fun isTrackedComponentView(view: View): Boolean =
            synchronized(trackedComponentViews) {
                trackedComponentViews.containsKey(view)
            }
    }
}
