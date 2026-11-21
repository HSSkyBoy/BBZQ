package io.github.bbzq.feats.hook.system

import android.app.Dialog
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfterMethod
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Consumer

class DialogBlurBackgroundHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!ModuleSettings.isDialogBlurBackgroundEnabled(prefs)) return

        env.hookAfterMethod(Dialog::class.java, "onStart") { param ->
            val dialog = param.thisObject as? Dialog ?: return@hookAfterMethod
            val window = dialog.window ?: return@hookAfterMethod
            applyBlurBackground(window)
        }
        log("DialogBlurBackgroundHook installed")
    }

    private fun applyBlurBackground(window: Window) {
        if (hookedWindows.containsKey(window)) return
        hookedWindows[window] = true

        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes.blurBehindRadius = BLUR_RADIUS_PX
            window.setBackgroundBlurRadius(BLUR_RADIUS_PX)

            val listener = Consumer<Boolean> { enabled ->
                runCatching {
                    window.setDimAmount(if (enabled) BLUR_DIM_AMOUNT else FALLBACK_DIM_AMOUNT)
                }
            }
            val decorView = window.decorView
            decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    runCatching {
                        window.windowManager.addCrossWindowBlurEnabledListener(listener)
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    runCatching {
                        window.windowManager.removeCrossWindowBlurEnabledListener(listener)
                    }
                }
            })
            if (decorView.isAttachedToWindow) {
                window.windowManager.addCrossWindowBlurEnabledListener(listener)
            }
        }
    }

    private companion object {
        private const val BLUR_RADIUS_PX = 50
        private const val BLUR_DIM_AMOUNT = 0.1f
        private const val FALLBACK_DIM_AMOUNT = 0.6f
        private val hookedWindows = Collections.synchronizedMap(WeakHashMap<Window, Boolean>())
    }
}
