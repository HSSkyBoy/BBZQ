package io.github.bbzq.feats.hook.ad

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.hook.network.hookKotlinMoss
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredChronosPromotionSymbols
import io.github.bbzq.prefs.ModuleSettingsBridge
import java.lang.reflect.InvocationTargetException
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class ChronosPromotionHook(env: RoamingEnv) : BaseRoamingHook(env) {
    internal var blockedCount = 0

    internal var callbackErrorCount = 0

    internal var originScrubErrorCount = 0

    internal var localGetViewProgressErrorCount = 0

    internal var localGetDmViewErrorCount = 0

    internal var replyByteScrubErrorCount = 0

    internal var dmViewByteScrubErrorCount = 0

    internal var customDanmakuScrubErrorCount = 0

    internal var commandDmListErrorCount = 0

    internal val hookedViewProgressClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    internal val hookedDmViewClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    internal val hookedCommandDmListClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    internal val geminiListFieldsCache = ConcurrentHashMap<Class<*>, List<java.lang.reflect.Field>>()

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) {
            log("startHook: ChronosPromotion disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        val kotlinHooks = installKotlinResponseHooks()
        val symbols = env.symbols?.chronosPromotion?.restore(classLoader)
        val promotionHooks = env.hookPlayerPromotions(symbols)
        if (symbols == null) {
            log("startHook: ChronosPromotion symbols unavailable, Kotlin hooks=$kotlinHooks, promotion hooks=$promotionHooks")
            return
        }

        val installed = kotlinHooks + promotionHooks + installRpcReceiveHooks(symbols) +
            installLocalGetViewProgressHook(symbols) +
            installLocalDmViewHook(symbols) +
            installRemoteHandlerHooks(symbols) +
            installChronosMessageSenderHook(symbols) +
            installAdDanmakuFeedHook(symbols) +
            installInteractLayerViewProgressHook(symbols) +
            installGeminiOperationWidgetHooks(symbols)
        if (installed == 0) {
            log("startHook: ChronosPromotion no hook point found")
        } else {
            log("startHook: ChronosPromotion installed $installed hook point(s)")
        }
    }

    private fun installKotlinResponseHooks(): Int {
        val dmHooks = env.hookKotlinMoss(
            "com.bapis.bilibili.community.service.dm.v1.KDMMoss",
            "dmView",
            "com.bapis.bilibili.community.service.dm.v1.DmViewReply",
            enabled = { ModuleSettings.isBlockChronosPromotionEnabled(prefs) },
        ) { reply ->
            val removed = scrubDmViewReply(reply)
            if (removed > 0) logBlocked("kotlinDmView:$removed")
            reply
        }
        return dmHooks + listOf("view", "viewunite").sumOf { family ->
            env.hookKotlinMoss(
                "com.bapis.bilibili.app.$family.v1.KViewMoss",
                "viewProgress",
                "com.bapis.bilibili.app.$family.v1.ViewProgressReply",
                enabled = { ModuleSettings.isBlockChronosPromotionEnabled(prefs) },
            ) { reply ->
                val removed = scrubOriginReply(reply)
                if (removed > 0) logBlocked("kotlinViewProgress:$removed")
                reply
            }
        }
    }

    private fun installRpcReceiveHooks(symbols: RestoredChronosPromotionSymbols): Int {
        val updateDetailStateRequest = symbols.clazz(CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST)
        val openUrlRequest = symbols.clazz(CHRONOS_ID_OPEN_URL_REQUEST)
        val adDanmakuEventRequest = symbols.clazz(CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST)
        val notifyCommercialEventRequest = symbols.clazz(CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST)
        val methods = symbols.methods(CHRONOS_METHOD_RPC_RECEIVE)

        if (methods.isEmpty()) {
            log("startHook: ChronosPromotion rpc receive no hook point found")
            return 0
        }
        if (
            updateDetailStateRequest == null &&
            openUrlRequest == null &&
            adDanmakuEventRequest == null &&
            notifyCommercialEventRequest == null
        ) {
            log("startHook: ChronosPromotion rpc receive request types missing")
            return 0
        }

        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val requestClass = param.args.getOrNull(1) as? Class<*>
                val request = param.args.getOrNull(2)
                fun matches(type: Class<*>): Boolean =
                    requestClass == type || (request != null && type.isInstance(request))

                if (updateDetailStateRequest != null && matches(updateDetailStateRequest)) {
                    val reason = request?.let(::detailStateBlockReason) ?: return@hookBefore
                    invokeChronosCallback(param.args.getOrNull(4), null)
                    logBlocked(reason)
                    param.result = null
                    return@hookBefore
                }

                if (openUrlRequest != null && matches(openUrlRequest)) {
                    val reason = request?.let(::openUrlBlockReason) ?: return@hookBefore
                    invokeChronosCallback(param.args.getOrNull(4), null)
                    logBlocked(reason)
                    param.result = null
                    return@hookBefore
                }

                if (adDanmakuEventRequest != null && matches(adDanmakuEventRequest)) {
                    invokeChronosCallback(param.args.getOrNull(5), -1)
                    logBlocked("adDanmakuEvent")
                    param.result = null
                    return@hookBefore
                }

                if (notifyCommercialEventRequest != null && matches(notifyCommercialEventRequest)) {
                    val identifier = request?.callMethod("getIdentifier") as? String
                    logBlocked("commercialEvent:${identifier.orEmpty().take(MAX_LOG_VALUE_LENGTH)}")
                    param.result = null
                }
            }
            log("startHook: ChronosPromotion rpc receive at ${method.declaringClass.name}.${method.name}")
        }
        return methods.size
    }

    private fun installLocalGetViewProgressHook(symbols: RestoredChronosPromotionSymbols): Int {
        val requestType = symbols.clazz(CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST)
        if (requestType == null) {
            log("startHook: ChronosPromotion GetViewProgress request missing")
            return 0
        }
        val function2Type = symbols.clazz(CHRONOS_ID_FUNCTION2)
        if (function2Type == null) {
            log("startHook: ChronosPromotion Function2 missing for local view progress")
            return 0
        }
        val replyTypes = mapOf(
            "reply" to symbols.clazz(CHRONOS_ID_VIEW_PROGRESS_REPLY),
            "unitereply" to symbols.clazz(CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY),
        ).mapNotNull { (key, type) -> type?.let { key to it } }.toMap()
        if (replyTypes.isEmpty()) {
            log("startHook: ChronosPromotion view progress reply types missing")
            return 0
        }

        val methods = symbols.methods(CHRONOS_METHOD_LOCAL_VIEW_PROGRESS)
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val requestClass = param.args.getOrNull(1) as? Class<*> ?: return@hookBefore
                val request = param.args.getOrNull(2)
                if (requestClass != requestType && (request == null || !requestType.isInstance(request))) {
                    return@hookBefore
                }
                val callback = param.args.getOrNull(4) ?: return@hookBefore
                hookLocalViewProgressCallback(callback.javaClass, replyTypes)
            }
            log("startHook: ChronosPromotion local view progress at ${method.declaringClass.name}.${method.name}")
        }
        if (methods.isEmpty()) {
            log("startHook: ChronosPromotion local view progress no hook point found")
        }
        return methods.size
    }

    private fun installLocalDmViewHook(symbols: RestoredChronosPromotionSymbols): Int {
        val requestType = symbols.clazz(CHRONOS_ID_GET_DM_VIEW_REQUEST)
        if (requestType == null) {
            log("startHook: ChronosPromotion GetDmView request missing")
            return 0
        }
        val replyType = symbols.clazz(CHRONOS_ID_DM_VIEW_REPLY)
        if (replyType == null) {
            log("startHook: ChronosPromotion DmView reply missing")
            return 0
        }
        val function2Type = symbols.clazz(CHRONOS_ID_FUNCTION2)
        if (function2Type == null) {
            log("startHook: ChronosPromotion Function2 missing for local dm view")
            return 0
        }

        val methods = symbols.methods(CHRONOS_METHOD_LOCAL_DM_VIEW)
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val requestClass = param.args.getOrNull(1) as? Class<*> ?: return@hookBefore
                val request = param.args.getOrNull(2)
                if (requestClass != requestType && (request == null || !requestType.isInstance(request))) {
                    return@hookBefore
                }
                val callback = param.args.getOrNull(4) ?: return@hookBefore
                hookLocalDmViewCallback(callback.javaClass, replyType)
            }
            log("startHook: ChronosPromotion local dm view at ${method.declaringClass.name}.${method.name}")
        }
        if (methods.isEmpty()) {
            log("startHook: ChronosPromotion local dm view no hook point found")
        }
        return methods.size
    }

    private fun installRemoteHandlerHooks(symbols: RestoredChronosPromotionSymbols): Int {
        if (symbols.clazz(CHRONOS_ID_REMOTE_HANDLER) == null) {
            log("startHook: ChronosPromotion remote handler missing")
            return 0
        }

        return installVideoDetailStateChangeHook(symbols) +
            installViewProgressHook(symbols) +
            installCommandDanmakuHook(symbols) +
            installRemoteAddDanmakuHook(symbols) +
            installAdFloatExposureHook(symbols)
    }

    private fun installChronosMessageSenderHook(symbols: RestoredChronosPromotionSymbols): Int {
        if (symbols.clazz(CHRONOS_ID_MESSAGE_SENDER) == null) {
            log("startHook: ChronosPromotion message sender missing")
            return 0
        }
        val addCustomRequestType = symbols.clazz(CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST)
        val dmViewChangeRequestType = symbols.clazz(CHRONOS_ID_DM_VIEW_CHANGE_REQUEST)
        val dmViewReplyType = symbols.clazz(CHRONOS_ID_DM_VIEW_REPLY)
        val commandDanmakuSentType = symbols.clazz(CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST)
        val commandDmListRequestType = symbols.clazz(CHRONOS_ID_COMMAND_DM_LIST_REQUEST)
        val commandDmListResponseType = symbols.clazz(CHRONOS_ID_COMMAND_DM_LIST_RESPONSE)
        val function2Type = symbols.clazz(CHRONOS_ID_FUNCTION2)
        if (
            addCustomRequestType == null &&
            commandDanmakuSentType == null &&
            (dmViewChangeRequestType == null || dmViewReplyType == null) &&
            (commandDmListRequestType == null || commandDmListResponseType == null || function2Type == null)
        ) {
            log(
                "startHook: ChronosPromotion message sender request missing " +
                    "addCustom=$addCustomRequestType commandSent=$commandDanmakuSentType " +
                    "dmView=$dmViewChangeRequestType reply=$dmViewReplyType " +
                    "commandList=$commandDmListRequestType/$commandDmListResponseType function2=$function2Type",
            )
            return 0
        }

        var installed = 0
        val sendMethods = symbols.methods(CHRONOS_METHOD_MESSAGE_SEND)
        sendMethods.forEach { method ->
            env.hookBefore(method) { param ->
                val request = param.args.firstOrNull() ?: return@hookBefore
                if (commandDanmakuSentType?.isInstance(request) == true) {
                    val reason = commandDanmakuBlockReason(request) ?: return@hookBefore
                    logBlocked("commandDanmakuSent:$reason")
                    param.result = null
                    return@hookBefore
                }

                if (addCustomRequestType?.isInstance(request) == true) {
                    val scrubbed = scrubAddCustomDanmakuRequest(request)
                    if (scrubbed.removed > 0) {
                        logBlocked("addCustomDanmaku:${scrubbed.removed}")
                        if (scrubbed.remaining <= 0) {
                            param.result = null
                        }
                    }
                }

                if (dmViewChangeRequestType?.isInstance(request) == true && dmViewReplyType != null) {
                    val scrubbed = scrubDmViewExtra(param.args.getOrNull(1), dmViewReplyType)
                    if (scrubbed.removed > 0) {
                        param.args[1] = scrubbed.extra
                        logBlocked("dmViewChange:${scrubbed.removed}")
                    }
                }
            }
            log("startHook: ChronosPromotion message sender at ${method.declaringClass.name}.${method.name}")
        }
        installed += sendMethods.size
        if (sendMethods.isEmpty()) {
            log("startHook: ChronosPromotion message sender no hook point found")
        }

        if (commandDmListRequestType != null && commandDmListResponseType != null && function2Type != null) {
            val requestMethods = symbols.methods(CHRONOS_METHOD_COMMAND_DM_LIST)
            requestMethods.forEach { method ->
                env.hookBefore(method) { param ->
                    val request = param.args.firstOrNull() ?: return@hookBefore
                    if (!commandDmListRequestType.isInstance(request)) return@hookBefore
                    val callback = param.args.getOrNull(3) ?: return@hookBefore
                    hookCommandDmListCallback(callback.javaClass, commandDmListResponseType)
                }
                log("startHook: ChronosPromotion command dm list at ${method.declaringClass.name}.${method.name}")
            }
            installed += requestMethods.size
            if (requestMethods.isEmpty()) {
                log("startHook: ChronosPromotion command dm list no hook point found")
            }
        } else {
            log(
                "startHook: ChronosPromotion command dm list unavailable " +
                    "request=$commandDmListRequestType response=$commandDmListResponseType function2=$function2Type",
            )
        }
        return installed
    }

    private fun installVideoDetailStateChangeHook(symbols: RestoredChronosPromotionSymbols): Int {
        val requestType = symbols.clazz(CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST)
        if (requestType == null) {
            log("startHook: ChronosPromotion video detail state unavailable request=$requestType")
            return 0
        }
        val method = symbols.firstMethod(CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE)
        if (method == null) {
            log("startHook: ChronosPromotion video detail state no hook point found")
            return 0
        }

        env.hookBefore(method) { param ->
            val request = param.args.firstOrNull() ?: return@hookBefore
            if (!requestType.isInstance(request)) return@hookBefore
            val reason = detailStateBlockReason(request, VIDEO_DETAIL_STATE_CHANGE_GETTERS) ?: return@hookBefore
            logBlocked("videoDetailState:$reason")
            param.result = null
        }
        log("startHook: ChronosPromotion video detail state at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installViewProgressHook(symbols: RestoredChronosPromotionSymbols): Int {
        val detailType = symbols.clazz(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        if (detailType == null) {
            log("startHook: ChronosPromotion view progress detail missing")
            return 0
        }
        val method = symbols.firstMethod(CHRONOS_METHOD_REMOTE_VIEW_PROGRESS)
        if (method == null) {
            log("startHook: ChronosPromotion view progress no hook point found")
            return 0
        }

        env.hookBefore(method) { param ->
            val detail = param.args.firstOrNull() ?: return@hookBefore
            if (!detailType.isInstance(detail)) return@hookBefore
            val removed = stripViewProgressDetail(detail)
            if (removed > 0) logBlocked("viewProgress:$removed")
        }
        log("startHook: ChronosPromotion view progress at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installCommandDanmakuHook(symbols: RestoredChronosPromotionSymbols): Int {
        val method = symbols.firstMethod(CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU)
        if (method == null) {
            log("startHook: ChronosPromotion command danmaku no hook point found")
            return 0
        }

        env.hookBefore(method) { param ->
            val originalList = param.args.firstOrNull() as? List<*> ?: return@hookBefore
            if (originalList.isEmpty()) return@hookBefore
            val kept = originalList.filterNot { item -> item != null && commandDanmakuBlockReason(item) != null }
            val removed = originalList.size - kept.size
            if (removed <= 0) return@hookBefore

            logBlocked("commandDanmaku:$removed")
            param.args[0] = ArrayList(kept)
        }
        log("startHook: ChronosPromotion command danmaku at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installRemoteAddDanmakuHook(symbols: RestoredChronosPromotionSymbols): Int {
        val methods = symbols.methods(CHRONOS_METHOD_REMOTE_ADD_DANMAKU)
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val type = (param.args.getOrNull(1) as? Number)?.toInt() ?: return@hookBefore
                val reason = addDanmakuBlockReason(
                    danmakuId = param.args.firstOrNull() as? String,
                    type = type,
                    extra = param.args.getOrNull(2),
                ) ?: return@hookBefore

                logBlocked("remoteAddDanmaku:$reason")
                param.result = null
            }
            log("startHook: ChronosPromotion remote add danmaku at ${method.declaringClass.name}.${method.name}")
        }
        if (methods.isEmpty()) {
            log("startHook: ChronosPromotion remote add danmaku no hook point found")
        }
        return methods.size
    }

    private fun installAdFloatExposureHook(symbols: RestoredChronosPromotionSymbols): Int {
        val method = symbols.firstMethod(CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE)
        if (method == null) {
            log("startHook: ChronosPromotion ad float no hook point found")
            return 0
        }

        env.hookBefore(method) { param ->
            val originalList = param.args.firstOrNull() as? List<*> ?: return@hookBefore
            if (originalList.isEmpty()) return@hookBefore
            val kept = originalList.filterNot { item -> item != null && isAdDanmakuFloatView(item) }
            val removed = originalList.size - kept.size
            if (removed <= 0) return@hookBefore

            logBlocked("adFloatView:$removed")
            if (kept.isEmpty()) {
                param.result = null
            } else {
                param.args[0] = ArrayList(kept)
            }
        }
        log("startHook: ChronosPromotion ad float at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installAdDanmakuFeedHook(symbols: RestoredChronosPromotionSymbols): Int {
        if (symbols.clazz(CHRONOS_ID_AD_DANMAKU_DELEGATE) == null) {
            log("startHook: ChronosPromotion ad danmaku delegate missing")
            return 0
        }
        val methods = symbols.methods(CHRONOS_METHOD_AD_DANMAKU_FEED)
        methods.forEach { method ->
            env.hookBefore(method) { param ->
                val originalList = param.args.firstOrNull() as? List<*> ?: return@hookBefore
                if (originalList.isEmpty()) return@hookBefore
                val kept = originalList.filterNot { item -> item != null && isAdDanmakuFloatView(item) }
                val removed = originalList.size - kept.size
                if (removed <= 0) return@hookBefore

                param.args[0] = ArrayList(kept)
                logBlocked("adFloatFeed:$removed")
            }
            log("startHook: ChronosPromotion ad danmaku feed at ${method.declaringClass.name}.${method.name}")
        }
        if (methods.isEmpty()) {
            log("startHook: ChronosPromotion ad danmaku feed no hook point found")
        }
        return methods.size
    }

    private fun installInteractLayerViewProgressHook(symbols: RestoredChronosPromotionSymbols): Int {
        if (symbols.clazz(CHRONOS_ID_INTERACT_LAYER_SERVICE) == null) {
            log("startHook: ChronosPromotion interact layer service missing")
            return 0
        }
        val detailType = symbols.clazz(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        if (detailType == null) {
            log("startHook: ChronosPromotion interact layer detail missing")
            return 0
        }
        val method = symbols.firstMethod(CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS)
        if (method == null) {
            log("startHook: ChronosPromotion interact layer detail getter no hook point found")
            return 0
        }

        env.hookAfter(method) { param ->
            val detail = param.result ?: return@hookAfter
            if (!detailType.isInstance(detail)) return@hookAfter
            val removed = stripViewProgressDetail(detail)
            if (removed > 0) logBlocked("serviceViewProgress:$removed")
        }
        log("startHook: ChronosPromotion interact layer detail getter at ${method.declaringClass.name}.${method.name}")
        return 1
    }

    private fun installGeminiOperationWidgetHooks(symbols: RestoredChronosPromotionSymbols): Int {
        if (symbols.clazz(CHRONOS_ID_GEMINI_OPERATION_WIDGET) == null) {
            log("startHook: ChronosPromotion gemini operation widget missing")
            return 0
        }
        val detailType = symbols.clazz(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
        var installed = 0

        val renderMethods = symbols.methods(CHRONOS_METHOD_GEMINI_OPERATION_RENDER)
        renderMethods.forEach { method ->
            env.hookBefore(method) { param ->
                val widget = param.thisObject ?: return@hookBefore
                val removed = scrubGeminiOperationMaterials(widget)
                if (removed > 0) logBlocked("geminiOperationRender:$removed")
            }
            log("startHook: ChronosPromotion gemini operation render at ${method.declaringClass.name}.${method.name}")
        }
        installed += renderMethods.size

        val observerType = symbols.clazz(CHRONOS_ID_GEMINI_OPERATION_OBSERVER)
        if (observerType != null && detailType != null) {
            val updateMethod = symbols.firstMethod(CHRONOS_METHOD_GEMINI_OPERATION_UPDATE)
            if (updateMethod != null) {
                env.hookBefore(updateMethod) { param ->
                    val detail = param.args.firstOrNull() ?: return@hookBefore
                    if (!detailType.isInstance(detail)) return@hookBefore
                    val removed = stripViewProgressDetail(detail)
                    if (removed > 0) logBlocked("geminiOperationUpdate:$removed")
                }
                log("startHook: ChronosPromotion gemini operation update at ${updateMethod.declaringClass.name}.${updateMethod.name}")
                installed++
            } else {
                log("startHook: ChronosPromotion gemini operation update no hook point found")
            }
        } else {
            log("startHook: ChronosPromotion gemini operation observer unavailable observer=$observerType detail=$detailType")
        }

        if (installed == 0) {
            log("startHook: ChronosPromotion gemini operation no hook point found")
        }
        return installed
    }

    private fun invokeChronosCallback(callback: Any?, first: Any?) {
        callback ?: return
        val invoke = callback.javaClass.allMethods()
            .firstOrNull { it.name == "invoke" && it.parameterCount == 2 }
            ?: return
        runCatching {
            invoke.invoke(callback, first, null)
        }.onFailure { throwable ->
            val real = (throwable as? InvocationTargetException)?.targetException ?: throwable
            val count = ++callbackErrorCount
            if (count <= 3) {
                log("ChronosPromotion callback failed: ${real.message}", real)
            }
        }
    }

    internal fun logBlocked(reason: String) {
        val count = ++blockedCount
        if (count <= 20 || count % 50 == 0) {
            log("ChronosPromotion blocked $reason count=$count")
        }
    }

    private companion object {
        private const val CHRONOS_ID_REMOTE_HANDLER = "remoteHandler"
        private const val CHRONOS_ID_MESSAGE_SENDER = "messageSender"
        private const val CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST = "addCustomDanmakuRequest"
        private const val CHRONOS_ID_DM_VIEW_CHANGE_REQUEST = "dmViewChangeRequest"
        private const val CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST = "commandDanmakuSentRequest"
        private const val CHRONOS_ID_COMMAND_DM_LIST_REQUEST = "commandDmListRequest"
        private const val CHRONOS_ID_COMMAND_DM_LIST_RESPONSE = "commandDmListResponse"
        private const val CHRONOS_ID_AD_DANMAKU_DELEGATE = "adDanmakuDelegate"
        private const val CHRONOS_ID_INTERACT_LAYER_SERVICE = "interactLayerService"
        private const val CHRONOS_ID_GEMINI_OPERATION_WIDGET = "geminiOperationWidget"
        private const val CHRONOS_ID_GEMINI_OPERATION_OBSERVER = "geminiOperationObserver"
        private const val CHRONOS_ID_VIEW_PROGRESS_DETAIL = "viewProgressDetail"
        private const val CHRONOS_ID_VIEW_PROGRESS_REPLY = "viewProgressReply"
        private const val CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY = "uniteViewProgressReply"
        private const val CHRONOS_ID_DM_VIEW_REPLY = "dmViewReply"
        private const val CHRONOS_ID_GET_DM_VIEW_REQUEST = "getDmViewRequest"
        private const val CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST = "getViewProgressRequest"
        private const val CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST = "updateDetailStateRequest"
        private const val CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST = "videoDetailStateChangeRequest"
        private const val CHRONOS_ID_OPEN_URL_REQUEST = "openUrlRequest"
        private const val CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST = "adDanmakuEventRequest"
        private const val CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST = "notifyCommercialEventRequest"
        private const val CHRONOS_ID_FUNCTION2 = "function2"
        private const val CHRONOS_METHOD_RPC_RECEIVE = "rpcReceive"
        private const val CHRONOS_METHOD_LOCAL_VIEW_PROGRESS = "localViewProgress"
        private const val CHRONOS_METHOD_LOCAL_DM_VIEW = "localDmView"
        private const val CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE = "remoteVideoDetailState"
        private const val CHRONOS_METHOD_REMOTE_VIEW_PROGRESS = "remoteViewProgress"
        private const val CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU = "remoteCommandDanmaku"
        private const val CHRONOS_METHOD_REMOTE_ADD_DANMAKU = "remoteAddDanmaku"
        private const val CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE = "remoteAdFloatExposure"
        private const val CHRONOS_METHOD_MESSAGE_SEND = "messageSend"
        private const val CHRONOS_METHOD_COMMAND_DM_LIST = "commandDmList"
        private const val CHRONOS_METHOD_AD_DANMAKU_FEED = "adDanmakuFeed"
        private const val CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS = "interactLayerViewProgress"
        private const val CHRONOS_METHOD_GEMINI_OPERATION_RENDER = "geminiOperationRender"
        private const val CHRONOS_METHOD_GEMINI_OPERATION_UPDATE = "geminiOperationUpdate"
        private val VIDEO_DETAIL_STATE_CHANGE_GETTERS = listOf(
            "getFollowStates",
            "getReserveState",
            "getClockInState",
            "getUpChargeState",
            "getVoteState",
            "getQuizState",
        )
    }
}
