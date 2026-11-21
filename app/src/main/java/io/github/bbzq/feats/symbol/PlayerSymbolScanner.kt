package io.github.bbzq.feats.symbol

import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.hook.player.QualityRangePolicy
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal fun scanTryFreeQuality(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<TryFreeQualitySymbols> {
    val playViewClassNames = buildSet {
        addAll(PLAYER_MOSS_CANDIDATES)
        addAll(findClassNamesBySimpleName(bridge, "PlayerMoss"))
        addAll(findClassNamesBySimpleName(bridge, "KPlayerMoss"))
        addAll(findClassNamesBySimpleName(bridge, "PlayURLMoss"))
    }
    val playViewMethods = playViewClassNames
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.allMethods().asSequence().filter { method ->
                method.isConcreteInstanceHookMethod() &&
                    method.name in PLAY_VIEW_METHOD_NAMES &&
                    method.parameterCount >= 1 &&
                    method.parameterTypes.firstOrNull()?.isPlayViewRequestType() == true
            }
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val getIsNeedTrial = ArrayList<Method>()
    val setIsNeedTrial = ArrayList<Method>()
    TRY_FREE_NEED_TRIAL_CLASSES.forEach { className ->
        val type = classLoader.loadClassOrNull(className) ?: return@forEach
        type.findMethod("getIsNeedTrial", Boolean::class.javaPrimitiveType!!)?.let(getIsNeedTrial::add)
        type.findMethod("setIsNeedTrial", Void.TYPE, Boolean::class.javaPrimitiveType!!)?.let(setIsNeedTrial::add)
    }
    val getVipFree = ArrayList<Method>()
    val getNeedVip = ArrayList<Method>()
    TRY_FREE_STREAM_INFO_CLASSES.forEach { className ->
        val type = classLoader.loadClassOrNull(className) ?: return@forEach
        type.findMethod("getVipFree", Boolean::class.javaPrimitiveType!!)?.let(getVipFree::add)
        type.findMethod("getNeedVip", Boolean::class.javaPrimitiveType!!)?.let(getNeedVip::add)
    }
    val needTrialCount = getIsNeedTrial.size + setIsNeedTrial.size
    val streamInfoCount = getVipFree.size + getNeedVip.size
    val playViewCount = playViewMethods.size
    val total = needTrialCount + streamInfoCount + playViewCount
    if (total == 0) return SymbolScanResult.Missing("try-free quality hook points not found")
    val symbols = TryFreeQualitySymbols(
        playViewMethods = playViewMethods.map(MethodDescriptor::of),
        getIsNeedTrialMethods = getIsNeedTrial.map(MethodDescriptor::of),
        setIsNeedTrialMethods = setIsNeedTrial.map(MethodDescriptor::of),
        getVipFreeMethods = getVipFree.map(MethodDescriptor::of),
        getNeedVipMethods = getNeedVip.map(MethodDescriptor::of),
        evidence = "needTrial=$needTrialCount,streamInfo=$streamInfoCount,playView=$playViewCount",
    )
    return SymbolScanResult.Found(symbols, "GeneratedMessages/PlayView", symbols.evidence)
}

internal fun scanVideoQuality(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<VideoQualitySymbols> {
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")

    // 1. PlayerPreloadHolder (PreloadData(type=)
    val preloadMethods = runCatching {
        val preloadClass = currentBridge.findClass(
            FindClass.create().matcher(ClassMatcher.create().usingStrings("PreloadData(type=")),
        ).mapNotNull { classLoader.loadClassOrNull(it.name) }.firstOrNull()
        val outerHolderClass = preloadClass?.declaringClass
        outerHolderClass?.declaredMethods?.filter {
            Modifier.isPublic(it.modifiers) && it.parameterCount == 1 && it.returnType == preloadClass
        }?.onEach { it.isAccessible = true }.orEmpty()
    }.getOrElse { emptyList() }

    // 2. PlayerSettingHelper (quality settings:)
    val defaultQnMethod = runCatching {
        classLoader.loadClassOrNull("tz1.h")?.declaredMethods?.singleOrNull {
            Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true } ?: currentBridge.findMethod(
            FindMethod.create().matcher(MethodMatcher.create().usingStrings("quality settings:").paramCount(0)),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .filter { it.returnType == Int::class.javaPrimitiveType || it.returnType == Int::class.javaObjectType }
            .distinctBy(Method::toGenericString)
            .singleOrNull()
            ?.apply { isAccessible = true }
    }.getOrNull()

    // 9.20 起畫質區間更新靜態方法遷至根包（如 aj52.c），不依賴包名並在反射載入前先以參數型別過濾
    val rangeUpdateMethod = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(MethodMatcher.create().modifiers(Modifier.STATIC).paramCount(11).returnType(Void.TYPE)),
        ).filter { data ->
            val params = data.paramTypeNames
            params.getOrNull(0) == "boolean" &&
                params.getOrNull(1) == "kotlinx.coroutines.flow.MutableStateFlow" &&
                params.getOrNull(2) == "kotlinx.coroutines.flow.MutableStateFlow" &&
                params.getOrNull(3) == "kotlin.jvm.internal.Ref\$BooleanRef" &&
                params.getOrNull(4) == "kotlin.jvm.internal.Ref\$ObjectRef"
        }.mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .filter(QualityRangePolicy::matchesUpdate)
            .distinctBy(Method::toGenericString)
            .singleOrNull()
            ?.apply { isAccessible = true }
    }.getOrNull()

    val symbols = VideoQualitySymbols(
        playerPreloadGetMethods = preloadMethods.map(MethodDescriptor::of),
        playerSettingHelperGetDefaultQnMethod = defaultQnMethod?.let(MethodDescriptor::of),
        rangeQualityUpdateMethod = rangeUpdateMethod?.let(MethodDescriptor::of),
        evidence = "preload=${preloadMethods.size},setting=${defaultQnMethod != null},range=${rangeUpdateMethod != null}",
    )

    return if (preloadMethods.isNotEmpty() || defaultQnMethod != null || rangeUpdateMethod != null) {
        SymbolScanResult.Found(symbols, "VideoQuality", symbols.evidence)
    } else {
        SymbolScanResult.Missing("video quality hook points not found")
    }
}

internal fun scanVideoEndRecommend(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<VideoEndRecommendSymbols> {
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
    val shouldShowMethod = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(
                    MethodMatcher.create()
                        .usingStrings(END_PAGE_DECISION_SERVICE_TAG, END_PAGE_SHOULD_SHOW_LOG)
                        .paramCount(0),
                ),
        ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .filter { !Modifier.isStatic(it.modifiers) && it.returnType == Boolean::class.javaPrimitiveType }
            .distinctBy(Method::toGenericString)
            .singleOrNull()
            ?.apply { isAccessible = true }
    }.getOrNull() ?: return SymbolScanResult.Missing("theseus2 end-page decision method not present")

    val symbols = VideoEndRecommendSymbols(
        shouldShowEndPageMethod = MethodDescriptor.of(shouldShowMethod),
        evidence = "${shouldShowMethod.declaringClass.name}.${shouldShowMethod.name}",
    )
    return SymbolScanResult.Found(symbols, symbols.evidence, symbols.evidence)
}

internal fun scanTripleSpeed(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<TripleSpeedSymbols> {
    val currentBridge = bridge() ?: return SymbolScanResult.Missing("DexKitBridge unavailable")
    val methodData = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(MethodMatcher.create().usingStrings(PLAY_SPEED_EXPERIMENT_PREF_KEY)),
        )
    }.getOrElse { throwable ->
        return SymbolScanResult.Missing("play speed experiment search failed: ${throwable.scanMessage()}")
    }
    val qualityResetData = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(MethodMatcher.create().usingStrings(HIGH_FRAME_RATE_SPEED_RESET_LOG)),
        )
    }.getOrElse { throwable ->
        return SymbolScanResult.Missing("high-frame-rate speed reset search failed: ${throwable.scanMessage()}")
    }
    val restoreErrors = ArrayList<String>()
    // The reader lives in a synthetic kotlin Function0 whose zero-arg invoke() maps the
    // "sp_play_speed_experiment" preference int onto LongPressSpeedExperiment. The other match
    // is the A/B writer (invokeSuspend, 1 parameter), which we exclude by shape. Since 9.17
    // playerbizcommonv2 and playerbizcommon3 each ship their own reader, so keep all of them.
    val readers = methodData
        .mapNotNull { data ->
            runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                null
            }
        }
        .filter { it.name == "invoke" && it.parameterCount == 0 && it.returnType == Any::class.java }
        .distinctBy(Method::toGenericString)
        .toList()
    if (readers.isEmpty()) {
        return SymbolScanResult.Missing(
            "play speed experiment reader not found: strings=${methodData.size}" +
                (restoreErrors.take(2).joinToString("; ").takeIf { it.isNotBlank() }?.let { ",restore=$it" } ?: ""),
        )
    }
    val qualityResetCandidates = qualityResetData
        .mapNotNull { data ->
            runCatching { data.getMethodInstance(classLoader) }.getOrElse { throwable ->
                restoreErrors += "${data.descriptor}: ${throwable.scanMessage()}"
                null
            }
        }
        .filter {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                it.returnType == Void.TYPE
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val qualityReset = when {
        qualityResetCandidates.size == 1 -> qualityResetCandidates.single()
        qualityResetCandidates.isEmpty() -> null
        else -> null
    }
    val longPressSpeed = runCatching {
        currentBridge.findMethod(
            FindMethod.create()
                .matcher(MethodMatcher.create().usingStrings(PLAY_SPEED_ARCHIVE_INFO_KEY)),
        )
    }.getOrNull()
        ?.mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
        ?.filter {
            Modifier.isStatic(it.modifiers) &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] != String::class.java &&
                it.returnType == Float::class.javaPrimitiveType
        }
        ?.distinctBy(Method::toGenericString)
        ?.singleOrNull()
        ?.apply { isAccessible = true }
    val highFrameRateSpeedGuard = longPressSpeed?.declaringClass
        ?.declaredMethods
        ?.filter {
            Modifier.isStatic(it.modifiers) &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                it.returnType == Boolean::class.javaPrimitiveType
        }
        ?.singleOrNull()
        ?.apply { isAccessible = true }
    val symbols = TripleSpeedSymbols(
        experimentReaderMethods = readers.map(MethodDescriptor::of),
        qualitySpeedResetMethod = qualityReset?.let(MethodDescriptor::of),
        highFrameRateSpeedGuardMethod = highFrameRateSpeedGuard?.let(MethodDescriptor::of),
        longPressSpeedMethod = longPressSpeed?.let(MethodDescriptor::of),
        evidence = "readers=${readers.joinToString("|") { it.declaringClass.name }},strings=${methodData.size}," +
            "qualityReset=${qualityReset?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}," +
            "qualityStrings=${qualityResetData.size}," +
            "highFrameGuard=${highFrameRateSpeedGuard?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}," +
            "longPressSpeed=${longPressSpeed?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}",
    )
    return SymbolScanResult.Found(symbols, symbols.evidence, symbols.evidence)
}

