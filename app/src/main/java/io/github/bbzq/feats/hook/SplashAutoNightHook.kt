package io.github.bbzq.feats.hook

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.methodOrNull

class SplashAutoNightHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSplashAutoNightEnabled(prefs)) {
            log("startHook: SplashAutoNight disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val fragmentClass = classLoader.findClassOrNull(SPLASH_FRAGMENT_CLASS) ?: run {
            log("startHook: SplashAutoNight splash fragment class not found")
            return
        }
        val onViewCreated = fragmentClass
            .methodOrNull("onViewCreated", View::class.java, Bundle::class.java)
            ?.takeIf { it.declaringClass.name == SPLASH_FRAGMENT_CLASS }
            ?: run {
                log("startHook: SplashAutoNight onViewCreated not declared by $SPLASH_FRAGMENT_CLASS")
                return
            }

        env.hookAfter(onViewCreated) { param ->
            if (!ModuleSettings.isSplashAutoNightEnabled(prefs)) return@hookAfter
            val root = param.args.firstOrNull() as? View ?: return@hookAfter
            runCatching { applyNightBackground(root) }
                .onFailure { log("SplashAutoNight apply failed", it) }
        }
        isInstalled = true
        log("startHook: SplashAutoNight at $SPLASH_FRAGMENT_CLASS.onViewCreated")
    }

    private fun applyNightBackground(root: View) {
        val night = root.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val target = root.findSplashContainer() ?: root
        target.setBackgroundColor(if (night) Color.BLACK else Color.WHITE)
    }

    private fun View.findSplashContainer(): View? {
        val id = resources.getIdentifier(SPLASH_CONTAINER_ID_NAME, "id", context.packageName)
        if (id == 0) return null
        return findViewById(id)
    }

    private companion object {
        private const val SPLASH_FRAGMENT_CLASS =
            "tv.danmaku.bili.ui.splash.brand.ui.BaseBrandSplashFragment"
        private const val SPLASH_CONTAINER_ID_NAME = "splash_container"
    }
}
