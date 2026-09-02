package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.setObjectField
import java.lang.reflect.Type

class LiveRoomBlurMaskHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isRemoveLiveRoomBlurMaskEnabled(prefs)) {
            log("startHook: LiveRoomBlurMask disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val jsonClass = runCatching { classLoader.loadClass("com.alibaba.fastjson.JSON") }.getOrNull()
        if (jsonClass == null) {
            log("startHook: Fastjson JSON class not found")
            return
        }

        val roomInfoClassName = "com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.BiliLiveRoomInfo"
        var hookCount = 0

        // Intercept parseObject(String text, Type clazz, Feature... features)
        jsonClass.methods.firstOrNull { 
            it.name == "parseObject" && 
            it.parameterTypes.size == 3 && 
            it.parameterTypes[0] == String::class.java &&
            it.parameterTypes[1] == Type::class.java &&
            it.parameterTypes[2].isArray && it.parameterTypes[2].componentType.name.endsWith("Feature")
        }?.let { method ->
            env.hookAfter(method) { param ->
                checkAndPurifyResult(param.result, roomInfoClassName)
            }
            hookCount++
        }

        // Intercept parseObject(String text, Type clazz, ParserConfig config, ParseProcess process, int features, Feature... features)
        jsonClass.methods.firstOrNull { 
            it.name == "parseObject" && 
            it.parameterTypes.size == 6 && 
            it.parameterTypes[0] == String::class.java &&
            it.parameterTypes[1] == Type::class.java
        }?.let { method ->
            env.hookAfter(method) { param ->
                checkAndPurifyResult(param.result, roomInfoClassName)
            }
            hookCount++
        }

        log("startHook: LiveRoomBlurMask hooked $hookCount Fastjson methods")
    }

    private fun checkAndPurifyResult(result: Any?, roomInfoClassName: String) {
        if (result == null) return
        
        // Is it the BiliLiveRoomInfo object directly?
        if (result.javaClass.name == roomInfoClassName) {
            purifyAreaMask(result)
            return
        }
        
        // Is it wrapped in GeneralResponse?
        if (result.javaClass.name == "com.bilibili.okretro.GeneralResponse") {
            val data = runCatching { result.javaClass.getField("data").get(result) }.getOrNull()
            if (data != null && data.javaClass.name == roomInfoClassName) {
                purifyAreaMask(data)
            }
        }
    }

    private fun purifyAreaMask(roomInfo: Any) {
        if (roomInfo.setObjectField("areaMaskInfo", null)) {
            log("LiveRoomBlurMask: removed areaMaskInfo from BiliLiveRoomInfo")
        }
    }
}
