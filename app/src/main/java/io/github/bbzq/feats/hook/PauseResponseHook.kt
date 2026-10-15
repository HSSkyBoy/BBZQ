package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier

internal fun RoamingEnv.hookPauseResponses(): Int {
    val packageName = "com.bapis.bilibili.app.viewunite.v1"
    val enabled = { ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs) }
    val filter: (Any) -> Any = { reply ->
        if (reply.javaClass.getMethod("hasAds").invoke(reply) != true) reply else {
            val hasBar = reply.javaClass.getMethod("hasBar")
            val getBar = reply.javaClass.getMethod("getBar")
            clearProtobufFields(reply, "Ads").also { updated ->
                check(updated.javaClass.getMethod("hasAds").invoke(updated) == false &&
                    hasBar.invoke(updated) == hasBar.invoke(reply) && getBar.invoke(updated) == getBar.invoke(reply))
            }
        }
    }
    return hookJavaMoss("$packageName.ViewMoss", "playPause", "$packageName.PlayPauseReq",
        "$packageName.PlayPauseReply", enabled, filter) +
        hookKotlinMoss("$packageName.KViewMoss", "playPause", "$packageName.PlayPauseReply", enabled, filter = filter) +
        hookPausePanel() + hookPauseCountdown()
}

private fun RoamingEnv.hookPausePanel(): Int = runCatching {
    val owner = classLoader.findClassOrNull("com.bilibili.ship.theseus.united.page.ad.AdPanelRepository") ?: return 0
    val show = owner.declaredMethods.singleOrNull {
        it.name == "showPanel" && !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
            it.parameterTypes.map(Class<*>::getName) == listOf("int",
                "com.bilibili.gripper.api.ad.biz.videodetail.IPanelData",
                "com.bilibili.gripper.api.ad.biz.videodetail.IAdPanelListener")
    } ?: return 0
    val dismiss = owner.getMethod("dismissPanel").takeIf {
        !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE
    }?.apply { isAccessible = true } ?: return 0
    hookAfter(show) { param ->
        if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs)) return@hookAfter
        if (param.args.getOrNull(1)?.javaClass?.simpleName != "AdPausedPagePanelData") return@hookAfter
        param.thisObject?.let { dismiss.invoke(it) }
    }
    1
}.getOrElse { log("Pause advertising panel hook unavailable", it); 0 }

private fun RoamingEnv.hookPauseCountdown(): Int = runCatching {
    val service = classLoader.findClassOrNull("com.bilibili.ship.theseus.united.page.pausedpage.PausedPageService") ?: return 0
    val owner = classLoader.findClassOrNull("${service.name}\$showPauseBarCountdownToast\$3") ?: return 0
    val source = classLoader.findClassOrNull("kntr.common.ad.source.IKSourceContent") ?: return 0
    val reportable = classLoader.findClassOrNull("kntr.common.ad.capability.report.IReportable")
    val contextType = classLoader.findClassOrNull("kotlin.coroutines.CoroutineContext") ?: return 0
    val jobs = classLoader.findClassOrNull("kotlinx.coroutines.JobKt") ?: return 0
    val unitType = classLoader.findClassOrNull("kotlin.Unit") ?: return 0
    val unit = unitType.getField("INSTANCE").get(null)?.takeIf(unitType::isInstance) ?: return 0
    val entry = owner.declaredMethods.singleOrNull {
        !Modifier.isStatic(it.modifiers) && it.name == "invokeSuspend" && it.returnType == Any::class.java &&
            it.parameterTypes.contentEquals(arrayOf(Any::class.java))
    } ?: return 0
    val fields = owner.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.onEach { it.isAccessible = true }
    val label = fields.singleOrNull { it.name == "label" && it.type == Int::class.javaPrimitiveType } ?: return 0
    val capturedService = fields.singleOrNull { it.name == "this\$0" && it.type == service } ?: return 0
    val sourceField = service.declaredFields.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == source }
        ?.apply { isAccessible = true } ?: return 0
    val capturedAds = fields.filter { reportable != null && it.type == reportable }
    if (capturedAds.size > 1) return 0
    val captured = capturedAds.singleOrNull()
    val hasAd = source.getMethod("getHasAdContent").takeIf {
        !Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType
    }?.apply { isAccessible = true } ?: return 0
    val context = owner.getMethod("getContext").takeIf {
        !Modifier.isStatic(it.modifiers) && contextType.isAssignableFrom(it.returnType)
    }?.apply { isAccessible = true } ?: return 0
    val ensureActive = jobs.getMethod("ensureActive", contextType).takeIf {
        Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE
    }?.apply { isAccessible = true } ?: return 0
    if (hookBefore(entry) { param ->
            if (!ModuleSettings.isBlockVideoDetailBannerAdEnabled(prefs) || param.args.firstOrNull() !== unit) return@hookBefore
            val receiver = param.thisObject ?: return@hookBefore
            val advertising = runCatching {
                if (label.getInt(receiver) != 0) return@runCatching false
                ensureActive.invoke(null, context.invoke(receiver) ?: return@runCatching false)
                val ad = if (captured != null) captured.get(receiver) else {
                    sourceField.get(capturedService.get(receiver) ?: return@runCatching false)
                }
                source.isInstance(ad) && hasAd.invoke(ad) == true
            }.getOrDefault(false)
            if (advertising) param.result = unit
        }) 1 else 0
}.getOrElse { log("Pause advertising countdown hook unavailable", it); 0 }
