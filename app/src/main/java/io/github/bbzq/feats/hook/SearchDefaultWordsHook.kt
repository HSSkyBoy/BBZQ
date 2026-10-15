package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv

internal fun RoamingEnv.hookSearchDefaultWords(): Int {
    val packageName = "com.bapis.bilibili.app.interfaces.v1"
    val enabled = { ModuleSettings.isHideHomeSearchDefaultWordEnabled(prefs) }
    val filter: (Any) -> Any = { reply ->
        val fields = listOf("Show", "Word", "Value").filter { field ->
            reply.javaClass.methods.singleOrNull {
                it.name == "get$field" && it.parameterCount == 0 && it.returnType == String::class.java
            }?.invoke(reply)?.let { (it as String).isNotEmpty() } == true
        }
        if (fields.isEmpty()) reply else clearProtobufFields(reply, *fields.toTypedArray()).also { updated ->
            check(fields.all { updated.javaClass.getMethod("get$it").invoke(updated) == "" })
        }
    }
    return hookJavaMoss("$packageName.SearchMoss", "defaultWords", "$packageName.DefaultWordsReq",
        "$packageName.DefaultWordsReply", enabled, filter) +
        hookKotlinMoss("$packageName.KSearchMoss", "defaultWords", "$packageName.DefaultWordsReply", enabled, filter = filter)
}
