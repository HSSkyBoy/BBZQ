package io.github.bbzq.feats.hook

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfterMethod
import io.github.bbzq.feats.hookBeforeMethod

class PageFontScaleHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isCustomFontScaleEnabled(prefs)) return
        val scale = ModuleSettings.getCustomFontScaleFloat(prefs)
        if (scale == 1.0f) return

        runCatching {
            val res = env.hostContext.resources
            val config = Configuration(res.configuration)
            if (config.fontScale != scale) {
                config.fontScale = scale
                @Suppress("DEPRECATION")
                res.updateConfiguration(config, res.displayMetrics)
            }
        }

        env.hookBeforeMethod(Activity::class.java, "attachBaseContext", Context::class.java) { param ->
            val base = param.args[0] as? Context ?: return@hookBeforeMethod
            val config = Configuration(base.resources.configuration)
            if (config.fontScale != scale) {
                config.fontScale = scale
                param.args[0] = base.createConfigurationContext(config)
            }
        }

        env.hookBeforeMethod(Activity::class.java, "applyOverrideConfiguration", Configuration::class.java) { param ->
            val config = param.args[0] as? Configuration ?: return@hookBeforeMethod
            config.fontScale = scale
        }

        env.hookAfterMethod(Activity::class.java, "getResources") { param ->
            val res = param.result as? Resources ?: return@hookAfterMethod
            val config = res.configuration ?: return@hookAfterMethod
            if (config.fontScale != scale) {
                val newConfig = Configuration(config)
                newConfig.fontScale = scale
                @Suppress("DEPRECATION")
                res.updateConfiguration(newConfig, res.displayMetrics)
            }
        }

        env.hookBeforeMethod(Activity::class.java, "onConfigurationChanged", Configuration::class.java) { param ->
            val config = param.args[0] as? Configuration ?: return@hookBeforeMethod
            config.fontScale = scale
        }

        log("PageFontScaleHook installed with scale=$scale (${ModuleSettings.getCustomFontScalePercent(prefs)}%)")
    }
}
