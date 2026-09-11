package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.getObjectField
import io.github.bbzq.feats.hookAfter

class SplashAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        val parserMethods = env.symbols?.splashAd?.restore(classLoader)?.parserMethods.orEmpty()

        parserMethods.forEach { method ->
            env.hookAfter(method) { param ->
                if (!ModuleSettings.isSkipSplashAdEnabled(prefs)) return@hookAfter
                runCatching {
                    dispatch(param.result)
                    dispatchKotlinx(param.args.firstOrNull(), param.result)
                }
                    .onFailure {
                        log("Splash response processor failed at ${method.declaringClass.name}.${method.name}", it)
                    }
            }
        }

        if (parserMethods.isEmpty()) {
            log("startHook: SplashAd, no parser found")
        } else {
            log("startHook: SplashAd, methods=${parserMethods.size}")
        }
    }

    private fun dispatch(rawResult: Any?) {
        val result = unwrap(rawResult) ?: return
        val fields = SPLASH_FIELDS[result.javaClass.name] ?: return
        fields.forEach { clearMutableList(result, it) }
    }

    private fun dispatchKotlinx(deserializer: Any?, rawResult: Any?) {
        val result = rawResult ?: return
        when (resolveSerialName(deserializer)) {
            "kntr.srcs.app.splash.model.SplashListResponse" -> clearAllMutableLists(result)
            "kntr.srcs.app.splash.model.SplashShowStrategy" -> clearSplashShowStrategy(result)
        }
    }

    private fun resolveSerialName(deserializer: Any?): String? {
        val descriptor = deserializer?.callNoArg("getDescriptor") ?: return null
        return descriptor.callNoArg("getSerialName") as? String
            ?: descriptor.getObjectField("serialName") as? String
    }

    private fun unwrap(result: Any?): Any? {
        if (result == null) return null
        val className = result.javaClass.name
        return if (className.endsWith("GeneralResponse") || className.endsWith("RxGeneralResponse")) {
            result.getObjectField("data")
        } else {
            result
        }
    }

    private fun clearMutableList(target: Any, fieldName: String) {
        val list = target.getObjectField(fieldName) as? List<*> ?: return
        if (list is MutableList<*>) {
            runCatching { list.clear() }
        } else {
            runCatching {
                val field = target.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, emptyList<Any>())
            }
        }
    }

    private fun clearAllMutableLists(target: Any) {
        target.javaClass.declaredFields
            .filter { List::class.java.isAssignableFrom(it.type) }
            .forEach { field ->
                field.isAccessible = true
                runCatching {
                    val list = field.get(target) as? List<*>
                    if (list is MutableList<*>) {
                        list.clear()
                    } else if (list != null && list.isNotEmpty()) {
                        field.set(target, emptyList<Any>())
                    }
                }
            }
    }

    private fun clearSplashShowStrategy(target: Any) {
        target.javaClass.declaredFields
            .filter { !it.type.isPrimitive }
            .filter { it.type != String::class.java && it.type.name !in JSON_OBJECT_CLASSES }
            .forEach { field ->
                field.isAccessible = true
                runCatching {
                    val value = field.get(target)
                    if (value is MutableList<*>) {
                        value.clear()
                    } else if (value is MutableMap<*, *>) {
                        value.clear()
                    } else {
                        field.set(target, null)
                    }
                }
            }
    }

    private fun Any.callNoArg(name: String): Any? {
        return javaClass.methods
            .firstOrNull { it.name == name && it.parameterCount == 0 }
            ?.let { method ->
                method.isAccessible = true
                runCatching { method.invoke(this) }.getOrNull()
            }
    }

    private companion object {
        private val JSON_OBJECT_CLASSES = setOf(
            "kotlinx.serialization.json.JsonObject",
            "kotlinx.serialization.p7923json.JsonObject",
        )
        private val SPLASH_LIST_FIELDS = listOf("adList", "showList", "splashList", "strategyList")
        private val SPLASH_SHOW_FIELDS = listOf("showList", "splashList", "strategyList")

        private val SPLASH_FIELDS = mapOf(
            "tv.danmaku.bili.splash.ad.model.SplashListResponse" to SPLASH_LIST_FIELDS,
            "tv.danmaku.bili.ui.splash.SplashData" to SPLASH_LIST_FIELDS,
            "tv.danmaku.bili.ui.splash.ad.model.SplashData" to SPLASH_LIST_FIELDS,
            "tv.danmaku.bili.splash.ad.model.SplashShowResponse" to SPLASH_SHOW_FIELDS,
            "tv.danmaku.bili.ui.splash.ShowSplashData" to SPLASH_SHOW_FIELDS,
            "tv.danmaku.bili.ui.splash.ad.model.SplashShowData" to SPLASH_SHOW_FIELDS,
        )
    }
}