internal fun scanVideoDetailRelateFeed(
    classLoader: ClassLoader,
): SymbolScanResult<VideoDetailRelateFeedSymbols> {
    val responseClasses = VIDEO_DETAIL_RELATE_RESPONSE_CLASSES.mapNotNull { classLoader.loadClassOrNull(it) }
    val responseGetItems = responseClasses.mapNotNull { responseClass ->
        val getItems = responseClass.allMethods().firstOrNull {
            (it.name == "getCardsList" || it.name == "getRelatesList") &&
                it.parameterCount == 0 &&
                List::class.java.isAssignableFrom(it.returnType) &&
                !Modifier.isStatic(it.modifiers) &&
                !Modifier.isAbstract(it.modifiers)
        } ?: return@mapNotNull null
        val itemsField = responseClass.allFields()
            .filter { List::class.java.isAssignableFrom(it.type) }
            .firstOrNull {
                it.name.startsWith("cards", ignoreCase = true) ||
                    it.name.startsWith("relates", ignoreCase = true) ||
                    it.name.startsWith("items", ignoreCase = true)
            } ?: responseClass.allFields().filter { List::class.java.isAssignableFrom(it.type) }.singleOrNull()
        RelateResponseGetItemsSymbols(MethodDescriptor.of(getItems), itemsField?.let(FieldDescriptor::of))
    }.distinctBy { it.getItems.declaringClassName + "#" + it.getItems.name }

    val detailRelateServiceClass = classLoader.loadClassOrNull(DETAIL_RELATE_SERVICE_CLASS)
    // 9.17 起同类还有多个接收卡片模型的方法(返回 ExtraParams / void),只有返回组件的才是卡片创建入口
    val detailRelateServiceCandidates = detailRelateServiceClass?.allMethods()?.filter { method ->
        !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            method.parameterCount == 1 &&
            (method.name == "d" || method.parameterTypes[0].name.endsWith("D0") || method.returnType.name.contains("RunningUIComponent"))
    }?.toList().orEmpty()
    val detailRelateServiceMethod = detailRelateServiceCandidates.firstOrNull { it.returnType.name.contains("RunningUIComponent") }
        ?: detailRelateServiceCandidates.firstOrNull { it.name == "d" }
        ?: detailRelateServiceCandidates.firstOrNull()

    if (responseGetItems.isEmpty() && detailRelateServiceMethod == null) {
        return SymbolScanResult.Missing("VideoDetailRelate response and service methods not found")
    }

    val symbols = VideoDetailRelateFeedSymbols(
        responseGetItems = responseGetItems,
        detailRelateServiceMethod = detailRelateServiceMethod?.let(MethodDescriptor::of),
        evidence = "responses=${responseGetItems.size},service=${detailRelateServiceMethod != null}",
    )
    val target = buildList {
        addAll(responseGetItems.map { it.getItems.declaringClassName })
        detailRelateServiceMethod?.let { add(it.declaringClass.name) }
    }.joinToString("|")
    return SymbolScanResult.Found(symbols, target, symbols.evidence)
}

