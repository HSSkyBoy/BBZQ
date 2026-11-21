package io.github.bbzq.feats.symbol

import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.hook.ad.PlayerPromotionAccess
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal fun scanChronosPromotion(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<ChronosPromotionSymbols> {
    val classes = ArrayList<NamedClassSymbol>()
    val methodGroups = ArrayList<NamedMethodGroup>()

    fun addClass(id: String, className: String): Class<*>? {
        val type = classLoader.loadClassOrNull(className) ?: return null
        classes += NamedClassSymbol(id, type.name)
        return type
    }

    fun addMethods(id: String, methods: List<Method>) {
        if (methods.isNotEmpty()) {
            methodGroups += NamedMethodGroup(id, methods.distinctBy(Method::toGenericString).map(MethodDescriptor::of))
        }
    }

    fun addMethods(id: String, methods: Sequence<Method>) {
        addMethods(id, methods.toList())
    }

    val rpcHandler = addClass(CHRONOS_ID_RPC_HANDLER, CHRONOS_RPC_HANDLER)
    val remoteHandler = addClass(CHRONOS_ID_REMOTE_HANDLER, REMOTE_SERVICE_HANDLER)
    val function2Type = addClass(CHRONOS_ID_FUNCTION2, KOTLIN_FUNCTION2)

    val updateDetailStateRequest = addClass(CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST, UPDATE_VIDEO_DETAIL_STATE_REQUEST)
    val openUrlRequest = addClass(CHRONOS_ID_OPEN_URL_REQUEST, OPEN_URL_SCHEME_REQUEST)
    val adDanmakuEventRequest = addClass(CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST, AD_DANMAKU_EVENT_REQUEST)
    val notifyCommercialEventRequest = addClass(CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST, NOTIFY_COMMERCIAL_EVENT_REQUEST)
    val getViewProgressRequest = addClass(CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST, GET_VIEW_PROGRESS_REQUEST)
    val getDmViewRequest = addClass(CHRONOS_ID_GET_DM_VIEW_REQUEST, GET_DM_VIEW_REQUEST)
    val viewProgressReply = addClass(CHRONOS_ID_VIEW_PROGRESS_REPLY, VIEW_PROGRESS_REPLY)
    val uniteViewProgressReply = addClass(CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY, UNITE_VIEW_PROGRESS_REPLY)
    val dmViewReply = addClass(CHRONOS_ID_DM_VIEW_REPLY, DM_VIEW_REPLY)
    val addCustomRequest = addClass(CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST, ADD_CUSTOM_DANMAKU_REQUEST)
    val dmViewChangeRequest = addClass(CHRONOS_ID_DM_VIEW_CHANGE_REQUEST, DM_VIEW_CHANGE_REQUEST)
    val commandDanmakuSentRequest = addClass(CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST, COMMAND_DANMAKU_SENT_REQUEST)
    val commandDmListRequest = addClass(CHRONOS_ID_COMMAND_DM_LIST_REQUEST, COMMAND_DM_LIST_REQUEST)
    val commandDmListResponse = addClass(CHRONOS_ID_COMMAND_DM_LIST_RESPONSE, COMMAND_DM_LIST_RESPONSE)
    val videoDetailStateChangeRequest = addClass(CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST, VIDEO_DETAIL_STATE_CHANGE_REQUEST)
    val viewProgressDetail = addClass(CHRONOS_ID_VIEW_PROGRESS_DETAIL, VIEW_PROGRESS_DETAIL)
    val senderScan = findChronosMessageSenderType(classLoader, remoteHandler, function2Type, bridge)
    val senderType = senderScan.type?.also {
        classes += NamedClassSymbol(CHRONOS_ID_MESSAGE_SENDER, it.name)
    }

    if (rpcHandler != null) {
        val invokeMethods = rpcHandler.allMethods()
            .filter { it.name == "invoke" && it.parameterCount == 6 }
            .distinctBy(Method::toGenericString)
            .toList()
        if (
            (updateDetailStateRequest != null && hasDetailStateGetterMethods(updateDetailStateRequest, DETAIL_STATE_GETTERS)) ||
            openUrlRequest != null ||
            adDanmakuEventRequest != null ||
            notifyCommercialEventRequest != null
        ) {
            addMethods(CHRONOS_METHOD_RPC_RECEIVE, invokeMethods)
        }
        if (getViewProgressRequest != null && function2Type != null && (viewProgressReply != null || uniteViewProgressReply != null)) {
            addMethods(CHRONOS_METHOD_LOCAL_VIEW_PROGRESS, invokeMethods)
        }
        if (getDmViewRequest != null && dmViewReply != null && function2Type != null) {
            addMethods(CHRONOS_METHOD_LOCAL_DM_VIEW, invokeMethods)
        }
    }

    if (remoteHandler != null) {
        if (videoDetailStateChangeRequest != null &&
            hasDetailStateGetterMethods(videoDetailStateChangeRequest, VIDEO_DETAIL_STATE_CHANGE_GETTERS)
        ) {
            addMethods(
                CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE,
                remoteHandler.allMethods().filter {
                    it.name == "onVideoDetailStateChanged" &&
                        it.parameterCount == 1 &&
                        it.parameterTypes[0] == videoDetailStateChangeRequest
                },
            )
        }
        if (viewProgressDetail != null) {
            addMethods(
                CHRONOS_METHOD_REMOTE_VIEW_PROGRESS,
                remoteHandler.allMethods().filter {
                    it.name == "configChronos" &&
                        it.parameterTypes.contentEquals(
                            arrayOf(viewProgressDetail, java.lang.Long.TYPE, java.lang.Long.TYPE),
                        )
                },
            )
        }
        addMethods(
            CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU,
            remoteHandler.allMethods().filter {
                it.name == "setCommandDanmakus" &&
                    it.parameterCount == 1 &&
                    List::class.java.isAssignableFrom(it.parameterTypes[0])
            },
        )
        addMethods(
            CHRONOS_METHOD_REMOTE_ADD_DANMAKU,
            remoteHandler.allMethods().filter {
                it.name == "addDanmaku" &&
                    it.parameterCount == 4 &&
                    it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == java.lang.Integer.TYPE &&
                    Map::class.java.isAssignableFrom(it.parameterTypes[3])
            },
        )
        addMethods(
            CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE,
            remoteHandler.allMethods().filter {
                it.name == "adDanmakuExposureRequest" &&
                    it.parameterTypes.contentEquals(arrayOf(List::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE))
            },
        )
    }

    if (senderType != null) {
        if (addCustomRequest != null || commandDanmakuSentRequest != null || (dmViewChangeRequest != null && dmViewReply != null)) {
            addMethods(
                CHRONOS_METHOD_MESSAGE_SEND,
                senderType.allMethods().filter {
                    it.isChronosMessageSendMethod()
                },
            )
        }
        if (commandDmListRequest != null && commandDmListResponse != null && function2Type != null) {
            addMethods(
                CHRONOS_METHOD_COMMAND_DM_LIST,
                senderType.allMethods().filter {
                    it.isChronosCommandDmListMethod(function2Type)
                },
            )
        }
    }

    val adDanmakuDelegate = addClass(CHRONOS_ID_AD_DANMAKU_DELEGATE, AD_DANMAKU_DELEGATE)
    adDanmakuDelegate?.let { delegate ->
        addMethods(
            CHRONOS_METHOD_AD_DANMAKU_FEED,
            delegate.allMethods().filter {
                it.name == "feedData2Chronos" &&
                    it.parameterTypes.contentEquals(arrayOf(List::class.java, java.lang.Long.TYPE, java.lang.Long.TYPE))
            },
        )
    }
    val interactLayerService = addClass(CHRONOS_ID_INTERACT_LAYER_SERVICE, INTERACT_LAYER_SERVICE)
    interactLayerService?.let { service ->
        if (viewProgressDetail != null) {
            addMethods(
                CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS,
                service.allMethods().filter {
                    it.name == "getViewProgressDetail" &&
                        it.parameterCount == 0 &&
                        viewProgressDetail.isAssignableFrom(it.returnType)
                },
            )
        }
    }
    val geminiOperationWidget = addClass(CHRONOS_ID_GEMINI_OPERATION_WIDGET, GEMINI_OPERATION_WIDGET)
    geminiOperationWidget?.let { widget ->
        val declared = widget.declaredMethods.filter {
            it.name in GEMINI_OPERATION_RENDER_METHOD_NAMES &&
                it.parameterCount == 0 &&
                it.returnType == java.lang.Void.TYPE
        }
        val renderMethods = declared.ifEmpty {
            widget.allMethods().filter {
                it.name in GEMINI_OPERATION_RENDER_METHOD_NAMES &&
                    it.parameterCount == 0 &&
                    it.returnType == java.lang.Void.TYPE
            }.take(1).toList()
        }
        addMethods(CHRONOS_METHOD_GEMINI_OPERATION_RENDER, renderMethods)
    }
    val geminiOperationObserver = addClass(CHRONOS_ID_GEMINI_OPERATION_OBSERVER, GEMINI_OPERATION_OBSERVER)
    geminiOperationObserver?.let { observer ->
        if (viewProgressDetail != null) {
            addMethods(
                CHRONOS_METHOD_GEMINI_OPERATION_UPDATE,
                observer.allMethods().filter {
                    it.name == "onUpdate" &&
                        it.parameterCount == 1 &&
                        viewProgressDetail.isAssignableFrom(it.parameterTypes[0])
                },
            )
        }
    }

    val freeDataGuide = PlayerPromotionAccess.directGuide(classLoader) ?: runCatching {
        bridge()?.findMethod(
            FindMethod.create().searchPackages(PlayerPromotionAccess.NETWORK_PACKAGE)
                .matcher(MethodMatcher.create().paramCount(1).returnType(Any::class.java)
                    .usingStrings(PlayerPromotionAccess.GUIDE_MARK)),
        ).orEmpty().mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .filter(PlayerPromotionAccess::isGuide).distinctBy(Method::toGenericString).singleOrNull()
    }.getOrNull()
    addMethods(PlayerPromotionAccess.GUIDE_METHOD, listOfNotNull(freeDataGuide))

    val advertFloat = classLoader.loadClassOrNull("${PlayerPromotionAccess.MODEL_PACKAGE}.DmAdvert")
        ?.declaredMethods?.singleOrNull { it.name == "getFloatLayers" && PlayerPromotionAccess.isAdvertFloat(it) }
        ?: runCatching {
            val cardClass = "${PlayerPromotionAccess.MODEL_PACKAGE}.Card"
            val matcher = MethodMatcher.create().paramCount(0).returnType(List::class.java)
            for (name in listOf("isValidCommon", "isValidCommerce", "isValidGuide")) {
                matcher.addInvoke(MethodMatcher.create().declaredClass(cardClass).name(name).paramCount(0))
            }
            bridge()?.findMethod(FindMethod.create().searchPackages(PlayerPromotionAccess.MODEL_PACKAGE).matcher(matcher))
                .orEmpty().filter { data ->
                    // 混合面板还读取普通弹幕，不能作为纯广告浮层入口。
                    data.invokes.none { it.declaredClassName == cardClass && it.name == "isValidDanmaku" }
                }.mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .filter(PlayerPromotionAccess::isAdvertFloat).distinctBy(Method::toGenericString).singleOrNull()
        }.getOrNull()
    addMethods(PlayerPromotionAccess.FLOAT_METHOD, listOfNotNull(advertFloat))

    val totalMethods = methodGroups.sumOf { it.methods.size }
    if (totalMethods == 0) {
        return SymbolScanResult.Missing("chronos promotion hook points not found")
    }
    val childHookPoints = buildChronosPromotionHookPointStatus(
        methodGroups = methodGroups,
        rpcHandler = rpcHandler,
        updateDetailStateRequest = updateDetailStateRequest,
        openUrlRequest = openUrlRequest,
        adDanmakuEventRequest = adDanmakuEventRequest,
        notifyCommercialEventRequest = notifyCommercialEventRequest,
        senderType = senderType,
        addCustomRequest = addCustomRequest,
        dmViewChangeRequest = dmViewChangeRequest,
        dmViewReply = dmViewReply,
        commandDanmakuSentRequest = commandDanmakuSentRequest,
        commandDmListRequest = commandDmListRequest,
        commandDmListResponse = commandDmListResponse,
        function2Type = function2Type,
        remoteHandler = remoteHandler,
        getViewProgressRequest = getViewProgressRequest,
        getDmViewRequest = getDmViewRequest,
        viewProgressReply = viewProgressReply,
        uniteViewProgressReply = uniteViewProgressReply,
        videoDetailStateChangeRequest = videoDetailStateChangeRequest,
        viewProgressDetail = viewProgressDetail,
        adDanmakuDelegate = adDanmakuDelegate,
        interactLayerService = interactLayerService,
        geminiOperationWidget = geminiOperationWidget,
        geminiOperationObserver = geminiOperationObserver,
        senderScan = senderScan,
    )
    val symbols = ChronosPromotionSymbols(
        classSymbols = classes.distinctBy { it.id },
        methodGroups = methodGroups.distinctBy { it.id },
        evidence = "classes=${classes.distinctBy { it.id }.size},methodGroups=${methodGroups.size},methods=$totalMethods,sender=${senderScan.evidence}",
    )
    return SymbolScanResult.Found(symbols, "ChronosPromotion", symbols.evidence, childHookPoints)
}

private fun buildChronosPromotionHookPointStatus(
    methodGroups: List<NamedMethodGroup>,
    rpcHandler: Class<*>?,
    updateDetailStateRequest: Class<*>?,
    openUrlRequest: Class<*>?,
    adDanmakuEventRequest: Class<*>?,
    notifyCommercialEventRequest: Class<*>?,
    senderType: Class<*>?,
    addCustomRequest: Class<*>?,
    dmViewChangeRequest: Class<*>?,
    dmViewReply: Class<*>?,
    commandDanmakuSentRequest: Class<*>?,
    commandDmListRequest: Class<*>?,
    commandDmListResponse: Class<*>?,
    function2Type: Class<*>?,
    remoteHandler: Class<*>?,
    getViewProgressRequest: Class<*>?,
    getDmViewRequest: Class<*>?,
    viewProgressReply: Class<*>?,
    uniteViewProgressReply: Class<*>?,
    videoDetailStateChangeRequest: Class<*>?,
    viewProgressDetail: Class<*>?,
    adDanmakuDelegate: Class<*>?,
    interactLayerService: Class<*>?,
    geminiOperationWidget: Class<*>?,
    geminiOperationObserver: Class<*>?,
    senderScan: ChronosSenderScan,
): List<HookPointStatus> {
    fun methodCount(id: String): Int =
        methodGroups.firstOrNull { it.id == id }?.methods?.size ?: 0

    fun status(id: String, methodId: String, missing: List<String>, evidence: String): HookPointStatus {
        val methods = methodCount(methodId)
        return when {
            missing.isNotEmpty() -> HookPointStatus(
                id = id,
                state = HookPointState.MISSING,
                missing = "fail closed: ${missing.joinToString()}",
                evidence = evidence,
            )
            methods == 0 -> HookPointStatus(
                id = id,
                state = HookPointState.MISSING,
                missing = "fail closed: method group $methodId not found",
                evidence = evidence,
            )
            else -> HookPointStatus.found(id, "ChronosPromotion.$methodId", "methods=$methods,$evidence")
        }
    }

    val receiveMissing = buildList {
        if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
        if (updateDetailStateRequest == null) add(CHRONOS_ID_UPDATE_DETAIL_STATE_REQUEST)
        if (openUrlRequest == null) add(CHRONOS_ID_OPEN_URL_REQUEST)
        if (adDanmakuEventRequest == null) add(CHRONOS_ID_AD_DANMAKU_EVENT_REQUEST)
        if (notifyCommercialEventRequest == null) add(CHRONOS_ID_NOTIFY_COMMERCIAL_EVENT_REQUEST)
    }
    val senderMissing = buildList {
        if (senderType == null) add(senderScan.missing ?: CHRONOS_ID_MESSAGE_SENDER)
        if (addCustomRequest == null) add(CHRONOS_ID_ADD_CUSTOM_DANMAKU_REQUEST)
        if (commandDanmakuSentRequest == null) add(CHRONOS_ID_COMMAND_DANMAKU_SENT_REQUEST)
        if (dmViewChangeRequest == null) add(CHRONOS_ID_DM_VIEW_CHANGE_REQUEST)
        if (dmViewReply == null) add(CHRONOS_ID_DM_VIEW_REPLY)
    }
    val commandDmListMissing = buildList {
        if (senderType == null) add(senderScan.missing ?: CHRONOS_ID_MESSAGE_SENDER)
        if (commandDmListRequest == null) add(CHRONOS_ID_COMMAND_DM_LIST_REQUEST)
        if (commandDmListResponse == null) add(CHRONOS_ID_COMMAND_DM_LIST_RESPONSE)
        if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
    }
    val localViewProgressMissing = buildList {
        if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
        if (getViewProgressRequest == null) add(CHRONOS_ID_GET_VIEW_PROGRESS_REQUEST)
        if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
        if (viewProgressReply == null && uniteViewProgressReply == null) add("$CHRONOS_ID_VIEW_PROGRESS_REPLY|$CHRONOS_ID_UNITE_VIEW_PROGRESS_REPLY")
    }
    val localDmViewMissing = buildList {
        if (rpcHandler == null) add(CHRONOS_ID_RPC_HANDLER)
        if (getDmViewRequest == null) add(CHRONOS_ID_GET_DM_VIEW_REQUEST)
        if (dmViewReply == null) add(CHRONOS_ID_DM_VIEW_REPLY)
        if (function2Type == null) add(CHRONOS_ID_FUNCTION2)
    }
    val remoteVideoDetailMissing = buildList {
        if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
        if (videoDetailStateChangeRequest == null) add(CHRONOS_ID_VIDEO_DETAIL_STATE_CHANGE_REQUEST)
    }
    val remoteViewProgressMissing = buildList {
        if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
        if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
    }
    val remoteHandlerMissing = buildList {
        if (remoteHandler == null) add(CHRONOS_ID_REMOTE_HANDLER)
    }
    val adDanmakuMissing = buildList {
        if (adDanmakuDelegate == null) add(CHRONOS_ID_AD_DANMAKU_DELEGATE)
    }
    val interactLayerMissing = buildList {
        if (interactLayerService == null) add(CHRONOS_ID_INTERACT_LAYER_SERVICE)
        if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
    }
    val geminiRenderMissing = buildList {
        if (geminiOperationWidget == null) add(CHRONOS_ID_GEMINI_OPERATION_WIDGET)
    }
    val geminiUpdateMissing = buildList {
        if (geminiOperationObserver == null) add(CHRONOS_ID_GEMINI_OPERATION_OBSERVER)
        if (viewProgressDetail == null) add(CHRONOS_ID_VIEW_PROGRESS_DETAIL)
    }
    return listOf(
        status(
            id = HP_CHRONOS_RPC_RECEIVE,
            methodId = CHRONOS_METHOD_RPC_RECEIVE,
            missing = receiveMissing,
            evidence = "requests=${4 - receiveMissing.count { it != CHRONOS_ID_RPC_HANDLER }}/4",
        ),
        status(
            id = HP_CHRONOS_LOCAL_VIEW_PROGRESS,
            methodId = CHRONOS_METHOD_LOCAL_VIEW_PROGRESS,
            missing = localViewProgressMissing,
            evidence = "reply=${viewProgressReply != null},uniteReply=${uniteViewProgressReply != null}",
        ),
        status(
            id = HP_CHRONOS_LOCAL_DM_VIEW,
            methodId = CHRONOS_METHOD_LOCAL_DM_VIEW,
            missing = localDmViewMissing,
            evidence = "request=${getDmViewRequest != null},reply=${dmViewReply != null}",
        ),
        status(
            id = HP_CHRONOS_MESSAGE_SENDER,
            methodId = CHRONOS_METHOD_MESSAGE_SEND,
            missing = senderMissing,
            evidence = "sender=${senderScan.evidence},requests=${4 - senderMissing.count { it != senderScan.missing && it != CHRONOS_ID_MESSAGE_SENDER }}/4",
        ),
        status(
            id = HP_CHRONOS_COMMAND_DM_LIST,
            methodId = CHRONOS_METHOD_COMMAND_DM_LIST,
            missing = commandDmListMissing,
            evidence = "sender=${senderScan.evidence},request=${commandDmListRequest != null},response=${commandDmListResponse != null}",
        ),
        status(
            id = HP_CHRONOS_REMOTE_VIDEO_DETAIL_STATE,
            methodId = CHRONOS_METHOD_REMOTE_VIDEO_DETAIL_STATE,
            missing = remoteVideoDetailMissing,
            evidence = "request=${videoDetailStateChangeRequest != null}",
        ),
        status(
            id = HP_CHRONOS_REMOTE_VIEW_PROGRESS,
            methodId = CHRONOS_METHOD_REMOTE_VIEW_PROGRESS,
            missing = remoteViewProgressMissing,
            evidence = "detail=${viewProgressDetail != null}",
        ),
        status(
            id = HP_CHRONOS_REMOTE_COMMAND_DANMAKU,
            methodId = CHRONOS_METHOD_REMOTE_COMMAND_DANMAKU,
            missing = remoteHandlerMissing,
            evidence = "remoteHandler=${remoteHandler != null}",
        ),
        status(
            id = HP_CHRONOS_REMOTE_ADD_DANMAKU,
            methodId = CHRONOS_METHOD_REMOTE_ADD_DANMAKU,
            missing = remoteHandlerMissing,
            evidence = "remoteHandler=${remoteHandler != null}",
        ),
        status(
            id = HP_CHRONOS_REMOTE_AD_FLOAT_EXPOSURE,
            methodId = CHRONOS_METHOD_REMOTE_AD_FLOAT_EXPOSURE,
            missing = remoteHandlerMissing,
            evidence = "remoteHandler=${remoteHandler != null}",
        ),
        status(
            id = HP_CHRONOS_AD_DANMAKU_FEED,
            methodId = CHRONOS_METHOD_AD_DANMAKU_FEED,
            missing = adDanmakuMissing,
            evidence = "delegate=${adDanmakuDelegate != null}",
        ),
        status(
            id = HP_CHRONOS_INTERACT_LAYER_VIEW_PROGRESS,
            methodId = CHRONOS_METHOD_INTERACT_LAYER_VIEW_PROGRESS,
            missing = interactLayerMissing,
            evidence = "service=${interactLayerService != null},detail=${viewProgressDetail != null}",
        ),
        status(
            id = HP_CHRONOS_GEMINI_OPERATION_RENDER,
            methodId = CHRONOS_METHOD_GEMINI_OPERATION_RENDER,
            missing = geminiRenderMissing,
            evidence = "widget=${geminiOperationWidget != null}",
        ),
        status(
            id = HP_CHRONOS_GEMINI_OPERATION_UPDATE,
            methodId = CHRONOS_METHOD_GEMINI_OPERATION_UPDATE,
            missing = geminiUpdateMissing,
            evidence = "observer=${geminiOperationObserver != null},detail=${viewProgressDetail != null}",
        ),
    )
}

private fun hasDetailStateGetterMethods(requestType: Class<*>, getters: List<String>): Boolean =
    getters.any { getter -> requestType.allMethods().any { it.name == getter && it.parameterCount == 0 } }

private fun findChronosMessageSenderType(
    classLoader: ClassLoader,
    remoteHandler: Class<*>?,
    function2Type: Class<*>?,
    bridge: () -> DexKitBridge?,
): ChronosSenderScan {
    if (remoteHandler == null) {
        return ChronosSenderScan(type = null, evidence = "remoteHandler=false", missing = CHRONOS_ID_MESSAGE_SENDER)
    }
    val fields = runCatching { remoteHandler.allFields().toList() }.getOrElse { throwable ->
        val reason = "fields=${throwable.scanMessage()}"
        return ChronosSenderScan(type = null, evidence = reason, missing = "$CHRONOS_ID_MESSAGE_SENDER($reason)")
    }
    val senderErrors = ArrayList<String>()
    val remoteMethods = runCatching { remoteHandler.allMethods().toList() }.getOrElse { throwable ->
        senderErrors += "remoteMethods=${throwable.scanMessage()}"
        emptyList()
    }
    val fieldTypes = fields.map { it.type }.distinctBy { it.name }

    fun candidateOf(type: Class<*>, source: String): ChronosSenderCandidate? {
        if (type.isPrimitive || type.name == remoteHandler.name || !type.isConcreteHookClass()) return null
        val methods = runCatching { type.allMethods().toList() }.getOrElse { throwable ->
            senderErrors += "${type.name}.methods=${throwable.scanMessage()}"
            return null
        }
        return ChronosSenderCandidate(
            type = type,
            source = source,
            messageMethods = methods.count { it.isChronosMessageSendMethod() },
            commandMethods = function2Type?.let { function2 ->
                methods.count { it.isChronosCommandDmListMethod(function2) }
            } ?: methods.count { it.isChronosSenderRequestMethod(function2Type, allowAbstract = false) },
        )
    }

    val directCandidates = fieldTypes
        .mapNotNull { type -> candidateOf(type, "field") }

    val senderInterfaces = (fieldTypes.asSequence().flatMap { sequenceOf(it) + it.chronosImplementedInterfaces() } +
        remoteMethods.asSequence().map { it.returnType })
        .filter { type -> type.isInterface && type.isChronosSenderInterface(function2Type) }
        .distinctBy { it.name }
        .toList()

    val implementorErrors = ArrayList<String>()
    val interfaceCandidates = senderInterfaces.flatMap { senderInterface ->
        val scan = findConcreteImplementors(classLoader, bridge, senderInterface.name)
        scan.error?.let { implementorErrors += it }
        scan.classes.mapNotNull { type -> candidateOf(type, "iface:${senderInterface.name.substringAfterLast('.')}") }
    }

    val candidates = (directCandidates + interfaceCandidates)
        .filter { it.messageMethods > 0 || it.commandMethods > 0 }
        .groupBy { it.type.name }
        .map { (_, sameType) ->
            val first = sameType.first()
            first.copy(source = sameType.map { it.source }.distinct().joinToString("+"))
        }
    val completeCandidates = candidates.filter { it.messageMethods > 0 && it.commandMethods > 0 }
    val directCompleteCandidates = completeCandidates.filter { it.source.split('+').contains("field") }
    val commandCandidates = candidates.filter { it.commandMethods > 0 }
    val directCommandCandidates = commandCandidates.filter { it.source.split('+').contains("field") }
    val messageCandidates = candidates.filter { it.messageMethods > 0 }
    val selected = when {
        directCompleteCandidates.size == 1 -> directCompleteCandidates.single()
        completeCandidates.size == 1 -> completeCandidates.single()
        directCommandCandidates.size == 1 -> directCommandCandidates.single()
        commandCandidates.size == 1 -> commandCandidates.single()
        messageCandidates.size == 1 -> messageCandidates.single()
        else -> null
    }
    val evidenceParts = listOf(
        "fields=${fieldTypes.size}",
        "interfaces=${senderInterfaces.size}",
        "candidates=${candidates.size}",
        "complete=${completeCandidates.size}",
        "source=${selected?.source ?: "-"}",
        "methods=${selected?.messageMethods ?: 0}/${selected?.commandMethods ?: 0}",
        "type=${selected?.type?.name ?: "-"}",
    )
    val evidence = (
        evidenceParts +
            senderErrors.take(1).map { "error=$it" } +
            implementorErrors.take(1).map { "scan=$it" }
        )
        .joinToString(",")
        .take(360)
    return ChronosSenderScan(
        type = selected?.type,
        evidence = evidence,
        missing = if (selected == null) "$CHRONOS_ID_MESSAGE_SENDER($evidence)" else null,
    )
}

private fun Method.isChronosMessageSendMethod(): Boolean =
    isChronosVoidLikeReturn() &&
        !Modifier.isStatic(modifiers) &&
        !Modifier.isAbstract(modifiers) &&
        parameterCount == 2 &&
        Map::class.java.isAssignableFrom(parameterTypes[1])

private fun Method.isChronosCommandDmListMethod(function2Type: Class<*>): Boolean =
    isChronosSenderRequestMethod(function2Type, allowAbstract = false)

private fun Method.isChronosSenderRequestMethod(function2Type: Class<*>?, allowAbstract: Boolean): Boolean =
    isChronosVoidLikeReturn() &&
        !Modifier.isStatic(modifiers) &&
        (allowAbstract || !Modifier.isAbstract(modifiers)) &&
        parameterCount == 5 &&
        Map::class.java.isAssignableFrom(parameterTypes[1]) &&
        Class::class.java.isAssignableFrom(parameterTypes[2]) &&
        (function2Type == null || parameterTypes[3].canAcceptChronosFunction2(function2Type)) &&
        (function2Type == null || parameterTypes[4].canAcceptChronosFunction2(function2Type))

private fun Class<*>.isChronosSenderInterface(function2Type: Class<*>?): Boolean {
    val methods = allMethods().toList()
    return methods.any { it.isChronosSenderRequestMethod(function2Type, allowAbstract = true) } &&
        methods.any { it.isChronosSenderSyncMethod() }
}

private fun Method.isChronosSenderSyncMethod(): Boolean =
    !Modifier.isStatic(modifiers) &&
        parameterCount == 2 &&
        Class::class.java.isAssignableFrom(parameterTypes[0]) &&
        !isChronosVoidLikeReturn()

private fun Class<*>.chronosImplementedInterfaces(): Sequence<Class<*>> = sequence {
    val seen = HashSet<String>()
    val pending = java.util.ArrayDeque<Class<*>>()
    pending.add(this@chronosImplementedInterfaces)
    while (!pending.isEmpty()) {
        val type = pending.removeFirst()
        type.interfaces.forEach { iface ->
            if (seen.add(iface.name)) {
                yield(iface)
                pending.add(iface)
            }
        }
        type.superclass?.let(pending::add)
    }
}

private fun Method.isChronosVoidLikeReturn(): Boolean =
    returnType == Void.TYPE || returnType.name == "kotlin.Unit"

private fun Class<*>.canAcceptChronosFunction2(function2Type: Class<*>): Boolean =
    this == Any::class.java || isAssignableFrom(function2Type)

private data class ChronosSenderScan(
    val type: Class<*>?,
    val evidence: String,
    val missing: String?,
)

private data class ChronosSenderCandidate(
    val type: Class<*>,
    val source: String,
    val messageMethods: Int,
    val commandMethods: Int,
)

private const val HP_CHRONOS_RPC_RECEIVE = "ChronosPromotionHook.ReceiveRpc"

private const val HP_CHRONOS_LOCAL_VIEW_PROGRESS = "ChronosPromotionHook.LocalViewProgress"

private const val HP_CHRONOS_LOCAL_DM_VIEW = "ChronosPromotionHook.LocalDmView"

private const val HP_CHRONOS_MESSAGE_SENDER = "ChronosPromotionHook.MessageSender"

private const val HP_CHRONOS_COMMAND_DM_LIST = "ChronosPromotionHook.CommandDmList"

private const val HP_CHRONOS_REMOTE_VIDEO_DETAIL_STATE = "ChronosPromotionHook.RemoteVideoDetailState"

private const val HP_CHRONOS_REMOTE_VIEW_PROGRESS = "ChronosPromotionHook.RemoteViewProgress"

private const val HP_CHRONOS_REMOTE_COMMAND_DANMAKU = "ChronosPromotionHook.RemoteCommandDanmaku"

private const val HP_CHRONOS_REMOTE_ADD_DANMAKU = "ChronosPromotionHook.RemoteAddDanmaku"

private const val HP_CHRONOS_REMOTE_AD_FLOAT_EXPOSURE = "ChronosPromotionHook.RemoteAdFloatExposure"

private const val HP_CHRONOS_AD_DANMAKU_FEED = "ChronosPromotionHook.AdDanmakuFeed"

private const val HP_CHRONOS_INTERACT_LAYER_VIEW_PROGRESS = "ChronosPromotionHook.InteractLayerViewProgress"

private const val HP_CHRONOS_GEMINI_OPERATION_RENDER = "ChronosPromotionHook.GeminiOperationRender"

private const val HP_CHRONOS_GEMINI_OPERATION_UPDATE = "ChronosPromotionHook.GeminiOperationUpdate"

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

private const val CHRONOS_RPC_HANDLER =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.a"

private const val REMOTE_SERVICE_HANDLER =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.remote.RemoteServiceHandler"

private const val ADD_CUSTOM_DANMAKU_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.AddCustomDanmaku\$Request"

private const val DM_VIEW_CHANGE_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.DmViewChange\$Request"

private const val COMMAND_DANMAKU_SENT_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDanmakuSent\$Request"

private const val COMMAND_DM_LIST_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDmListRequest\$Request"

private const val COMMAND_DM_LIST_RESPONSE =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.CommandDmListRequest\$Response"

private const val AD_DANMAKU_DELEGATE =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.AdDanmakuDelegate"

private const val GEMINI_OPERATION_WIDGET =
    "com.bilibili.app.gemini.player.widget.operation.a"

private val GEMINI_OPERATION_RENDER_METHOD_NAMES = setOf("i", "j", "h", "k")

private const val GEMINI_OPERATION_OBSERVER =
    "com.bilibili.app.gemini.player.widget.operation.a\$d"

private const val VIEW_PROGRESS_DETAIL =
    "tv.danmaku.biliplayerv2.service.interact.biz.model.viewprogress.uniteviewprogress.ViewProgressDetail"

private const val VIEW_PROGRESS_REPLY =
    "com.bapis.bilibili.app.view.v1.ViewProgressReply"

private const val UNITE_VIEW_PROGRESS_REPLY =
    "com.bapis.bilibili.app.viewunite.v1.ViewProgressReply"

private const val DM_VIEW_REPLY =
    "com.bapis.bilibili.community.service.dm.v1.DmViewReply"

private const val GET_DM_VIEW_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetDmView\$Request"

private const val GET_VIEW_PROGRESS_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetViewProgress\$Request"

private const val UPDATE_VIDEO_DETAIL_STATE_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.UpdateVideoDetailState\$Request"

private const val VIDEO_DETAIL_STATE_CHANGE_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.VideoDetailStateChange\$Request"

private const val OPEN_URL_SCHEME_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.OpenUrlScheme\$Request"

private const val AD_DANMAKU_EVENT_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.AdDanmakuEvent\$Request"

private const val NOTIFY_COMMERCIAL_EVENT_REQUEST =
    "tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.NotifyCommercialEvent\$Request"

private const val KOTLIN_FUNCTION2 = "kotlin.jvm.functions.Function2"

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
    "getVoteState",
    "getActivityState",
)
