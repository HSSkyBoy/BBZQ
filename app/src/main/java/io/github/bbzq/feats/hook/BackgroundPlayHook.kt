package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter

class BackgroundPlayHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isDisableBackgroundPlayEnabled(prefs)) {
            log("startHook: BackgroundPlay disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val serviceClass = runCatching {
            classLoader.loadClass("com.bilibili.playerbizcommon.features.background.BackgroundPlayService")
        }.getOrNull()
        if (serviceClass == null) {
            log("startHook: BackgroundPlay skipped, service class not found")
            return
        }

        var hookCount = 0
        serviceClass.declaredMethods
            .filter { (it.name == "isAvailable" || it.name == "isEnable") && it.parameterCount == 0 && it.returnType == java.lang.Boolean.TYPE }
            .forEach { method ->
                env.hookAfter(method) { param -> param.result = false }
                hookCount++
            }
        log("startHook: BackgroundPlay hooked $hookCount methods")
    }
}
