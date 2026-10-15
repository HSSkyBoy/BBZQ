package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredChronosPromotionSymbols
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PlayerPromotionAccess {
    const val NETWORK_PACKAGE = "com.bilibili.ship.theseus.united.player.mediaplay.network"
    const val MODEL_PACKAGE = "com.bilibili.adcommon.data.model"
    const val GUIDE_MARK = "already show free data guide toast in this cold start"
    const val GUIDE_METHOD = "player.freeDataGuide"
    const val FLOAT_METHOD = "player.advertFloat"

    fun isAdvertFloat(method: Method): Boolean = !Modifier.isStatic(method.modifiers) &&
        !Modifier.isAbstract(method.modifiers) && !method.isBridge && method.parameterCount == 0 &&
        method.returnType == List::class.java && method.declaringClass.name.startsWith("$MODEL_PACKAGE.")

    fun directGuide(loader: ClassLoader): Method? = runCatching {
        val continuation = loader.findClassOrNull("$NETWORK_PACKAGE.NetworkShowToastService\$tryToShowFreeDataGuideToast\$1")
            ?.takeIf { it.superclass?.name == "kotlin.coroutines.jvm.internal.ContinuationImpl" } ?: return null
        val owner = continuation.declaredFields.filter {
            !Modifier.isStatic(it.modifiers) && it.type.name.startsWith("$NETWORK_PACKAGE.") && it.type != continuation
        }.map { it.type }.distinct().singleOrNull() ?: return null
        owner.declaredMethods.singleOrNull(::isGuide)?.apply { isAccessible = true }
    }.getOrNull()

    fun isGuide(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers) || method.isBridge ||
            method.returnType != Any::class.java || method.parameterCount != 1 ||
            method.parameterTypes[0].name !in setOf("kotlin.coroutines.Continuation", "kotlin.coroutines.jvm.internal.ContinuationImpl") ||
            !method.declaringClass.name.startsWith("$NETWORK_PACKAGE.")) return false
        val fields = method.declaringClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        return listOf("android.content.Context", "android.content.SharedPreferences", "tv.danmaku.biliplayerv2.service.IToastService")
            .all { name -> fields.count { it.type.name == name } == 1 }
    }
}

