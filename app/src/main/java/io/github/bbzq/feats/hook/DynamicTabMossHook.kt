package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod

internal fun RoamingEnv.hookKotlinDynamicTabs(): Int = hookKotlinMoss(
    "com.bapis.bilibili.app.dynamic.v2.KDynamicMoss",
    "dynTab",
    "com.bapis.bilibili.app.dynamic.v2.DynTabReply",
    enabled = {
        ModuleSettings.isDynamicPreferredVideoTabEnabled(prefs) ||
            ModuleSettings.isDynamicRemoveCityTabEnabled(prefs) || ModuleSettings.isDynamicRemoveSchoolTabEnabled(prefs)
    },
) { reply ->
    val hideCity = ModuleSettings.isDynamicRemoveCityTabEnabled(prefs)
    val hideSchool = ModuleSettings.isDynamicRemoveSchoolTabEnabled(prefs)
    val hasPrimaryTabs = (reply.callMethod("getScreenTabList") as? List<*>)?.isNotEmpty() == true
    var builder: Any? = null
    for (field in listOf("ScreenTab", "DynTab")) {
        val original = reply.callMethod("get${field}List") as? List<*> ?: continue
        val title: (Any?) -> String? = { it?.callMethod("getTitle") as? String }
        val name: (Any?) -> String? = { it?.callMethod("getName") as? String }
        var updated = DynamicTabPolicy.filter(original, hideCity, hideSchool, title, name,
            preserveNonEmpty = field != "DynTab" || !hasPrimaryTabs)
        if (field == "ScreenTab" && ModuleSettings.isDynamicPreferredVideoTabEnabled(prefs)) {
            updated = DynamicTabPolicy.preferVideo(updated, title, name, {
                it?.callMethod("getDefaultTab") == true
            }, { tab, selected ->
                requireNotNull(tab)
                val tabBuilder = tab.javaClass.getMethod("toBuilder").invoke(tab) ?: error("Empty dynamic tab builder")
                tabBuilder.javaClass.getMethod("setDefaultTab", Boolean::class.javaPrimitiveType).invoke(tabBuilder, selected)
                tabBuilder.javaClass.getMethod("build").invoke(tabBuilder) ?: error("Empty dynamic tab")
            })
        }
        if (updated === original) continue
        val target = builder ?: reply.javaClass.getMethod("toBuilder").invoke(reply)
            ?.also { builder = it } ?: error("Empty dynamic reply builder")
        target.javaClass.getMethod("clear$field").invoke(target)
        target.javaClass.getMethod("addAll$field", Iterable::class.java).invoke(target, updated)
    }
    builder?.let { it.javaClass.getMethod("build").invoke(it) ?: error("Empty dynamic reply") } ?: reply
}
