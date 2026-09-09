package io.github.biliz.feats.hook

import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.symbol.loadClassOrNull

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

        // 1. VIP privilege spoofing for quality management classes:
        // Allows non-VIP users to select and persist 1080P高码率/4K without triggering trial popups or forced resets.
        if (unlockHighestBitrate || unlockVideoFeatures) {
            runCatching {
                val vipClass = classLoader.loadClassOrNull("com.bilibili.lib.accountinfo.model.VipUserInfo")
                val isEffectiveVip = vipClass?.declaredMethods?.firstOrNull { method ->
                    method.name == "isEffectiveVip" && method.parameterCount == 0
                }
                if (isEffectiveVip != null) {
                    env.hookBefore(isEffectiveVip) { param ->
                        runCatching {
                            val trace = Thread.currentThread().stackTrace
                            val limit = minOf(trace.size, 15)
                            var isQualityCaller = false
                            for (i in 0 until limit) {
                                val cls = trace[i].className
                                if (cls.contains(".quality.") || cls.contains(".player.")) {
                                    isQualityCaller = true
                                    break
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

        // 2. Prevent preloading low quality streams ONLY when user explicitly configured a fixed half-screen quality
        if (halfScreenQuality != 0) {
            symbols.playerPreloadGetMethods.forEach { method ->
                runCatching {
                    env.hookBefore(method) { param ->
                        param.result = null
                        PlayerTrace.logPreload(method.name, blocked = true)
                    }
                    installedHooks++
                }.onFailure { log("Failed to hook preload method ${method.name}", it) }
            }

            symbols.playerQualityServiceMethods.forEach { method ->
                runCatching {
                    if (method.parameterCount == 0) {
                        env.hookBefore(method) { param ->
                            param.result = halfScreenQuality
                            PlayerTrace.logStrategy("PlayerQualityService.${method.name}", "halfQn=$halfScreenQuality", halfScreenQuality)
                        }
                        installedHooks++
                    }
                }.onFailure { log("Failed to hook quality service method ${method.name}", it) }
            }
        }

        // 3. Full screen setting helper: ONLY override when user explicitly configured a fixed full-screen quality
        if (fullScreenQuality != 0) {
            symbols.playerSettingHelperGetDefaultQnMethod?.let { method ->
                runCatching {
                    env.hookBefore(method) { param ->
                        param.result = fullScreenQuality
                        PlayerTrace.logStrategy("PlayerSettingHelper.getDefaultQn", "fullQn=$fullScreenQuality", fullScreenQuality)
                    }
                    installedHooks++
                }.onFailure { log("Failed to hook setting helper getDefaultQn", it) }
            }
        }

        // 4. AutoSupremumQuality constructor ceiling lifting
        // When quality unlock is active, only lift ceiling to 127 so Bilibili's strategy doesn't clamp high quality.
        // We NEVER overwrite values for lower qualities, leaving 720P/480P completely natural.
        symbols.autoSupremumQualityConstructor?.let { ctor ->
            runCatching {
                env.hookBefore(ctor) { param ->
                    if (param.args.size >= 6) {
                        if (halfScreenQuality != 0) {
                            param.args[0] = halfScreenQuality // loginHalf
                            param.args[3] = halfScreenQuality // unloginHalf
                            param.args[4] = halfScreenQuality // unloginFull
                            param.args[5] = halfScreenQuality // unloginMobileFull
                        }
                        if (fullScreenQuality != 0) {
                            param.args[1] = fullScreenQuality // loginFull
                            param.args[2] = fullScreenQuality // loginMobileFull
                        }
                        if (unlockHighestBitrate || unlockVideoFeatures) {
                            val currentFull = (param.args[1] as? Int) ?: 0
                            if (currentFull < 127) param.args[1] = 127
                            val currentMobileFull = (param.args[2] as? Int) ?: 0
                            if (currentMobileFull < 127) param.args[2] = 127
                            val currentUnloginFull = (param.args[4] as? Int) ?: 0
                            if (currentUnloginFull < 127) param.args[4] = 127
                            val currentUnloginMobile = (param.args[5] as? Int) ?: 0
                            if (currentUnloginMobile < 127) param.args[5] = 127
                        }
                        if (fakeWifi) {
                            val mobileFull = (param.args[2] as? Int) ?: 0
                            val full = (param.args[1] as? Int) ?: 0
                            if (mobileFull < full) {
                                param.args[2] = full
                            }
                        }
                    }
                }
                installedHooks++
            }.onFailure { log("Failed to hook AutoSupremumQuality constructor", it) }
        }

        // 5. QualityStrategy selectQuality hook: force isVideoPortrait = true to route to loginFull/unloginFull
        if (halfScreenQuality != 0 || fullScreenQuality != 0 || unlockHighestBitrate || unlockVideoFeatures) {
            symbols.qualityStrategySelectMethod?.let { method ->
                runCatching {
                    env.hookBefore(method) { param ->
                        if (param.args.size >= 3) {
                            param.args[2] = true
                        }
                    }
                    installedHooks++
                }.onFailure { log("Failed to hook QualityStrategy selectQuality method", it) }
            }
        }

        log("startHook: VideoQualityHook active halfQn=$halfScreenQuality fullQn=$fullScreenQuality fakeWifi=$fakeWifi highestBitrate=$unlockHighestBitrate hooks=$installedHooks")
    }
}
