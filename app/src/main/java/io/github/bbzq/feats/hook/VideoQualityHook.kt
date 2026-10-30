package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter

class VideoQualityHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        val halfScreenQuality = ModuleSettings.getHalfScreenQuality(prefs)
        val fullScreenQuality = ModuleSettings.getFullScreenQuality(prefs)
        val fakeWifi = ModuleSettings.isFakeWifiEnabled(prefs)
        val unlockHighestBitrate = ModuleSettings.isUnlockHighestBitrateEnabled(prefs)
        val unlockVideoFeatures = ModuleSettings.isUnlockVideoFeaturesEnabled(prefs)

        val anyQualityModActive = halfScreenQuality != 0 ||
            fullScreenQuality != 0 ||
            fakeWifi ||
            unlockHighestBitrate ||
            unlockVideoFeatures

        if (!anyQualityModActive) {
            log("startHook: VideoQualityHook disabled (no quality modification features enabled)")
            return
        }

        val symbols = env.symbols?.videoQuality?.restore(classLoader) ?: run {
            log("startHook: VideoQualityHook skipped because symbols are unavailable")
            return
        }

        var installedHooks = 0

        if (halfScreenQuality != 0 || fullScreenQuality != 0) {
            symbols.rangeQualityUpdateMethod?.let { method ->
                env.hookAfter(method) { param ->
                    if (param.args[0] == true && param.args[9] == true) return@hookAfter
                    val state = param.args.getOrNull(3) ?: return@hookAfter
                    val fullScreen = state.javaClass.getField("element").getBoolean(state)
                    val halfFlow = param.args.getOrNull(1) ?: return@hookAfter
                    val autoFlow = param.args.getOrNull(2) ?: return@hookAfter
                    QualityRangePolicy.overrideRanges(
                        halfFlow, autoFlow, fullScreen,
                        ModuleSettings.getHalfScreenQuality(prefs), ModuleSettings.getFullScreenQuality(prefs),
                    )
                }
                installedHooks++
            }
        }

        env.hostContext?.let { context ->
            StorySceneDetector.register(context)
        }

        // 1. VIP privilege spoofing for quality management classes:
        if (unlockHighestBitrate || unlockVideoFeatures) {
            runCatching {
                val vipClass = classLoader.findClassOrNull("com.bilibili.lib.accountinfo.model.VipUserInfo")
                val isEffectiveVip = vipClass?.declaredMethods?.firstOrNull { method ->
                    method.name == "isEffectiveVip" && method.parameterCount == 0
                }
                if (isEffectiveVip != null) {
                    env.hookBefore(isEffectiveVip) { param ->
                        runCatching {
                            if (StorySceneDetector.isStoryForeground()) return@hookBefore
                            val trace = Thread.currentThread().stackTrace
                            val limit = minOf(trace.size, 15)
                            var isQualityCaller = false
                            for (i in 0 until limit) {
                                val cls = trace[i].className
                                if (cls.contains("story", ignoreCase = true)) {
                                    return@hookBefore
                                }
                                if (cls.contains(".quality.") || cls.contains(".player.")) {
                                    isQualityCaller = true
                                }
                            }
                            if (isQualityCaller) {
                                param.result = true
                            }
                        }
                    }
                    installedHooks++
                }
            }.onFailure { log("Failed to hook VipUserInfo.isEffectiveVip", it) }
        }

        // 2. Prevent half-screen preloading low quality streams
        if (halfScreenQuality != 0) {
            symbols.playerPreloadGetMethods.forEach { method ->
                runCatching {
                    env.hookAfter(method) { param ->
                        if (ModuleSettings.getHalfScreenQuality(prefs) != 0) param.result = null
                    }
                    installedHooks++
                }.onFailure { log("Failed to hook preload method ${method.name}", it) }
            }
        }

        // 2. Full screen setting helper
        if (fullScreenQuality != 0 && symbols.rangeQualityUpdateMethod == null) {
            symbols.playerSettingHelperGetDefaultQnMethod?.let { method ->
                runCatching {
                    env.hookBefore(method) { param ->
                        param.result = fullScreenQuality
                    }
                    installedHooks++
                }.onFailure { log("Failed to hook setting helper getDefaultQn", it) }
            }
        }

        log("startHook: VideoQualityHook active halfQn=$halfScreenQuality fullQn=$fullScreenQuality fakeWifi=$fakeWifi highestBitrate=$unlockHighestBitrate hooks=$installedHooks")
    }
}