private const val PLAY_SPEED_EXPERIMENT_PREF_KEY = "sp_play_speed_experiment"

private const val HIGH_FRAME_RATE_SPEED_RESET_LOG = "reset 3x speed because target quality"

private const val PLAY_SPEED_ARCHIVE_INFO_KEY = "united_player_archive_info"

private const val END_PAGE_DECISION_SERVICE_TAG = "UGCEpisodeEndPageDecisionService"

private const val END_PAGE_SHOULD_SHOW_LOG = "shouldShowEndPage: all conditions met, should show end page"

private val TRY_FREE_NEED_TRIAL_CLASSES = arrayOf(
    "com.bapis.bilibili.pgc.gateway.player.v2.SceneControl",
    "com.bapis.bilibili.playershared.VideoVod",
)

private val TRY_FREE_STREAM_INFO_CLASSES = arrayOf(
    "com.bapis.bilibili.app.playurl.v1.StreamInfo",
    "com.bapis.bilibili.playershared.StreamInfo",
)

private val VIDEO_DETAIL_RELATE_RESPONSE_CLASSES = arrayOf(
    "com.bapis.bilibili.app.viewunite.common.Relates",
    "com.bapis.bilibili.app.viewunite.v1.Relates",
    "com.bapis.bilibili.app.viewunite.v1.RelatesFeedReply",
    "com.bapis.bilibili.app.view.v1.RelatesFeedReply",
    "com.bapis.bilibili.app.view.v1.ViewReply",
    "com.bapis.bilibili.app.view.v1.PlayerRelatesReply",
)

private const val DETAIL_RELATE_SERVICE_CLASS =
    "com.bilibili.ship.theseus.united.page.intro.module.relate.DetailRelateService"
