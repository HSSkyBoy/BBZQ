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
    private var relatesFeedInstalled = false
    private var homeInstalled = false

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockAiDeclaredVideoEnabled(prefs)) {
            log("startHook: AiDeclaredVideo disabled, zero hooks installed")
            return
        }
        if (!detailInstalled) detailInstalled = installDetail()
        if (!relatesFeedInstalled) relatesFeedInstalled = installRelatesFeed()
        if (!homeInstalled) homeInstalled = installHomeFeed()
        isInstalled = detailInstalled && homeInstalled
        log("startHook: AiDeclaredVideo detail=$detailInstalled relatesFeed=$relatesFeedInstalled home=$homeInstalled")
    }

    private fun installDetail(): Boolean {
        val shape = reply ?: ReplyShape.resolve(classLoader)?.also { reply = it }
        if (shape == null) {
            log("AiDeclaredVideo: ViewReply structure not found on this host")
            return false
        }
        val installed = installMoss(SYNC_METHOD, ASYNC_METHOD, REQUEST_CLASS) { value, request ->
            process(shape, value, isPassive(request))
        }
        if (installed == 0) log("AiDeclaredVideo: no view method matched on ViewMoss")
        return installed > 0
    }

    private fun installRelatesFeed(): Boolean {
        val shape = reply ?: return false
        val replyClass = classLoader.findClassOrNull(RELATES_FEED_REPLY_CLASS) ?: return false
        val installed = installMoss(FEED_SYNC_METHOD, FEED_ASYNC_METHOD, RELATES_FEED_REQUEST_CLASS) { value, _ ->
            if (replyClass.isInstance(value)) stripRelatesFeed(shape, value) else null
        }
        if (installed == 0) log("AiDeclaredVideo: no relates feed method matched on ViewMoss")
        return installed > 0
    }

    private fun installMoss(
        syncName: String,
        asyncName: String,
        requestClassName: String,
        transform: (Any, Any?) -> Any?,
    ): Int {
        val mossClass = classLoader.findClassOrNull(MOSS_CLASS) ?: return 0
        val requestClass = classLoader.findClassOrNull(requestClassName) ?: return 0
        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == syncName &&
                it.parameterTypes.contentEquals(arrayOf(requestClass)) &&
                !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.hookAfter(sync) { param ->
                val original = param.result ?: return@hookAfter
                transform(original, param.args.firstOrNull())?.let { param.result = it }
            }
            installed++
        }

        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)
        if (handlerClass != null && handlerClass.isInterface) {
            mossClass.declaredMethods.firstOrNull {
                it.name == asyncName &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    val request = param.args.firstOrNull()
                    param.args[1] = wrapHandler(handlerClass, delegate) { transform(it, request) }
                }
                installed++
            }
        }
        return installed
    }

    private fun installHomeFeed(): Boolean {
        val feedSymbols = env.symbols?.homeRecommendFeed?.restore(classLoader) ?: return false
        val getUri = feedSymbols.getUri
        feedSymbols.responseGetItems.forEach { response ->
            env.hookAfter(response.getItems) { param ->
                val items = param.result as? List<*> ?: return@hookAfter
                if (items.none { item -> item != null && isAiFeedItem(item, getUri) }) return@hookAfter
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

    private fun stripRelatesFeed(shape: ReplyShape, original: Any): Any? = runCatching {
        if (AiDeclaredVideoRegistry.isEmpty()) return@runCatching null
        val cards = original.callMethod("getRelatesList") as? List<*> ?: return@runCatching null
        val retained = shape.retainUnknown(cards) ?: return@runCatching null
        if (retained.isEmpty()) return@runCatching null
        val builder = original.callMethod("toBuilder") ?: return@runCatching null
        builder.callMethod("clearRelates")
        builder.callMethod("addAllRelates", retained)
        val updated = builder.callMethod("build") ?: return@runCatching null
        val readback = updated.callMethod("getRelatesList") as? List<*>
        if (readback?.size != retained.size) return@runCatching null
        log("AiDeclaredVideo removed ${cards.size - retained.size} relates feed card(s)")
        updated
    }.onFailure { log("AiDeclaredVideo relates feed strip failed, keeping original reply", it) }.getOrNull()

    private fun isPassive(request: Any?): Boolean =
        AiDeclaredVideoPolicy.isPassiveRequest(request?.callMethod("getSpmid") as? String)

    private fun process(shape: ReplyShape, original: Any, passive: Boolean): Any? = runCatching {
        if (!shape.replyClass.isInstance(original)) return@runCatching null
        val facts = shape.facts(original)
        if (!facts.declared) {
            if (passive || facts.candidates.none { it.isVideo && AiDeclaredVideoRegistry.contains(it.aid) }) {
                return@runCatching null
            }
            val stripped = shape.stripRelates(original) ?: return@runCatching null
            log("AiDeclaredVideo removed known AI cards from detail relates")
            return@runCatching stripped
        }
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

        fun retainUnknown(cards: List<*>): List<Any>? {
            if (cards.any { it == null }) return null
            val retained = cards.filterNotNull().filterNot(::isKnownAiCard)
            return retained.takeIf { it.size != cards.size }
        }

        fun stripRelates(reply: Any): Any? {
            val tab = reply.child("hasTab", "getTab") ?: return null
            val tabModules = tab.callMethod("getTabModuleList") as? List<*> ?: return null
            var tabChanged = false
            val newTabModules = tabModules.map { tabModule ->
                if (tabModule == null) return null
                val rebuilt = rebuildTabModule(tabModule)
                if (rebuilt != null) tabChanged = true
                rebuilt ?: tabModule
            }
            if (!tabChanged) return null
            val newTab = tab.edit {
                callMethod("clearTabModule")
                callMethod("addAllTabModule", newTabModules)
            } ?: return null
            return reply.edit { callMethod("setTab", newTab) }
        }

        private fun rebuildTabModule(tabModule: Any): Any? {
            val intro = tabModule.child("hasIntroduction", "getIntroduction") ?: return null
            val modules = intro.callMethod("getModulesList") as? List<*> ?: return null
            var changed = false
            val newModules = modules.map { module ->
                if (module == null) return null
                val rebuilt = rebuildModule(module)
                if (rebuilt != null) changed = true
                rebuilt ?: module
            }
            if (!changed) return null
            val newIntro = intro.edit {
                callMethod("clearModules")
                callMethod("addAllModules", newModules)
            } ?: return null
            return tabModule.edit { callMethod("setIntroduction", newIntro) }
        }

        private fun rebuildModule(module: Any): Any? {
            val relates = module.child("hasRelates", "getRelates") ?: return null
            val cards = relates.callMethod("getCardsList") as? List<*> ?: return null
            val retained = retainUnknown(cards) ?: return null
            val newRelates = relates.edit {
                callMethod("clearCards")
                callMethod("addAllCards", retained)
            } ?: return null
            return module.edit { callMethod("setRelates", newRelates) }
        }

        private inline fun Any.edit(block: Any.() -> Unit): Any? {
            val builder = callMethod("toBuilder") ?: return null
            builder.block()
            return builder.callMethod("build")
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
        const val RELATES_FEED_REQUEST_CLASS = V1 + "RelatesFeedReq"
        const val RELATES_FEED_REPLY_CLASS = V1 + "RelatesFeedReply"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val SYNC_METHOD = "executeView"
        const val ASYNC_METHOD = "view"
        const val FEED_SYNC_METHOD = "executeRelatesFeed"
        const val FEED_ASYNC_METHOD = "relatesFeed"
    }
}
