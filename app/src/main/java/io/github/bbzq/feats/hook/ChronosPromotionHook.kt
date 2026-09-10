package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.isAssignableFromBoxed
import io.github.bbzq.feats.setObjectField
import io.github.bbzq.feats.symbol.RestoredChronosPromotionSymbols
import java.lang.reflect.Array as JavaArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

class ChronosPromotionHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var blockedCount = 0
    private var callbackErrorCount = 0
    private var originScrubErrorCount = 0
    private var localGetViewProgressErrorCount = 0
    private var localGetDmViewErrorCount = 0
    private var replyByteScrubErrorCount = 0
    private var dmViewByteScrubErrorCount = 0
    private var customDanmakuScrubErrorCount = 0
    private var commandDmListErrorCount = 0

    private val hookedViewProgressClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedDmViewClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())
    private val hookedCommandDmListClasses = Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) {
            log("startHook: ChronosPromotion disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }
        val symbols = env.symbols?.chronosPromotion?.restore(classLoader)
        if (symbols == null) {
            log("startHook: ChronosPromotion skipped because symbols are unavailable")
            return
        }

        val installed = installRpcReceiveHooks(symbols) +
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

    private fun detailStateBlockReason(request: Any, getters: List<String> = DETAIL_STATE_GETTERS): String? {
        getters.forEach { getter ->
            val value = request.callMethod(getter) ?: return@forEach
            if (value !is Collection<*> || value.isNotEmpty()) {
                return DETAIL_STATE_REASONS[getter] ?: getter
            }
        }
        return null
    }

    private fun openUrlBlockReason(request: Any): String? {
        val biz = request.callMethod("getBiz") as? String
        if (biz.hasAnyToken(PROMOTION_BIZ_TOKENS)) return "openUrl:biz=${biz.safeLogValue()}"

        val extra = request.callMethod("getExtra")
        val operId = extra?.callMethod("getOperId") as? String
        if (!operId.isNullOrBlank()) return "openUrl:operId=${operId.safeLogValue()}"

        val address = request.callMethod("getAddress") as? String
        if (address.hasAnyToken(PROMOTION_URL_TOKENS)) return "openUrl:address=${address.safeLogValue()}"

        val scheme = request.callMethod("getScheme") as? String
        if (scheme.hasAnyToken(PROMOTION_URL_TOKENS)) return "openUrl:scheme=${scheme.safeLogValue()}"

        return null
    }

    private fun stripViewProgressDetail(detail: Any): Int {
        var removed = 0
        removed += clearAttentionCard(detail)
        removed += filterVideoGuide(detail)
        removed += scrubOriginData(detail)
        return removed
    }

    private fun clearAttentionCard(detail: Any): Int {
        val dmResource = detail.callMethod("getDmResource") ?: return 0
        val attentionCard = dmResource.callMethod("getAttentionCard") ?: return 0
        val showTimes = attentionCard.callMethod("getShowTimeList") as? Collection<*> ?: return 0
        if (showTimes.isEmpty()) return 0
        val removed = showTimes.size
        if (dmResource.setObjectField("attentionCard", null)) {
            return removed
        }
        (showTimes as? MutableCollection<*>)?.clear()
        return removed
    }

    private fun filterVideoGuide(detail: Any): Int {
        val videoGuide = detail.callMethod("getVideoGuide") ?: return 0
        var removed = 0

        val rightMaterial = videoGuide.callMethod("getRightMaterial")
        if (rightMaterial != null && materialBlockReason(rightMaterial) != null) {
            if (videoGuide.setObjectField("rightMaterial", null)) removed++
        }

        val materialList = videoGuide.callMethod("getMaterialList") as? List<*>
        if (!materialList.isNullOrEmpty()) {
            val kept = materialList.filterNot { item -> item != null && materialBlockReason(item) != null }
            val removedMaterials = materialList.size - kept.size
            if (removedMaterials > 0 && videoGuide.setObjectField("materialList", ArrayList(kept))) {
                removed += removedMaterials
            }
        }

        val videoPoint = videoGuide.callMethod("getVideoPoint") ?: return removed
        val pointMaterial = videoPoint.callMethod("getPointMaterial")
        if (pointMaterial != null && pointMaterialBlockReason(pointMaterial) != null) {
            if (videoPoint.setObjectField("pointMaterial", null)) removed++
        }

        val videoPointList = videoPoint.callMethod("getVideoPointList") as? List<*> ?: return removed
        if (videoPointList.isEmpty()) return removed
        val keptPoints = videoPointList.filterNot { item -> item != null && videoPointBlockReason(item) != null }
        val removedPoints = videoPointList.size - keptPoints.size
        if (removedPoints > 0 && videoPoint.setObjectField("videoPointList", ArrayList(keptPoints))) {
            removed += removedPoints
        }
        return removed
    }

    private fun scrubOriginData(detail: Any): Int =
        runCatching {
            val origin = detail.callMethod("getOriginData") ?: return@runCatching 0
            scrubOriginReply(origin)
        }.onFailure { throwable ->
            val count = ++originScrubErrorCount
            if (count <= 3) {
                log("ChronosPromotion origin scrub failed: ${throwable.message}", throwable)
            }
        }.getOrDefault(0)

    private fun scrubOriginReply(origin: Any): Int {
        var removed = 0

        val videoGuide = origin.callMethod("getVideoGuide")
        if (videoGuide != null) {
            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getMaterialList",
                clearMethod = "clearMaterial",
                addAllMethod = "addAllMaterial",
            ) { item -> item != null && materialBlockReason(item) != null }

            val rightMaterial = videoGuide.callMethod("getRightMaterial")
            if (rightMaterial != null && materialBlockReason(rightMaterial) != null && videoGuide.invokeNoArgMethod("clearRightMaterial")) {
                removed++
            }

            val pointMaterial = videoGuide.callMethod("getPointMaterial")
            if (pointMaterial != null && pointMaterialBlockReason(pointMaterial) != null && videoGuide.invokeNoArgMethod("clearPointMaterial")) {
                removed++
            }

            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getPointsList",
                clearMethod = "clearPoints",
                addAllMethod = "addAllPoints",
            ) { item -> item != null && videoPointBlockReason(item) != null }

            val videoPoint = videoGuide.callMethod("getVideoPoint")
            if (videoPoint != null) {
                val pointMaterialInGuide = videoPoint.callMethod("getPointMaterial")
                if (
                    pointMaterialInGuide != null &&
                    pointMaterialBlockReason(pointMaterialInGuide) != null &&
                    videoPoint.invokeNoArgMethod("clearPointMaterial")
                ) {
                    removed++
                }
                removed += filterGeneratedList(
                    owner = videoPoint,
                    listGetter = "getPointsList",
                    clearMethod = "clearPoints",
                    addAllMethod = "addAllPoints",
                ) { item -> item != null && videoPointBlockReason(item) != null }
            }

            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getCommandDmsList",
                clearMethod = "clearCommandDms",
                addAllMethod = "addAllCommandDms",
            ) { item -> item != null && commandDanmakuBlockReason(item) != null }

            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getOperationCardList",
                clearMethod = "clearOperationCard",
                addAllMethod = "addAllOperationCard",
            ) { item -> item != null && operationCardBlockReason(item) != null }

            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getOperationCardNewList",
                clearMethod = "clearOperationCardNew",
                addAllMethod = "addAllOperationCardNew",
            ) { item -> item != null && operationCardBlockReason(item) != null }

            removed += filterGeneratedList(
                owner = videoGuide,
                listGetter = "getCardsSecondList",
                clearMethod = "clearCardsSecond",
                addAllMethod = "addAllCardsSecond",
            ) { item -> item != null && operationCardBlockReason(item) != null }

            val contractCard = videoGuide.callMethod("getContractCard")
            if (contractCard != null && contractCardBlockReason(contractCard) != null && videoGuide.invokeNoArgMethod("clearContractCard")) {
                removed++
            }
        }

        val dm = origin.callMethod("getDm")
        if (dm != null) {
            val attention = dm.callMethod("getAttention")
            val attentionTimes = attention?.callMethod("getShowTimeList") as? Collection<*>
            if (!attentionTimes.isNullOrEmpty() && dm.invokeNoArgMethod("clearAttention")) {
                removed += attentionTimes.size
            }

            removed += filterGeneratedList(
                owner = dm,
                listGetter = "getCommandDmsList",
                clearMethod = "clearCommandDms",
                addAllMethod = "addAllCommandDms",
            ) { item -> item != null && commandDanmakuBlockReason(item) != null }

            removed += filterGeneratedList(
                owner = dm,
                listGetter = "getCardsList",
                clearMethod = "clearCards",
                addAllMethod = "addAllCards",
            ) { item -> item != null && operationCardBlockReason(item) != null }
        }

        val rootPointMaterial = origin.callMethod("getPointMaterial")
        if (rootPointMaterial != null && pointMaterialBlockReason(rootPointMaterial) != null && origin.invokeNoArgMethod("clearPointMaterial")) {
            removed++
        }
        val buzzwordPeriods = origin.callMethod("getBuzzwordPeriodsList") as? Collection<*>
        if (!buzzwordPeriods.isNullOrEmpty() && origin.invokeNoArgMethod("clearBuzzwordPeriods")) {
            removed += buzzwordPeriods.size
        }
        removed += filterGeneratedList(
            owner = origin,
            listGetter = "getPointsList",
            clearMethod = "clearPoints",
            addAllMethod = "addAllPoints",
        ) { item -> item != null && videoPointBlockReason(item) != null }

        return removed
    }

    private fun filterGeneratedList(
        owner: Any,
        listGetter: String,
        clearMethod: String,
        addAllMethod: String,
        block: (Any?) -> Boolean,
    ): Int {
        val originalList = owner.callMethod(listGetter) as? List<*> ?: return 0
        if (originalList.isEmpty()) return 0
        val kept = originalList.filterNot(block)
        val removed = originalList.size - kept.size
        if (removed <= 0) return 0
        if (!owner.invokeNoArgMethod(clearMethod)) return 0
        if (kept.isNotEmpty() && !owner.invokeOneArgMethod(addAllMethod, kept)) return removed
        return removed
    }

    private fun hookLocalViewProgressCallback(clazz: Class<*>, replyTypes: Map<String, Class<*>>) {
        if (!hookedViewProgressClasses.add(clazz)) return
        clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
            env.hookBefore(invokeMethod) { param ->
                runCatching {
                    if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                    val extra = param.args.getOrNull(1) ?: return@hookBefore
                    val scrubbed = scrubLocalViewProgressExtra(extra, replyTypes)
                    if (scrubbed.removed > 0) logBlocked("localViewProgress:${scrubbed.removed}")
                    if (scrubbed.extra != null) {
                        param.args[1] = scrubbed.extra
                    }
                }.onFailure {
                    val count = ++localGetViewProgressErrorCount
                    if (count <= 3) {
                        log("ChronosPromotion LocalViewProgress invoke hook failed", it)
                    }
                }
            }
        }
    }

    private fun hookLocalDmViewCallback(clazz: Class<*>, replyType: Class<*>) {
        if (!hookedDmViewClasses.add(clazz)) return
        clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
            env.hookBefore(invokeMethod) { param ->
                runCatching {
                    if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                    val extra = param.args.getOrNull(1) ?: return@hookBefore
                    val scrubbed = scrubDmViewExtra(extra, replyType)
                    if (scrubbed.removed > 0) logBlocked("localDmView:${scrubbed.removed}")
                    if (scrubbed.extra != null) {
                        param.args[1] = scrubbed.extra
                    }
                }.onFailure {
                    val count = ++localGetDmViewErrorCount
                    if (count <= 3) {
                        log("ChronosPromotion LocalDmView invoke hook failed", it)
                    }
                }
            }
        }
    }

    private fun hookCommandDmListCallback(clazz: Class<*>, responseType: Class<*>) {
        if (!hookedCommandDmListClasses.add(clazz)) return
        clazz.allMethods().filter { it.name == "invoke" && it.parameterCount == 2 }.forEach { invokeMethod ->
            env.hookBefore(invokeMethod) { param ->
                runCatching {
                    if (!ModuleSettings.isBlockChronosPromotionEnabled(prefs)) return@hookBefore
                    val response = param.args.getOrNull(0) ?: return@hookBefore
                    if (responseType.isInstance(response)) {
                        val removed = scrubCommandDmListResponse(response)
                        if (removed > 0) logBlocked("commandDmList:$removed")
                    }
                }.onFailure {
                    val count = ++commandDmListErrorCount
                    if (count <= 3) {
                        log("ChronosPromotion CommandDmList invoke hook failed", it)
                    }
                }
            }
        }
    }


    private fun scrubLocalViewProgressExtra(extra: Any?, replyTypes: Map<String, Class<*>>): ScrubbedExtra {
        val original = extra as? Map<*, *> ?: return ScrubbedExtra(null, 0)
        var removed = 0
        var changed = false
        val cleaned = LinkedHashMap<Any?, Any?>(original.size)
        original.forEach { (key, value) ->
            val keyString = key as? String
            val replyType = keyString?.let(replyTypes::get)
            val bytes = value as? ByteArray
            if (replyType == null || bytes == null) {
                cleaned[key] = value
                return@forEach
            }

            val scrubbed = scrubReplyBytes(replyType, bytes)
            if (scrubbed.removed > 0) {
                removed += scrubbed.removed
                changed = true
            }
            cleaned[key] = scrubbed.bytes
        }
        return if (changed) ScrubbedExtra(cleaned, removed) else ScrubbedExtra(null, 0)
    }

    private fun scrubReplyBytes(replyType: Class<*>, bytes: ByteArray): ScrubbedBytes =
        runCatching {
            val reply = replyType.callStaticMethod("parseFrom", bytes) ?: return@runCatching ScrubbedBytes(bytes, 0)
            val removed = scrubOriginReply(reply)
            if (removed <= 0) {
                ScrubbedBytes(bytes, 0)
            } else {
                val cleanBytes = reply.callMethod("toByteArray") as? ByteArray ?: bytes
                ScrubbedBytes(cleanBytes, removed)
            }
        }.onFailure { throwable ->
            val count = ++replyByteScrubErrorCount
            if (count <= 3) {
                log("ChronosPromotion reply byte scrub failed: ${throwable.message}", throwable)
            }
        }.getOrDefault(ScrubbedBytes(bytes, 0))

    private fun scrubDmViewExtra(extra: Any?, replyType: Class<*>): ScrubbedExtra {
        val original = extra as? Map<*, *> ?: return ScrubbedExtra(null, 0)
        var removed = 0
        var changed = false
        val cleaned = LinkedHashMap<Any?, Any?>(original.size)
        original.forEach { (key, value) ->
            if (key != "reply" || value !is ByteArray) {
                cleaned[key] = value
                return@forEach
            }

            val scrubbed = scrubDmViewReplyBytes(replyType, value)
            if (scrubbed.removed > 0) {
                removed += scrubbed.removed
                changed = true
            }
            cleaned[key] = scrubbed.bytes
        }
        return if (changed) ScrubbedExtra(cleaned, removed) else ScrubbedExtra(null, 0)
    }

    private fun scrubDmViewReplyBytes(replyType: Class<*>, bytes: ByteArray): ScrubbedBytes =
        runCatching {
            val reply = replyType.callStaticMethod("parseFrom", bytes) ?: return@runCatching ScrubbedBytes(bytes, 0)
            val removed = scrubDmViewReply(reply)
            if (removed <= 0) {
                ScrubbedBytes(bytes, 0)
            } else {
                val cleanBytes = reply.callMethod("toByteArray") as? ByteArray ?: bytes
                ScrubbedBytes(cleanBytes, removed)
            }
        }.onFailure { throwable ->
            val count = ++dmViewByteScrubErrorCount
            if (count <= 3) {
                log("ChronosPromotion dm view byte scrub failed: ${throwable.message}", throwable)
            }
        }.getOrDefault(ScrubbedBytes(bytes, 0))

    private fun scrubDmViewReply(reply: Any): Int {
        val command = reply.callMethod("getCommand") ?: return 0
        return filterGeneratedList(
            owner = command,
            listGetter = "getCommandDmsList",
            clearMethod = "clearCommandDms",
            addAllMethod = "addAllCommandDms",
        ) { item -> item != null && commandDanmakuBlockReason(item) != null }
    }

    private fun scrubCommandDmListResponse(response: Any): Int =
        runCatching {
            val originalList = response.callMethod("getDanmakuList") as? List<*> ?: return@runCatching 0
            if (originalList.isEmpty()) return@runCatching 0
            val kept = originalList.filterNot { item -> item != null && commandDanmakuBlockReason(item) != null }
            val removed = originalList.size - kept.size
            if (removed <= 0) return@runCatching 0
            if (response.invokeOneArgMethod("setDanmakuList", ArrayList(kept))) {
                removed
            } else {
                0
            }
        }.onFailure { throwable ->
            val count = ++commandDmListErrorCount
            if (count <= 3) {
                log("ChronosPromotion command dm list scrub failed: ${throwable.message}", throwable)
            }
        }.getOrDefault(0)

    private fun scrubAddCustomDanmakuRequest(request: Any): ScrubbedCustomDanmakus =
        runCatching {
            val original = request.callMethod("getDms") as? Array<*> ?: return@runCatching ScrubbedCustomDanmakus(0, 0)
            if (original.isEmpty()) return@runCatching ScrubbedCustomDanmakus(0, 0)

            val kept = original.filterNot { item -> item != null && customDanmakuBlockReason(item) != null }
            val removed = original.size - kept.size
            if (removed <= 0) return@runCatching ScrubbedCustomDanmakus(0, original.size)
            if (kept.isEmpty() || replaceCustomDanmakus(request, original, kept)) {
                ScrubbedCustomDanmakus(removed, kept.size)
            } else {
                ScrubbedCustomDanmakus(0, original.size)
            }
        }.onFailure { throwable ->
            val count = ++customDanmakuScrubErrorCount
            if (count <= 3) {
                log("ChronosPromotion AddCustomDanmaku scrub failed: ${throwable.message}", throwable)
            }
        }.getOrDefault(ScrubbedCustomDanmakus(0, 0))

    private fun replaceCustomDanmakus(request: Any, original: Array<*>, kept: List<Any?>): Boolean {
        val componentType = original.javaClass.componentType ?: return false
        val cleaned = JavaArray.newInstance(componentType, kept.size)
        kept.forEachIndexed { index, item -> JavaArray.set(cleaned, index, item) }
        return request.invokeOneArgMethod("setDms", cleaned) || request.setObjectField("dms", cleaned)
    }

    private fun addDanmakuBlockReason(danmakuId: String?, type: Int, extra: Any?): String? {
        if (type == ADD_CUSTOM_DANMAKU_AD_TYPE) return "type=$type"
        if (danmakuId.hasAnyToken(PROMOTION_URL_TOKENS) || danmakuId.hasAnyToken(COMMAND_TOKEN_TEXTS)) {
            return "idToken"
        }
        if (type == ADD_CUSTOM_DANMAKU_NORMAL_TYPE) {
            return extra?.let { customDanmakuExtraBlockReason(it) }
        }
        val className = extra?.javaClass?.name ?: return null
        return if (className.contains("AdFloatView") || className.contains("AdDanmaku")) {
            customDanmakuExtraBlockReason(extra)
        } else {
            null
        }
    }

    private fun customDanmakuBlockReason(item: Any): String? {
        val type = (item.callMethod("getType") as? Number)?.toInt()
        if (type == ADD_CUSTOM_DANMAKU_AD_TYPE) return "customAdType"

        val danmakuId = item.callMethod("getDanmakuId") as? String
        if (danmakuId.hasAnyToken(PROMOTION_URL_TOKENS) || danmakuId.hasAnyToken(COMMAND_TOKEN_TEXTS)) {
            return "customIdToken"
        }

        val extra = item.callMethod("getExtra")
        if (extra != null) {
            customDanmakuExtraBlockReason(extra)?.let { return it }
        }

        return null
    }

    private fun customDanmakuExtraBlockReason(extra: Any): String? {
        val className = extra.javaClass.name
        if (className.contains("NormalExtra")) return normalExtraBlockReason(extra)
        if (className.contains("AdFloatView")) return "adFloatExtra"
        if (className.contains("AdDanmaku")) return "adDanmakuExtra"
        if (commandDanmakuBlockReason(extra) != null) return "customCommand"
        if (className.hasAnyToken(PROMOTION_BIZ_TOKENS)) return "extraClass"
        if (extra.hasPromotionToken()) return "extraToken"
        return null
    }

    private fun normalExtraBlockReason(extra: Any): String? {
        val metadataValues = NORMAL_EXTRA_METADATA_GETTERS.mapNotNull { getter ->
            extra.callMethod(getter)
        } + extra.javaClass.allFields()
            .filter { field -> field.name in NORMAL_EXTRA_METADATA_FIELDS }
            .mapNotNull { field -> runCatching { field.get(extra) }.getOrNull() }
            .toList()

        if (metadataValues.any { it.hasPromotionTokenValue() || it.hasCommandTokenValue() }) {
            return "normalExtraMeta"
        }

        val hasInteractiveMeta = metadataValues.any { value ->
            when (value) {
                is String -> value.isNotBlank()
                is Map<*, *> -> value.isNotEmpty()
                is Collection<*> -> value.isNotEmpty()
                else -> true
            }
        }
        val content = extra.callMethod("getContent") as? String
        if (hasInteractiveMeta && content.hasAnyToken(PROMOTION_TEXT_TOKENS)) {
            return "normalExtraContent"
        }

        return null
    }

    private fun Any.hasPromotionToken(): Boolean {
        if (this is String) {
            return hasAnyToken(PROMOTION_TEXT_TOKENS) || hasAnyToken(PROMOTION_URL_TOKENS)
        }
        if (this is Map<*, *>) return hasAnyPromotionToken()

        if (CUSTOM_EXTRA_TOKEN_GETTERS.any { getter ->
                val value = callMethod(getter)
                value.hasPromotionTokenValue()
            }
        ) {
            return true
        }

        return javaClass.allFields().any { field ->
            val value = runCatching { field.get(this) }.getOrNull()
            value.hasPromotionTokenValue()
        }
    }

    private fun Any?.hasPromotionTokenValue(): Boolean =
        when (this) {
            is String -> hasAnyToken(PROMOTION_TEXT_TOKENS) || hasAnyToken(PROMOTION_URL_TOKENS)
            is Map<*, *> -> hasAnyPromotionToken()
            is Collection<*> -> take(MAX_COLLECTION_TOKEN_SCAN).any { item ->
                item is String && (item.hasAnyToken(PROMOTION_TEXT_TOKENS) || item.hasAnyToken(PROMOTION_URL_TOKENS))
            }
            else -> false
        }

    private fun Any?.hasCommandTokenValue(): Boolean =
        when (this) {
            is String -> hasAnyToken(COMMAND_TOKEN_TEXTS)
            is Map<*, *> -> entries.any { (key, value) ->
                key?.toString().hasAnyToken(COMMAND_TOKEN_TEXTS) ||
                    value?.toString().hasAnyToken(COMMAND_TOKEN_TEXTS)
            }
            is Collection<*> -> take(MAX_COLLECTION_TOKEN_SCAN).any { item ->
                item is String && item.hasAnyToken(COMMAND_TOKEN_TEXTS)
            }
            else -> false
        }

    private fun materialBlockReason(material: Any): String? {
        val type = (material.callMethod("getType") as? Enum<*>)?.name
        if (type in BLOCKED_MATERIAL_TYPES) return "materialType=$type"

        val fields = listOf(
            material.callMethod("getBgColor") as? String,
            material.callMethod("getText") as? String,
            material.callMethod("getUrl") as? String,
            material.callMethod("getParam") as? String,
            material.callMethod("getIcon") as? String,
            material.callMethod("getStaticIcon") as? String,
            material.callMethod("getBgPic") as? String,
            material.callMethod("getPageType")?.toString(),
        )
        val report = material.callMethod("getReport") as? Map<*, *>
        return if (
            fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) } ||
            report.hasAnyPromotionToken()
        ) {
            "materialToken"
        } else {
            null
        }
    }

    private fun pointMaterialBlockReason(pointMaterial: Any): String? {
        val url = pointMaterial.callMethod("getUrl") as? String
        val source = pointMaterial.callMethod("getMaterialSource")?.toString()
        return if (url.hasAnyToken(PROMOTION_URL_TOKENS) || source.hasAnyToken(PROMOTION_TEXT_TOKENS)) {
            "pointMaterialToken"
        } else {
            null
        }
    }

    private fun videoPointBlockReason(videoPoint: Any): String? {
        val fields = listOf(
            videoPoint.callMethod("getContent") as? String,
            videoPoint.callMethod("getTeamName") as? String,
            videoPoint.callMethod("getTeamType") as? String,
            videoPoint.callMethod("getCover") as? String,
            videoPoint.callMethod("getLogoUrl") as? String,
        )
        return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
            "videoPointToken"
        } else {
            null
        }
    }

    private fun operationCardBlockReason(card: Any): String? {
        val fields = listOf(
            card.callMethod("getTitle") as? String,
            card.callMethod("getContent") as? String,
            card.callMethod("getButtonText") as? String,
            card.callMethod("getButtonTitle") as? String,
            card.callMethod("getButtonSelectedTitle") as? String,
            card.callMethod("getIcon") as? String,
            card.callMethod("getUrl") as? String,
            card.callMethod("getBizType")?.toString(),
            card.callMethod("getBiz_type")?.toString(),
            card.callMethod("getCardType")?.toString(),
            card.callMethod("getParamCase")?.toString(),
            card.callMethod("getRenderCase")?.toString(),
        )
        if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
            return "operationCardToken"
        }

        val content = card.callMethod("getContent")
        if (content != null && operationCardContentBlockReason(content) != null) return "operationCardContent"

        val jump = card.callMethod("getJump")
        if (jump != null && operationNestedBlockReason(jump) != null) return "operationJump"

        if (card.callMethod("hasFollow") == true) return "operationFollow"
        if (card.callMethod("hasReserve") == true) return "operationReserve"
        if (card.callMethod("hasGame") == true) return "operationGame"
        if (card.callMethod("hasGameBackflow") == true) return "operationGameBackflow"
        if (card.callMethod("hasJump") == true) return "operationJump"
        if (card.callMethod("hasSkip") == true) return "operationSkip"

        val skip = card.callMethod("getSkip")
        if (skip != null && operationCardBlockReason(skip) != null) return "operationSkipContent"

        val standard = card.callMethod("getStandard")
        if (standard != null && operationNestedBlockReason(standard) != null) return "operationStandard"

        return null
    }

    private fun scrubGeminiOperationMaterials(widget: Any): Int {
        var removed = 0
        widget.javaClass.allFields()
            .filter { field -> MutableList::class.java.isAssignableFrom(field.type) }
            .forEach { field ->
                @Suppress("UNCHECKED_CAST")
                val list = runCatching { field.get(widget) as? MutableList<Any?> }.getOrNull() ?: return@forEach
                val iterator = list.iterator()
                while (iterator.hasNext()) {
                    val item = iterator.next() ?: continue
                    if (materialBlockReason(item) != null) {
                        iterator.remove()
                        removed++
                    }
                }
            }
        return removed
    }

    private fun operationCardContentBlockReason(content: Any): String? {
        val fields = listOf(
            content.callMethod("getTitle") as? String,
            content.callMethod("getSubtitle") as? String,
            content.callMethod("getButtonTitle") as? String,
            content.callMethod("getButtonSelectedTitle") as? String,
            content.callMethod("getIcon") as? String,
        )
        return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
            "operationContentToken"
        } else {
            null
        }
    }

    private fun operationNestedBlockReason(value: Any): String? {
        val fields = listOf(
            value.callMethod("getTitle") as? String,
            value.callMethod("getSubtitle") as? String,
            value.callMethod("getDesc") as? String,
            value.callMethod("getContent") as? String,
            value.callMethod("getButtonText") as? String,
            value.callMethod("getButtonTitle") as? String,
            value.callMethod("getUrl") as? String,
            value.callMethod("getUri") as? String,
            value.callMethod("getLink") as? String,
            value.callMethod("getSchema") as? String,
            value.callMethod("getScheme") as? String,
        )
        return if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
            "nestedToken"
        } else {
            null
        }
    }

    private fun contractCardBlockReason(card: Any): String? {
        val text = card.callMethod("getText")
        val fields = listOf(
            text?.callMethod("getTitle") as? String,
            text?.callMethod("getSubtitle") as? String,
            text?.callMethod("getInlineTitle") as? String,
        )
        if (fields.any { it.hasAnyToken(PROMOTION_TEXT_TOKENS) || it.hasAnyToken(PROMOTION_URL_TOKENS) }) {
            return "contractTextToken"
        }
        if (card.callMethod("getIsFollowDisplay").isNonZeroNumber()) return "contractFollow"
        if (card.callMethod("isFollowDisplay").isNonZeroNumber()) return "contractFollow"
        return null
    }

    private fun commandDanmakuBlockReason(item: Any): String? {
        val command = (item.callMethod("getCommand") as? String)?.uppercase()
        if (command in BLOCKED_COMMANDS) return "command=$command"
        if (command.hasAnyToken(COMMAND_TOKEN_TEXTS)) return "commandToken"

        val content = item.callMethod("getContent") as? String
        val metadataValues = COMMAND_METADATA_GETTERS.mapNotNull { getter ->
            item.callMethod(getter)
        }
        if (content.hasAnyToken(PROMOTION_TEXT_TOKENS) || metadataValues.any { it.hasPromotionTokenValue() || it.hasCommandTokenValue() }) {
            return "commandToken"
        }
        if (content.hasAnyToken(PROMOTION_URL_TOKENS)) {
            return "commandUrl"
        }
        return null
    }

    private fun isAdDanmakuFloatView(item: Any): Boolean {
        if (item.callMethod("isFloatView") == true) return true
        if (item.callMethod("isFloatViewActivities") == true) return true
        if (item.callMethod("isFloatViewPermanent") == true) return true
        return item.javaClass.name in AD_FLOAT_VIEW_TYPES
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

    private fun logBlocked(reason: String) {
        val count = ++blockedCount
        if (count <= 20 || count % 50 == 0) {
            log("ChronosPromotion blocked $reason count=$count")
        }
    }

    private fun String?.hasAnyToken(tokens: Set<String>): Boolean {
        if (isNullOrBlank()) return false
        val lower = lowercase()
        return tokens.any { lower.contains(it) }
    }

    private fun Map<*, *>?.hasAnyPromotionToken(): Boolean {
        if (isNullOrEmpty()) return false
        return entries.any { (key, value) ->
            key?.toString().hasAnyToken(PROMOTION_TEXT_TOKENS) ||
                key?.toString().hasAnyToken(PROMOTION_URL_TOKENS) ||
                value?.toString().hasAnyToken(PROMOTION_TEXT_TOKENS) ||
                value?.toString().hasAnyToken(PROMOTION_URL_TOKENS)
        }
    }

    private fun String?.safeLogValue(): String =
        orEmpty()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_LOG_VALUE_LENGTH)

    private fun Any.invokeNoArgMethod(name: String): Boolean =
        invokeReflectMethod(name)

    private fun Any.invokeOneArgMethod(name: String, arg: Any?): Boolean =
        invokeReflectMethod(name, arg)

    private fun Any.invokeReflectMethod(name: String, vararg args: Any?): Boolean {
        val method = javaClass.allMethods().firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterCount == args.size &&
                candidate.parameterTypes.indices.all { index ->
                    candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
                }
        } ?: return false
        return runCatching {
            method.invoke(this, *args)
            true
        }.getOrDefault(false)
    }

    private fun Any?.isNonZeroNumber(): Boolean {
        val value = (this as? Number)?.toInt() ?: return false
        return value != 0
    }

    private data class ScrubbedExtra(
        val extra: Map<*, *>?,
        val removed: Int,
    )

    private data class ScrubbedBytes(
        val bytes: ByteArray,
        val removed: Int,
    )

    private data class ScrubbedCustomDanmakus(
        val removed: Int,
        val remaining: Int,
    )

    private companion object {
        private const val CHRONOS_ID_RPC_HANDLER = "rpcHandler"
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
        private const val MAX_LOG_VALUE_LENGTH = 80
        private const val MAX_COLLECTION_TOKEN_SCAN = 12
        private const val ADD_CUSTOM_DANMAKU_AD_TYPE = 101
        private const val ADD_CUSTOM_DANMAKU_NORMAL_TYPE = 104

        private val DETAIL_STATE_GETTERS = listOf(
            "getFollowStates",
            "getReserveState",
            "getClockInState",
            "getVoteState",
        )
        private val VIDEO_DETAIL_STATE_CHANGE_GETTERS = listOf(
            "getFollowStates",
            "getReserveState",
            "getClockInState",
            "getUpChargeState",
            "getVoteState",
            "getQuizState",
        )
        private val DETAIL_STATE_REASONS = mapOf(
            "getFollowStates" to "followGuide",
            "getReserveState" to "reserve",
            "getClockInState" to "checkIn",
            "getUpChargeState" to "upCharge",
            "getVoteState" to "vote",
            "getQuizState" to "quiz",
        )
        private val BLOCKED_COMMANDS = setOf(
            "#ATTENTION#",
            "#GOODSLIKE#",
            "#GOODSLIKEVOTE#",
            "#GRADE#",
            "#LINK#",
            "#QUIZ#",
            "#SSCHECKIN#",
            "#VOTE#",
            "#WATCH_LATER#",
            "#WATCH_LATER_V2#",
            "#WATCHLATER#",
            "#WATCH-LATER#",
        )
        private val COMMAND_TOKEN_TEXTS = setOf(
            "#attention#",
            "#goodslike#",
            "#goodslikevote#",
            "#grade#",
            "#link#",
            "#quiz#",
            "#sscheckin#",
            "#vote#",
            "#watch_later#",
            "#watch_later_v2#",
            "#watchlater#",
            "#watch-later#",
            "command_dm",
            "commanddm",
            "watch_later",
            "watchlater",
            "watch-later",
        )
        private val BLOCKED_MATERIAL_TYPES = setOf(
            "ACTIVITY",
            "ACTIVITY_ICON",
            "GENERAL_TYPE",
        )
        private val COMMAND_METADATA_GETTERS = listOf(
            "getAction",
            "getCommand",
            "getDanmakuId",
            "getExtra",
            "getIdStr",
            "getIdstr",
            "getLink",
            "getParam",
            "getScheme",
            "getUri",
            "getUrl",
        )
        private val NORMAL_EXTRA_METADATA_GETTERS = listOf(
            "getAction",
            "getAnimation",
            "getBiz",
            "getBizType",
            "getExtra",
            "getLink",
            "getParam",
            "getScheme",
            "getUri",
            "getUrl",
        )
        private val NORMAL_EXTRA_METADATA_FIELDS = setOf(
            "action",
            "animation",
            "biz",
            "bizType",
            "extra",
            "link",
            "param",
            "scheme",
            "uri",
            "url",
        )
        private val CUSTOM_EXTRA_TOKEN_GETTERS = listOf(
            "getAdNotes",
            "getAdTagText",
            "getBgPic",
            "getButtonText",
            "getButtonTitle",
            "getButton_text",
            "getContent",
            "getDesc",
            "getExtra",
            "getGotBtnText",
            "getGotNotes",
            "getImageUrl",
            "getImage_url",
            "getLink",
            "getNotes",
            "getParam",
            "getPriceDesc",
            "getQuestion",
            "getScheme",
            "getStaticIcon",
            "getSubtitle",
            "getSucceedBtnText",
            "getSucceedNotes",
            "getTitle",
            "getUri",
            "getUrl",
        )
        private val AD_FLOAT_VIEW_TYPES = setOf(
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatView",
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewPermanent",
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewAnswer",
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewGot",
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewCommerce",
            "tv.danmaku.biliplayerv2.service.interact.biz.model.AdDanmakuBean\$AdFloatViewCommon",
        )
        private val PROMOTION_BIZ_TOKENS = setOf(
            "ad",
            "commercial",
            "commerce",
            "goods",
            "mall",
            "shop",
            "game",
            "campaign",
            "promotion",
            "marketing",
            "watch-later",
            "watch_later",
            "watchlater",
        )
        private val PROMOTION_TEXT_TOKENS = setOf(
            "ad",
            "advert",
            "campaign",
            "commercial",
            "commerce",
            "game",
            "goods",
            "mall",
            "marketing",
            "promotion",
            "reserve",
            "shop",
            "watch-later",
            "watch later",
            "watch_later",
            "watchlater",
            "活动",
            "广告",
            "预约",
            "商业",
            "商品",
            "商城",
            "游戏",
            "稍后再看",
            "稍后看",
            "稍后观看",
            "投票",
            "打卡",
            "关注",
        )
        private val PROMOTION_URL_TOKENS = setOf(
            "cm.bilibili.com",
            "mall.bilibili.com",
            "game.bilibili.com",
            "show.bilibili.com",
            "adpage",
            "ad_",
            "/ad/",
            "compose/watch_later",
            "watch-later",
            "watch_later",
            "watch_later_v2",
            "watchlater",
            "bilibili://ad",
            "bilibili://mall",
            "bilibili://game",
            "bilibili://pegasus",
            "bilibili://user_center/watch_later",
            "bilibili://main/playset/watch-later",
            "activity://playset/watch-later",
            "bilibili.com/blackboard/activity",
        )
    }
}