internal fun RoamingEnv.hookPlayerPromotions(symbols: RestoredChronosPromotionSymbols?): Int {
    var installed = 0
    val enabled = { ModuleSettings.isBlockChronosPromotionEnabled(prefs) }
    runCatching {
        val assisted = symbols?.firstMethod(PlayerPromotionAccess.FLOAT_METHOD)?.takeIf(PlayerPromotionAccess::isAdvertFloat)
        val advert = classLoader.findClassOrNull("${PlayerPromotionAccess.MODEL_PACKAGE}.DmAdvert") ?: assisted?.declaringClass
        val dm = classLoader.findClassOrNull("${PlayerPromotionAccess.MODEL_PACKAGE}.Dm") ?: return@runCatching
        val card = classLoader.findClassOrNull("${PlayerPromotionAccess.MODEL_PACKAGE}.Card") ?: return@runCatching
        val getCard = dm.getMethod("getCard").takeIf { it.returnType == card } ?: return@runCatching
        val getType = card.getMethod("getCardType").takeIf { it.returnType == Int::class.javaPrimitiveType } ?: return@runCatching
        val readers = advert?.declaredMethods.orEmpty().filter {
            !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) && it.parameterCount == 0 &&
                it.returnType == List::class.java && it.name in setOf("getAds", "getFloatLayers", "getValidPanelData")
        } + listOfNotNull(assisted?.takeIf {
            it.declaringClass == advert && !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                it.parameterCount == 0 && it.returnType == List::class.java
        })
        readers.distinctBy(Method::toGenericString).forEach { method ->
            hookAfter(method) { param ->
                if (!enabled()) return@hookAfter
                val source = param.result as? List<*> ?: return@hookAfter
                val kept = source.filterNot { item ->
                    if (item == null || !dm.isInstance(item)) false else {
                        val value = getCard.invoke(item)
                        value != null && card.isInstance(value) && getType.invoke(value) in setOf(31, 32, 79, 80)
                    }
                }
                if (kept.size != source.size) param.result = kept
            }
            installed++
        }
    }.onFailure { log("Player promotion list hooks unavailable", it) }
    runCatching {
        val method = PlayerPromotionAccess.directGuide(classLoader)
            ?: symbols?.firstMethod(PlayerPromotionAccess.GUIDE_METHOD)?.takeIf(PlayerPromotionAccess::isGuide)
            ?: return@runCatching
        val unitType = classLoader.findClassOrNull("kotlin.Unit") ?: return@runCatching
        val unit = unitType.getField("INSTANCE").get(null)?.takeIf(unitType::isInstance) ?: return@runCatching
        if (hookBefore(method) { param -> if (enabled()) param.result = unit }) installed++
    }.onFailure { log("Automatic free data promotion hook unavailable", it) }
    runCatching {
        val reply = classLoader.findClassOrNull("com.bapis.bilibili.app.view.v1.TFInfoReply") ?: return@runCatching
        val toast = classLoader.findClassOrNull("com.bapis.bilibili.app.view.v1.TFToast") ?: return@runCatching
        val reader = reply.getMethod("getTfToast").takeIf { it.returnType == toast } ?: return@runCatching
        val title = toast.getMethod("getTitle").takeIf { it.returnType == String::class.java } ?: return@runCatching
        val factory = toast.getMethod("getDefaultInstance").takeIf { Modifier.isStatic(it.modifiers) && it.returnType == toast }
            ?: return@runCatching
        val empty = factory.invoke(null)?.takeIf(toast::isInstance) ?: return@runCatching
        if (title.invoke(empty) != "") return@runCatching
        hookAfter(reader) { param ->
            if (!enabled() || KotlinMossBridge.isRaw()) return@hookAfter
            val value = param.result ?: return@hookAfter
            if (toast.isInstance(value) && (title.invoke(value) as? String)?.isNotEmpty() == true) param.result = empty
        }
        installed++
    }.onFailure { log("Free data promotion reply hook unavailable", it) }
    runCatching {
        val parent = classLoader.findClassOrNull("com.bilibili.video.story.StoryDetail") ?: return@runCatching
        val model = classLoader.findClassOrNull("com.bilibili.video.story.FreeDataToast") ?: return@runCatching
        val reader = parent.getMethod("getFreeDataToast").takeIf { it.returnType == model } ?: return@runCatching
        for (name in listOf("getTitle", "getSubtitle", "getButtonText", "getUri")) {
            if (model.getMethod(name).returnType != String::class.java) return@runCatching
        }
        if (model.getMethod("getTipsId").returnType != Long::class.javaPrimitiveType) return@runCatching
        val field = parent.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == model }
            ?: return@runCatching
        val annotation = field.declaredAnnotations.singleOrNull {
            it.annotationClass.java.name == "com.alibaba.fastjson.annotation.JSONField"
        } ?: return@runCatching
        if (annotation.annotationClass.java.getMethod("name").invoke(annotation) != "free_flow_toast") return@runCatching
        hookAfter(reader) { param ->
            if (enabled() && model.isInstance(param.result)) param.result = null
        }
        installed++
    }.onFailure { log("Story free data promotion hook unavailable", it) }
    runCatching {
        val contract = classLoader.findClassOrNull("com.bilibili.lib.blconfig.Contract")
            ?.takeIf { it.isInterface } ?: return@runCatching
        for (name in listOf("TypedContract", "OverrideConfig")) {
            val owner = classLoader.findClassOrNull("com.bilibili.lib.blconfig.internal.$name")
                ?.takeIf(contract::isAssignableFrom) ?: continue
            val reader = owner.declaredMethods.singleOrNull {
                it.name == "get" && !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                    it.returnType == Any::class.java && it.parameterTypes.contentEquals(arrayOf(String::class.java, Any::class.java))
            } ?: continue
            if (hookBefore(reader) { param ->
                    if (param.args.getOrNull(0) != "freedata.mobile_free_data_toast_times") return@hookBefore
                    if (enabled() && param.args.getOrNull(1) is String) param.result = "0"
                }) installed++
        }
    }.onFailure { log("Automatic free data promotion config hook unavailable", it) }
    log("Player promotion hooks installed: $installed")
    return installed
}
