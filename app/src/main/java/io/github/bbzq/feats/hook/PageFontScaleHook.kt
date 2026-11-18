package io.github.bbzq.feats.hook

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfterMethod
import io.github.bbzq.feats.hookBeforeMethod

class PageFontScaleHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isCustomFontScaleEnabled(prefs)) return
        val scale = ModuleSettings.getCustomFontScaleFloat(prefs)
        if (scale == 1.0f) return

        applyFontScale(env.hostContext.resources, scale)

        // 防止宿主 LimitTextSizeUtil / VertexActivity / AppCompat 等後續將 fontScale 重置回 1.0f
        env.hookBeforeMethod(
            Resources::class.java,
            "updateConfiguration",
            Configuration::class.java,
            DisplayMetrics::class.java,
        ) { param ->
            val config = param.args[0] as? Configuration
            val metrics = param.args[1] as? DisplayMetrics
            if (config != null && config.fontScale != scale) {
                config.fontScale = scale
            }
            if (metrics != null) {
                val targetScaledDensity = metrics.density * scale
                if (metrics.scaledDensity != targetScaledDensity) {
                    metrics.scaledDensity = targetScaledDensity
                }
            }
        }

        classLoader.findClassOrNull("com.bilibili.droid.text.LimitTextSizeUtil")?.let { limitUtil ->
            env.hookAfterMethod(limitUtil, "limitTextSize", Resources::class.java) { param ->
                val res = param.result as? Resources ?: return@hookAfterMethod
                applyFontScale(res, scale)
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

        env.hookBeforeMethod(Activity::class.java, "onConfigurationChanged", Configuration::class.java) { param ->
            val config = param.args[0] as? Configuration ?: return@hookBeforeMethod
            config.fontScale = scale
        }

        // Hook Activity 與宿主覆寫 getResources() 的基類，確保返回前套用縮放與 scaledDensity
        val resourceHostClasses = listOfNotNull(
            Activity::class.java,
            classLoader.findClassOrNull("androidx.appcompat.app.AppCompatActivity"),
            classLoader.findClassOrNull("com.bilibili.lib.ui.BaseAppCompatActivity"),
            classLoader.findClassOrNull("com.bilibili.lib.ui.ComposeActivity"),
            classLoader.findClassOrNull("com.bilibili.lib.ui.DialogContainerActivity"),
            classLoader.findClassOrNull("kntr.common.compose.launcher.VertexActivity"),
        ).distinct()
        resourceHostClasses.forEach { hostClass ->
            env.hookAfterMethod(hostClass, "getResources") { param ->
                val res = param.result as? Resources ?: return@hookAfterMethod
                applyFontScale(res, scale)
            }
        }

        log("PageFontScaleHook installed with scale=$scale (${ModuleSettings.getCustomFontScalePercent(prefs)}%)")
    }

    private fun applyFontScale(res: Resources, scale: Float) {
        runCatching {
            val config = res.configuration ?: return
            val metrics = res.displayMetrics
            val targetScaledDensity = metrics.density * scale
            if (config.fontScale != scale || metrics.scaledDensity != targetScaledDensity) {
                val newConfig = Configuration(config).apply {
                    fontScale = scale
                }
                metrics.scaledDensity = targetScaledDensity
                @Suppress("DEPRECATION")
                res.updateConfiguration(newConfig, metrics)
            }
        }
    }
}
