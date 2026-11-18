package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfterAllConstructors
import io.github.bbzq.feats.hookAfterMethod
import org.json.JSONObject

class AllowMiniPlayHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (!ModuleSettings.isAllowMiniPlayEnabled(prefs)) return

        val configTypeClass = classLoader.findClassOrNull(PLAY_CONFIG_TYPE_CLASS) ?: return
        val menuConfigClass = classLoader.findClassOrNull(PLAY_MENU_CONFIG_CLASS) ?: return
        val miniPlayerType = runCatching {
            configTypeClass.getField("MINIPLAYER").get(null)
        }.getOrNull() ?: return

        val typeField = menuConfigClass.declaredFields.firstOrNull {
            it.type == configTypeClass
        }?.apply { isAccessible = true } ?: return

        val booleanFields = menuConfigClass.declaredFields
            .filter { it.type == java.lang.Boolean.TYPE }
            .onEach { it.isAccessible = true }
        val supportField = booleanFields.getOrNull(0) ?: return
        val disableField = booleanFields.getOrNull(1)
        val unsupportedSceneField = menuConfigClass.declaredFields.firstOrNull {
            MutableList::class.java.isAssignableFrom(it.type) || List::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }

        val forceAllow: (Any?) -> Unit = { instance ->
            if (instance != null && runCatching { typeField.get(instance) == miniPlayerType }.getOrDefault(false)) {
                runCatching {
                    supportField.setBoolean(instance, true)
                    disableField?.setBoolean(instance, false)
                    (unsupportedSceneField?.get(instance) as? MutableList<*>)?.clear()
                }
            }
        }

        var count = env.hookAfterAllConstructors(menuConfigClass) { param ->
            forceAllow(param.thisObject)
        }
        count += env.hookAfterMethod(menuConfigClass, "fromJsonObject", JSONObject::class.java) { param ->
            forceAllow(param.thisObject)
        }
        log("AllowMiniPlayHook installed ($count hooks)")
    }

    private companion object {
        private const val PLAY_CONFIG_TYPE_CLASS = "com.bilibili.lib.media.resource.PlayConfig\$PlayConfigType"
        private const val PLAY_MENU_CONFIG_CLASS = "com.bilibili.lib.media.resource.PlayConfig\$PlayMenuConfig"
    }
}
