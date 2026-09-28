package io.github.bbzq.feats.hook

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.getStaticObjectField
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.methodOrNull
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class AiDeclaredVideoHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val guard = AiRedirectGuard()
    private var reply: ReplyShape? = null
    private var detailInstalled = false
    private var relateInstalled = false
    private var homeInstalled = false

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockAiDeclaredVideoEnabled(prefs)) {
            log("startHook: AiDeclaredVideo disabled, zero hooks installed")
            return
        }
        if (!detailInstalled) detailInstalled = installDetail()
        if (!relateInstalled) relateInstalled = installRelateCards()
        if (!homeInstalled) homeInstalled = installHomeFeed()
        isInstalled = detailInstalled && homeInstalled
        log("startHook: AiDeclaredVideo detail=$detailInstalled relate=$relateInstalled home=$homeInstalled")
    }

    private fun installDetail(): Boolean {
        val mossClass = classLoader.findClassOrNull(MOSS_CLASS)
        val requestClass = classLoader.findClassOrNull(REQUEST_CLASS)
        val shape = reply ?: ReplyShape.resolve(classLoader)?.also { reply = it }
        if (mossClass == null || requestClass == null || shape == null) {
            log("AiDeclaredVideo: ViewMoss structure not found on this host")
            return false
        }

        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == SYNC_METHOD &&
                it.parameterTypes.contentEquals(arrayOf(requestClass)) &&
                !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.hookAfter(sync) { param ->
                val original = param.result ?: return@hookAfter
                process(shape, original, isPassive(param.args.firstOrNull()))?.let { param.result = it }
            }
            installed++
        }

        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)
        if (handlerClass != null && handlerClass.isInterface) {
            mossClass.declaredMethods.firstOrNull {
                it.name == ASYNC_METHOD &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    val passive = isPassive(param.args.firstOrNull())
                    param.args[1] = wrapHandler(handlerClass, delegate) { process(shape, it, passive) }
                }
                installed++
            }
        }

        if (installed == 0) {
            log("AiDeclaredVideo: no view method matched on ViewMoss")
            return false
        }
        return true
    }

    private fun installRelateCards(): Boolean {
        val shape = reply ?: return false
        var installed = 0
        classLoader.findClassOrNull(RELATES_CLASS)?.methodOrNull("getCardsList")?.let { method ->
            env.hookAfter(method) { param ->
                val cards = param.result as? List<*> ?: return@hookAfter
                retainUnknown(shape, cards, keepNonEmpty = false)?.let { param.result = it }
            }
            installed++
        }
        classLoader.findClassOrNull(RELATES_FEED_REPLY_CLASS)?.methodOrNull("getRelatesList")?.let { method ->
            env.hookAfter(method) { param ->
                val cards = param.result as? List<*> ?: return@hookAfter
                retainUnknown(shape, cards, keepNonEmpty = true)?.let { param.result = it }
            }
            installed++
        }
        return installed > 0
    }

    private fun installHomeFeed(): Boolean {
        val feedSymbols = env.symbols?.homeRecommendFeed?.restore(classLoader) ?: return false
        val getUri = feedSymbols.getUri
        feedSymbols.responseGetItems.forEach { response ->
            env.hookAfter(response.getItems) { param ->
                val items = param.result as? List<*> ?: return@hookAfter
                if (items.isEmpty()) return@hookAfter
                val filtered = items.filterNot { item -> item != null && isAiFeedItem(item, getUri) }
                if (filtered.size == items.size) return@hookAfter
                param.result = filtered
                response.itemsField?.let { field ->
                    runCatching { field.set(param.thisObject, filtered) }
                        .onFailure { log("AiDeclaredVideo could not update home feed items field", it) }
                }
                log("AiDeclaredVideo removed ${items.size - filtered.size} home feed item(s)")
            }
        }
        return feedSymbols.responseGetItems.isNotEmpty()
    }

    private fun isAiFeedItem(item: Any, getUri: Method?): Boolean {
        val uri = getUri?.let { runCatching { it.invoke(item) as? String }.getOrNull() }
        if (AiDeclaredVideoPolicy.feedUriDeclaresAigc(uri)) return true
        if (AiDeclaredVideoRegistry.isEmpty()) return false
        val aid = AiDeclaredVideoPolicy.aidFromParam(item.callMethod("getParam") as? String)
        return AiDeclaredVideoRegistry.contains(aid)
    }

    private fun retainUnknown(shape: ReplyShape, cards: List<*>, keepNonEmpty: Boolean): List<Any?>? {
        if (cards.isEmpty() || AiDeclaredVideoRegistry.isEmpty()) return null
        val retained = cards.filterNot { card -> card != null && shape.isKnownAiCard(card) }
        if (retained.size == cards.size) return null
        if (keepNonEmpty && retained.isEmpty()) return null
        return retained
    }

    private fun isPassive(request: Any?): Boolean =
        AiDeclaredVideoPolicy.isPassiveRequest(request?.callMethod("getSpmid") as? String)

    private fun process(shape: ReplyShape, original: Any, passive: Boolean): Any? = runCatching {
        if (!shape.replyClass.isInstance(original)) return@runCatching null
        val facts = shape.facts(original)
        if (!facts.declared) return@runCatching null
        AiDeclaredVideoRegistry.add(facts.aid)
        if (passive || shape.hasHostError(original)) return@runCatching null
        val candidate = facts.candidates.firstOrNull {
            AiDeclaredVideoPolicy.isReplacementCandidate(it, facts.aid, facts.ownerMid, AiDeclaredVideoRegistry::contains)
        }
        val redirect = candidate?.uri?.takeIf { guard.tryAcquire() }
        val updated = if (redirect != null) {
            shape.redirect(original, redirect)
        } else {
            shape.block(original, text(R.string.ai_declared_blocked_hint))
        } ?: return@runCatching null
        toast(text(if (redirect != null) R.string.ai_declared_redirect_toast else R.string.ai_declared_blocked_hint))
        log("AiDeclaredVideo intercepted aid=${facts.aid} redirect=${redirect != null}")
        updated
    }.onFailure { log("AiDeclaredVideo rewrite failed, keeping original reply", it) }.getOrNull()

    private fun wrapHandler(handlerClass: Class<*>, delegate: Any, transform: (Any) -> Any?): Any =
        Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val forwarded = arrayOfNulls<Any?>(args?.size ?: 0)
                args?.forEachIndexed { index, value -> forwarded[index] = value }
                if (method.name == "onNext" && forwarded.size == 1) {
                    forwarded[0]?.let { value ->
                        runCatching { transform(value) }
                            .onFailure { log("AiDeclaredVideo async transform failed", it) }
                            .getOrNull()
                            ?.let { forwarded[0] = it }
                    }
                }
                try {
                    method.invoke(delegate, *forwarded)
                } catch (e: InvocationTargetException) {
                    throw e.targetException ?: e
                }
            },
        )

    private fun text(id: Int): String =
        runCatching { (env.moduleContext ?: env.hostContext).getString(id) }.getOrDefault("")

    private fun toast(message: String) {
        if (message.isEmpty()) return
        mainHandler.post {
            runCatching { Toast.makeText(env.hostContext, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private data class ReplyFacts(
        val aid: Long,
        val ownerMid: Long,
        val declared: Boolean,
        val candidates: List<AiRelateCandidate>,
    )

    private class ReplyShape(
        val replyClass: Class<*>,
        private val configClass: Class<*>,
        private val avType: Int,
        private val notFoundCode: Int,
        private val privacyCode: Int,
    ) {
        fun hasHostError(reply: Any): Boolean = (reply.callMethod("getEcodeValue") as? Int ?: 0) != 0

        fun redirect(reply: Any, url: String): Any? = write(reply, notFoundCode, url, "")

        fun block(reply: Any, message: String): Any? = write(reply, privacyCode, "", message)

        private fun write(reply: Any, code: Int, url: String, message: String): Any? {
            val config = configClass.callStaticMethod("newBuilder")
                ?.apply {
                    callMethod("setRedirectUrl", url)
                    callMethod("setMsg", message)
                }
                ?.callMethod("build") ?: return null
            val builder = reply.callMethod("toBuilder") ?: return null
            builder.callMethod("setEcodeValue", code)
            builder.callMethod("setEcodeConfig", config)
            val updated = builder.callMethod("build") ?: return null
            return updated.takeIf { it.callMethod("getEcodeValue") == code }
        }

        fun facts(reply: Any): ReplyFacts {
            val aid = reply.child("hasArc", "getArc")?.callMethod("getAid") as? Long ?: 0L
            val ownerMid = reply.child("hasOwner", "getOwner")?.callMethod("getMid") as? Long ?: 0L
            var declared = false
            val candidates = ArrayList<AiRelateCandidate>()
            forEachModule(reply) { module ->
                if (!declared) {
                    val title = module.child("hasUgcIntroduction", "getUgcIntroduction")
                        ?.child("hasNeutral", "getNeutral")
                        ?.callMethod("getTitle") as? String
                    if (AiDeclaredVideoPolicy.isAiDeclaration(title)) declared = true
                }
                (module.child("hasRelates", "getRelates")?.callMethod("getCardsList") as? List<*>)
                    ?.forEach { card -> if (card != null) candidates += candidate(card) }
            }
            return ReplyFacts(aid, ownerMid, declared, candidates)
        }

        fun isKnownAiCard(card: Any): Boolean {
            val fact = candidate(card)
            return fact.isVideo && AiDeclaredVideoRegistry.contains(fact.aid)
        }

        private fun candidate(card: Any): AiRelateCandidate {
            val basic = card.child("hasBasicInfo", "getBasicInfo")
            return AiRelateCandidate(
                isVideo = card.callMethod("getRelateCardTypeValue") as? Int == avType,
                aid = basic?.callMethod("getId") as? Long ?: 0L,
                uri = basic?.callMethod("getUri") as? String,
                authorMid = basic?.child("hasAuthor", "getAuthor")?.callMethod("getMid") as? Long ?: 0L,
            )
        }

        private inline fun forEachModule(reply: Any, block: (Any) -> Unit) {
            val tab = reply.child("hasTab", "getTab") ?: return
            (tab.callMethod("getTabModuleList") as? List<*>)?.forEach { tabModule ->
                val intro = tabModule?.child("hasIntroduction", "getIntroduction") ?: return@forEach
                (intro.callMethod("getModulesList") as? List<*>)?.forEach { module ->
                    if (module != null) block(module)
                }
            }
        }

        private fun Any.child(presence: String, getter: String): Any? =
            if (callMethod(presence) == true) callMethod(getter) else null

        companion object {
            fun resolve(classLoader: ClassLoader): ReplyShape? {
                val replyClass = classLoader.findClassOrNull(V1 + "ViewReply") ?: return null
                val configClass = classLoader.findClassOrNull(V1 + "ECodeConfig") ?: return null
                val ecodeClass = classLoader.findClassOrNull(V1 + "ECode") ?: return null
                val cardTypeClass = classLoader.findClassOrNull(COMMON + "RelateCardType") ?: return null
                if (replyClass.methodOrNull("getEcodeValue") == null) return null
                if (replyClass.methodOrNull("toBuilder") == null) return null
                return ReplyShape(
                    replyClass = replyClass,
                    configClass = configClass,
                    avType = cardTypeClass.getStaticObjectField("AV_VALUE") as? Int ?: return null,
                    notFoundCode = ecodeClass.getStaticObjectField("CODE_404_VALUE") as? Int ?: return null,
                    privacyCode = ecodeClass.getStaticObjectField("CODE_ARC_PRIVACY_VALUE") as? Int ?: return null,
                )
            }
        }
    }

    private companion object {
        const val V1 = "com.bapis.bilibili.app.viewunite.v1."
        const val COMMON = "com.bapis.bilibili.app.viewunite.common."
        const val MOSS_CLASS = V1 + "ViewMoss"
        const val REQUEST_CLASS = V1 + "ViewReq"
        const val RELATES_CLASS = COMMON + "Relates"
        const val RELATES_FEED_REPLY_CLASS = V1 + "RelatesFeedReply"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val SYNC_METHOD = "executeView"
        const val ASYNC_METHOD = "view"
    }
}
