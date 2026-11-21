package io.github.bbzq.feats.symbol

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import io.github.bbzq.feats.allMethods
import io.github.bbzq.feats.symbol.dexkit.scanMessage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal fun scanSplashAd(classLoader: ClassLoader): SymbolScanResult<SplashAdSymbols> {
    val gsonParsers = classLoader.loadClassOrNull(SPLASH_GSON_CLASS)
        ?.allMethods()
        ?.filter { it.name == "fromJson" && !Modifier.isAbstract(it.modifiers) && it.returnType != Void.TYPE }
        ?.toList()
        .orEmpty()
    val fastJsonParsers = SPLASH_FAST_JSON_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?.declaredMethods
        ?.filter {
            Modifier.isStatic(it.modifiers) &&
                it.name in SPLASH_FAST_JSON_PARSE_METHODS &&
                it.returnType != Void.TYPE
        }
        .orEmpty()
    val kotlinxParsers = SPLASH_KOTLINX_JSON_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
        ?.allMethods()
        ?.filter { it.name == "decodeFromString" && it.parameterCount >= 2 && it.returnType != Void.TYPE }
        ?.toList()
        .orEmpty()
    val total = gsonParsers.size + fastJsonParsers.size + kotlinxParsers.size
    if (total == 0) return SymbolScanResult.Missing("json parser hook methods not found")
    val symbols = SplashAdSymbols(
        parserMethods = (gsonParsers + fastJsonParsers + kotlinxParsers)
            .distinctBy(Method::toGenericString)
            .map(MethodDescriptor::of),
        evidence = "gson=${gsonParsers.size},fastjson=${fastJsonParsers.size},kotlinx=${kotlinxParsers.size}",
    )
    return SymbolScanResult.Found(symbols, "JsonParsers", symbols.evidence)
}

internal fun scanRewardAd(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<RewardAdSymbols> {
    val activityClass = REWARD_ACTIVITY_CLASSES.firstNotNullOfOrNull { classLoader.loadClassOrNull(it) }
    val activityOnCreate = activityClass?.findMethod("onCreate", Void.TYPE, Bundle::class.java)
    val activityOnResume = activityClass?.findMethod("onResume", Void.TYPE)
    val activityOnStop = activityClass?.findMethod("onStop", Void.TYPE)?.takeUnless {
        it.declaringClass == Activity::class.java ||
            it.declaringClass.name.startsWith("android.") ||
            it.declaringClass.name.startsWith("androidx.")
    }
    val activityCount = listOfNotNull(activityOnCreate, activityOnResume, activityOnStop).size

    val headerClass = classLoader.loadClassOrNull(REWARD_HEADER_VIEW)
    val headerSetTotalTime = headerClass?.findRewardTimerSetter("setTotalTime", Int::class.javaPrimitiveType!!)
    val headerSetElapsedTime = headerClass?.findRewardTimerSetter("setElapsedTime", Long::class.javaPrimitiveType!!)
    val headerStartTimer = headerClass?.findMethod("startTimer", Void.TYPE)
    val headerCount = listOfNotNull(headerSetTotalTime, headerSetElapsedTime, headerStartTimer).size

    val countDownClass = classLoader.loadClassOrNull(REWARD_COUNT_DOWN_TEXT_VIEW)
    val countDownSetTotalTime = countDownClass?.findRewardTimerSetter("setTotalTime", Int::class.javaPrimitiveType!!)
    val countDownSetElapsedTime = countDownClass?.findRewardTimerSetter("setElapsedTime", Long::class.javaPrimitiveType!!)
    val countDownCount = listOfNotNull(countDownSetTotalTime, countDownSetElapsedTime).size

    val miniGameRewardClass = classLoader.loadClassOrNull(REWARD_MINI_GAME_ABILITY)
    val miniGameRewardShow = miniGameRewardClass?.declaredMethods?.singleOrNull { method ->
        !Modifier.isStatic(method.modifiers) &&
            method.parameterCount == 5 &&
            method.parameterTypes[1] == String::class.java &&
            method.parameterTypes[2] == String::class.java &&
            method.parameterTypes[3] == String::class.java &&
            method.returnType != Void.TYPE
    }?.apply { isAccessible = true }
    val miniGameRewardEmitEvent = miniGameRewardClass?.declaredMethods?.singleOrNull { method ->
        !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java, JSONObject::class.java),
            )
    }?.apply { isAccessible = true }
    val miniGameRewardCallback = miniGameRewardShow?.parameterTypes?.getOrNull(4)
        ?.declaredMethods
        ?.singleOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java, JSONObject::class.java))
        }
        ?.apply { isAccessible = true }
    val miniGameRewardReady =
        miniGameRewardShow != null && miniGameRewardEmitEvent != null && miniGameRewardCallback != null
    val miniGameRewardCount = listOfNotNull(
        miniGameRewardShow,
        miniGameRewardEmitEvent,
        miniGameRewardCallback,
    ).size

    val jumpClockField = REWARD_JUMP_CLOCK_CLASSES.firstNotNullOfOrNull { className ->
        classLoader.loadClassOrNull(className)
            ?.declaredFields
            ?.singleOrNull { field ->
                Modifier.isStatic(field.modifiers) &&
                    (field.type == Long::class.javaObjectType || field.type == Long::class.javaPrimitiveType)
            }
            ?.apply { isAccessible = true }
    }
    // Reward display moved from the activity into RewardAdDisplay: the countdown lives in a
    // small timer model and the jump-return clock is an instance field on the display.
    val displayClass = classLoader.loadClassOrNull(REWARD_DISPLAY)
    val jumpStartField = displayClass?.declaredFields
        ?.singleOrNull { !Modifier.isStatic(it.modifiers) && it.type == Long::class.javaObjectType }
        ?.apply { isAccessible = true }
    val jumpResumeMethod = if (displayClass != null && jumpStartField != null) {
        findDeclaredMethods(bridge, classLoader) {
            searchPackages(REWARD_PACKAGE).matcher(
                MethodMatcher.create()
                    .declaredClass(displayClass)
                    .paramCount(0)
                    .returnType(Void.TYPE)
                    .usingStrings(REWARD_JUMP_REASON),
            )
        }.singleOrNull()?.apply { isAccessible = true }
    } else {
        null
    }
    // 9.20 起倒數計時器類遷至根包（如 rb92），優先由 RewardAdDisplay 欄位類型定位，不依賴包名
    val timerSetTotalMethod = displayClass?.declaredFields
        ?.asSequence()
        ?.map { it.type }
        ?.distinct()
        ?.mapNotNull { it.findRewardTimerSetTotal() }
        ?.distinctBy(Method::toGenericString)
        ?.singleOrNull()
        ?: findDeclaredMethods(bridge, classLoader) {
            searchPackages(REWARD_PACKAGE).matcher(
                MethodMatcher.create().name("start").paramCount(0).returnType(Void.TYPE),
            )
        }.mapNotNull { it.declaringClass.findRewardTimerSetTotal() }.distinctBy(Method::toGenericString).singleOrNull()

    val hookPoints = listOf(
        childHookPoint(HP_REWARD_AD_ACTIVITY, activityCount > 0, "reward activity hooks not found", "methods=$activityCount"),
        childHookPoint(HP_REWARD_AD_HEADER, headerCount > 0, "reward header timer hooks not found", "methods=$headerCount"),
        childHookPoint(HP_REWARD_AD_COUNTDOWN, countDownCount > 0, "countdown text hooks not found", "methods=$countDownCount"),
        optionalChildHookPoint(
            HP_REWARD_AD_JUMP_CLOCK,
            jumpClockField != null || jumpResumeMethod != null,
            "jump clock field not found",
            "static=${jumpClockField?.name},resume=${jumpResumeMethod?.name},start=${jumpStartField?.name}",
        ),
        optionalChildHookPoint(
            HP_REWARD_AD_TIMER,
            timerSetTotalMethod != null,
            "reward countdown timer not found",
            "method=${timerSetTotalMethod?.declaringClass?.name}.${timerSetTotalMethod?.name}",
        ),
        HookPointStatus.found(HP_REWARD_AD_ACTIVITY_SWEEPER, "android.app.Activity.onResume", "sdk=true"),
        optionalChildHookPoint(
            HP_REWARD_AD_MINI_GAME,
            miniGameRewardReady,
            "mini game rewarded video hooks not found",
            "methods=$miniGameRewardCount",
        ),
    )
    val total = activityCount + headerCount + countDownCount + (if (miniGameRewardReady) 1 else 0) +
        (if (timerSetTotalMethod != null) 1 else 0) + (if (jumpResumeMethod != null) 1 else 0) + 1
    if (total == 1) return SymbolScanResult.Missing("reward ad target hook points not found")
    val symbols = RewardAdSymbols(
        activityOnCreate = activityOnCreate?.let(MethodDescriptor::of),
        activityOnResume = activityOnResume?.let(MethodDescriptor::of),
        activityOnStop = activityOnStop?.let(MethodDescriptor::of),
        headerSetTotalTime = headerSetTotalTime?.let(MethodDescriptor::of),
        headerSetElapsedTime = headerSetElapsedTime?.let(MethodDescriptor::of),
        headerStartTimer = headerStartTimer?.let(MethodDescriptor::of),
        countDownSetTotalTime = countDownSetTotalTime?.let(MethodDescriptor::of),
        countDownSetElapsedTime = countDownSetElapsedTime?.let(MethodDescriptor::of),
        jumpClockField = jumpClockField?.let(FieldDescriptor::of),
        timerSetTotalMethod = timerSetTotalMethod?.let(MethodDescriptor::of),
        jumpResumeMethod = jumpResumeMethod?.let(MethodDescriptor::of),
        jumpStartField = if (jumpResumeMethod != null) jumpStartField?.let(FieldDescriptor::of) else null,
        miniGameRewardShow = if (miniGameRewardReady) MethodDescriptor.of(miniGameRewardShow) else null,
        miniGameRewardEmitEvent = if (miniGameRewardReady) {
            MethodDescriptor.of(miniGameRewardEmitEvent)
        } else {
            null
        },
        miniGameRewardCallback = if (miniGameRewardReady) {
            MethodDescriptor.of(miniGameRewardCallback)
        } else {
            null
        },
        evidence = "activity=$activityCount,header=$headerCount,countDown=$countDownCount," +
            "jumpClock=${jumpClockField != null},jumpResume=${jumpResumeMethod != null}," +
            "timer=${timerSetTotalMethod != null},miniGame=$miniGameRewardReady",
    )
    return SymbolScanResult.Found(symbols, "RewardAd", symbols.evidence, hookPoints)
}

/**
 * Kotlin may append a module name to public Java method names after a module split.
 * Keep accepting the stable name while requiring the original timer signature.
 */
private fun Class<*>.findRewardTimerSetter(name: String, parameterType: Class<*>): Method? =
    allMethods().firstOrNull { method ->
        method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(parameterType)) &&
            (method.name == name || method.name.startsWith("$name\$"))
    }?.apply { isAccessible = true }

/** Countdown model: start()/stop()/d()/e() plus b(int) that receives the total duration in ms. */
private fun Class<*>.findRewardTimerSetTotal(): Method? {
    fun noArgVoid(name: String) = declaredMethods.any {
        it.name == name && it.parameterCount == 0 && it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers)
    }
    if (!listOf("start", "stop", "d", "e").all(::noArgVoid)) return null
    val longFields = declaredFields.count { !Modifier.isStatic(it.modifiers) && it.type == Long::class.javaPrimitiveType }
    if (longFields != 1) return null
    return declaredMethods.singleOrNull {
        it.name == "b" &&
            it.returnType == Void.TYPE &&
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
            !Modifier.isStatic(it.modifiers)
    }?.apply { isAccessible = true }
}

private fun skipVideoAdControllerHookPoint(
    id: String,
    scan: ControllerClassScan,
    ready: Boolean,
    missing: String,
    evidence: String,
): HookPointStatus {
    if (ready) {
        val scanEvidence = scan.error?.let { "$evidence,scanError=${it.take(180)}" } ?: evidence
        return HookPointStatus.found(id, id.substringAfterLast('.'), scanEvidence)
    }
    return scan.error?.let { HookPointStatus.error(id, "fail closed: ${it.take(240)}") }
        ?: HookPointStatus.optional(id, missing)
}

private fun storySkipVideoAdHookPoint(
    ready: Boolean,
    classFound: Boolean,
    scanError: String?,
    evidence: String,
): HookPointStatus {
    if (ready) return HookPointStatus.found(HP_SKIP_VIDEO_AD_STORY, "StoryPlayer", evidence)
    scanError?.let { return HookPointStatus.error(HP_SKIP_VIDEO_AD_STORY, "fail closed: ${it.take(240)}") }
    val missing = if (classFound) {
        "story player position/seek methods not found"
    } else {
        "story pager player class not found"
    }
    return HookPointStatus.optional(HP_SKIP_VIDEO_AD_STORY, missing)
}

internal fun scanVideoDetailBannerAd(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<VideoDetailBannerAdSymbols> {
    val bizKt = classLoader.loadClassOrNull(G_AD_BIZ_KT)
    val videoDetailType = classLoader.loadClassOrNull(G_AD_VIDEO_DETAIL)
    val underPlayerType = classLoader.loadClassOrNull(I_AD_UNDER_PLAYER)
    val relateType = classLoader.loadClassOrNull(I_AD_VIDEO_RELATE)
    val merchandiseType = classLoader.loadClassOrNull(I_AD_MERCHANDISE)
    val pausedPageType = classLoader.loadClassOrNull(I_VD_PAUSED_PAGE)
    val adPanelType = classLoader.loadClassOrNull(I_AD_PANEL)
    val getVideoDetail = if (bizKt != null && videoDetailType != null) {
        bizKt.allMethods().firstOrNull {
            it.name == "getGAdVideoDetail" &&
                it.parameterCount == 0 &&
                Modifier.isStatic(it.modifiers) &&
                videoDetailType.isAssignableFrom(it.returnType)
        }
    } else {
        null
    }
    val requestPausedPage = pausedPageType?.allMethods()?.firstOrNull { it.isPausedPageRequestMethod() }
    val getPausedPagePanel = adPanelType?.allMethods()?.firstOrNull {
        it.name == "getPausedPagePanel" &&
            it.parameterCount == 1
    }
    val getBrandPausedPagePanel = adPanelType?.allMethods()?.firstOrNull {
        it.name == "getBrandPausedPagePanel" &&
            it.parameterCount == 2
    }

    val baseComponent = findGeminiBindingComponentClass(classLoader)
    val viewBindingClass = classLoader.loadClassOrNull(ANDROIDX_VIEW_BINDING)
    val relateGameComponent = if (baseComponent != null && viewBindingClass != null) {
        findRelateGameComponentClass(classLoader, bridge, baseComponent, viewBindingClass)
    } else {
        RelateGameComponentScan(
            type = null,
            evidence = "base=${baseComponent != null},viewBinding=${viewBindingClass != null}",
        )
    }
    val simpleViewEntry = classLoader.loadClassOrNull(GEMINI_SIMPLE_VIEW_ENTRY)
    val simpleViewEntryConstructor = simpleViewEntry?.declaredConstructors?.firstOrNull {
        it.parameterTypes.contentEquals(arrayOf(View::class.java))
    }?.apply { isAccessible = true }
    val createViewEntry = baseComponent?.allMethods()?.firstOrNull {
        it.name == "createViewEntry" &&
            it.parameterCount == 2 &&
            it.parameterTypes[0] == Context::class.java &&
            it.parameterTypes[1] == ViewGroup::class.java
    }
    val bindToView = baseComponent?.allMethods()?.firstOrNull {
        it.name == "bindToView" &&
            it.parameterCount == 2 &&
            it.parameterTypes[1].name == "kotlin.coroutines.Continuation"
    }
    val unitField = runCatching {
        classLoader.loadClassOrNull(KOTLIN_UNIT)
            ?.getDeclaredField("INSTANCE")
            ?.apply { isAccessible = true }
    }.getOrNull()

    val hasProxyHook = getVideoDetail != null && videoDetailType != null && underPlayerType != null
    val hasPausedPageRequestHook = getVideoDetail != null &&
        videoDetailType != null &&
        pausedPageType != null &&
        requestPausedPage != null
    val hasPausedPagePanelHook = getVideoDetail != null &&
        videoDetailType != null &&
        adPanelType != null &&
        getPausedPagePanel != null &&
        getBrandPausedPagePanel != null
    val hasRelateGameHook = simpleViewEntryConstructor != null &&
        createViewEntry != null &&
        bindToView != null &&
        unitField != null &&
        relateGameComponent.type != null
    if (!hasProxyHook && !hasPausedPageRequestHook && !hasPausedPagePanelHook && !hasRelateGameHook) {
        return SymbolScanResult.Missing("video detail banner hook points not found")
    }

    val symbols = VideoDetailBannerAdSymbols(
        getVideoDetail = getVideoDetail?.let(MethodDescriptor::of),
        videoDetailTypeName = if (hasProxyHook || hasPausedPageRequestHook || hasPausedPagePanelHook) {
            videoDetailType.name
        } else {
            null
        },
        underPlayerTypeName = if (hasProxyHook) underPlayerType.name else null,
        relateTypeName = relateType?.name,
        merchandiseTypeName = merchandiseType?.name,
        pausedPageTypeName = if (hasPausedPageRequestHook) pausedPageType.name else null,
        adPanelTypeName = if (hasPausedPagePanelHook) adPanelType.name else null,
        requestPausedPage = if (hasPausedPageRequestHook) MethodDescriptor.of(requestPausedPage) else null,
        getPausedPagePanel = if (hasPausedPagePanelHook) MethodDescriptor.of(getPausedPagePanel) else null,
        getBrandPausedPagePanel = if (hasPausedPagePanelHook) {
            MethodDescriptor.of(getBrandPausedPagePanel)
        } else {
            null
        },
        relateGameComponentTypeName = if (hasRelateGameHook) relateGameComponent.type.name else null,
        simpleViewEntryConstructor = if (hasRelateGameHook) {
            ConstructorDescriptor.of(simpleViewEntryConstructor)
        } else {
            null
        },
        createViewEntry = if (hasRelateGameHook) MethodDescriptor.of(createViewEntry) else null,
        bindToView = if (hasRelateGameHook) MethodDescriptor.of(bindToView) else null,
        kotlinUnitField = if (hasRelateGameHook) FieldDescriptor.of(unitField) else null,
        evidence = "proxy=$hasProxyHook,pausedRequest=$hasPausedPageRequestHook," +
            "pausedPanel=$hasPausedPagePanelHook,relateGame=$hasRelateGameHook," +
            "component=${relateGameComponent.type?.name ?: "-"}",
    )
    val hookPoints = listOf(
        childHookPoint(
            HP_VIDEO_DETAIL_BANNER_PROXY,
            hasProxyHook,
            "proxy hook dependencies not found",
            "method=${getVideoDetail != null},detail=${videoDetailType != null},underPlayer=${underPlayerType != null}",
        ),
        childHookPoint(
            HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_REQUEST,
            hasPausedPageRequestHook,
            "paused page request hook dependencies not found",
            "method=${getVideoDetail != null},detail=${videoDetailType != null},pausedPage=${pausedPageType != null},request=${requestPausedPage != null}",
        ),
        childHookPoint(
            HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_PANEL,
            hasPausedPagePanelHook,
            "paused page panel hook dependencies not found",
            "method=${getVideoDetail != null},detail=${videoDetailType != null},panel=${adPanelType != null},normal=${getPausedPagePanel != null},brand=${getBrandPausedPagePanel != null}",
        ),
        childHookPoint(
            HP_VIDEO_DETAIL_BANNER_RELATE_GAME,
            hasRelateGameHook,
            "relate game view hook dependencies not found",
            "component=${relateGameComponent.type?.name ?: "-"},ctor=${simpleViewEntryConstructor != null},create=${createViewEntry != null},bind=${bindToView != null},unit=${unitField != null},scan=${relateGameComponent.evidence}",
        ),
    )
    return SymbolScanResult.Found(symbols, "VideoDetailBannerAd", symbols.evidence, hookPoints)
}

private fun Method.isPausedPageRequestMethod(): Boolean =
    name == "requestPausedPage" &&
        parameterCount > 0 &&
        parameterTypes.last().isKotlinContinuationTypeName() &&
        returnType == Any::class.java

private fun findGeminiBindingComponentClass(classLoader: ClassLoader): Class<*>? {
    for (className in GEMINI_BINDING_COMPONENT_CLASSES) {
        val clazz = classLoader.loadClassOrNull(className) ?: continue
        if (Modifier.isAbstract(clazz.modifiers) &&
            clazz.allMethods().any { it.name == "createViewEntry" && it.parameterCount == 2 } &&
            clazz.allMethods().any { it.name == "bindToView" && it.parameterCount == 2 }
        ) {
            return clazz
        }
    }
    return GEMINI_BINDING_COMPONENT_CLASSES.firstNotNullOfOrNull(classLoader::loadClassOrNull)
}

private fun findRelateGameComponentClass(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
    baseComponent: Class<*>,
    viewBindingClass: Class<*>,
): RelateGameComponentScan {
    val searched = findClassNamesByNameContains(bridge, listOf(RELATE_GAME_COMPONENT_PACKAGE))
        .filter { it.startsWith("$RELATE_GAME_COMPONENT_PACKAGE.") }
    val names = (searched + RELATE_GAME_COMPONENT_CANDIDATE_NAMES).distinct()
    if (names.isEmpty()) {
        return RelateGameComponentScan(null, "candidates=0")
    }

    val restoreErrors = ArrayList<String>()
    val candidates = names.mapNotNull { name ->
        runCatching { Class.forName(name, false, classLoader) }
            .onFailure { restoreErrors += "$name:${it.scanMessage()}" }
            .getOrNull()
    }.filter { type ->
        type.isRelateGameComponentCandidate(baseComponent, viewBindingClass)
    }.distinctBy { it.name }

    val errorSuffix = restoreErrors.takeIf { it.isNotEmpty() }
        ?.joinToString(prefix = ",restoreErrors=", limit = 2) { it.take(120) }
        .orEmpty()
    return when (candidates.size) {
        1 -> RelateGameComponentScan(
            type = candidates.single(),
            evidence = "candidates=${names.size},matched=1$errorSuffix",
        )
        0 -> RelateGameComponentScan(
            type = null,
            evidence = "candidates=${names.size},matched=0$errorSuffix",
        )
        else -> RelateGameComponentScan(
            type = null,
            evidence = "candidates=${names.size},matched=${candidates.size},ambiguous=${candidates.joinToString(limit = 4) { it.name }}$errorSuffix",
        )
    }
}

private fun Class<*>.isRelateGameComponentCandidate(
    baseComponent: Class<*>,
    viewBindingClass: Class<*>,
): Boolean {
    if (!baseComponent.isAssignableFrom(this)) return false
    if (Modifier.isAbstract(modifiers) || isInterface) return false
    val hasCreateBinding = declaredMethods.any { method ->
        method.parameterCount == 3 &&
            method.parameterTypes[0] == Context::class.java &&
            method.parameterTypes[1] == LayoutInflater::class.java &&
            method.parameterTypes[2] == ViewGroup::class.java &&
            viewBindingClass.isAssignableFrom(method.returnType)
    }
    if (!hasCreateBinding) return false
    return declaredMethods.any { method ->
        method.parameterCount == 2 &&
            viewBindingClass.isAssignableFrom(method.parameterTypes[0]) &&
            method.parameterTypes[1].isKotlinContinuationTypeName()
    }
}

internal fun scanSkipVideoAd(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge?,
): SymbolScanResult<SkipVideoAdSymbols> {
    val playViewMethods = PLAYER_MOSS_CANDIDATES
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
    val playerCoreScan = findConcreteImplementors(
        classLoader = classLoader,
        bridge = bridge,
        interfaceName = PLAYER_CORE_SERVICE_INTERFACE,
    )
    val cardScan = findConcreteImplementors(
        classLoader = classLoader,
        bridge = bridge,
        interfaceName = CARD_PLAYER_CONTEXT_INTERFACE,
    )
    val playerCoreClasses = playerCoreScan.classes
    val cardClasses = cardScan.classes
    val playerCoreCurrent = playerCoreClasses.flatMap { it.currentPositionMethods() }.distinctBy(Method::toGenericString)
    val playerCoreState = playerCoreClasses.flatMap { it.stateMethods(STATE_METHOD_NAMES) }.distinctBy(Method::toGenericString)
    val playerCoreSeek = playerCoreClasses.flatMap { it.seekMethods() }.distinctBy(Method::toGenericString)
    val cardCurrent = cardClasses.flatMap { it.currentPositionMethods() }.distinctBy(Method::toGenericString)
    val cardState = cardClasses.flatMap { it.stateMethods(CARD_STATE_METHOD_NAMES) }.distinctBy(Method::toGenericString)
    val cardSeek = cardClasses.flatMap { it.seekMethods() }.distinctBy(Method::toGenericString)
    var storyScanError: String? = null
    val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
    val storyMethods = storyPagerPlayer?.let { type ->
        runCatching {
            Triple(
                type.currentPositionMethods().distinctBy(Method::toGenericString),
                type.stateMethods(STATE_METHOD_NAMES).distinctBy(Method::toGenericString),
                type.seekMethods().distinctBy(Method::toGenericString),
            )
        }.onFailure { throwable ->
            storyScanError = throwable.scanMessage()
        }.getOrNull()
    } ?: Triple(emptyList(), emptyList(), emptyList())
    val storyCurrent = storyMethods.first
    val storyState = storyMethods.second
    val storySeek = storyMethods.third
    val playerCoreReady = playerCoreCurrent.isNotEmpty() && playerCoreState.isNotEmpty() && playerCoreSeek.isNotEmpty()
    val cardReady = cardCurrent.isNotEmpty() && cardState.isNotEmpty() && cardSeek.isNotEmpty()
    val storyReady = storyCurrent.isNotEmpty() && storySeek.isNotEmpty()
    val controllerReady = playerCoreReady || cardReady || storyReady
    val hookPoints = listOf(
        childHookPoint(HP_SKIP_VIDEO_AD_PLAY_VIEW, playViewMethods.isNotEmpty(), "play view hook methods not found", "methods=${playViewMethods.size}"),
        skipVideoAdControllerHookPoint(
            id = HP_SKIP_VIDEO_AD_PLAYER_CORE,
            scan = playerCoreScan,
            ready = playerCoreReady,
            missing = "player core position/state/seek methods not found",
            evidence = "classes=${playerCoreClasses.size},current=${playerCoreCurrent.size},state=${playerCoreState.size},seek=${playerCoreSeek.size}",
        ),
        skipVideoAdControllerHookPoint(
            id = HP_SKIP_VIDEO_AD_CARD,
            scan = cardScan,
            ready = cardReady,
            missing = "card player position/state/seek methods not found",
            evidence = "classes=${cardClasses.size},current=${cardCurrent.size},state=${cardState.size},seek=${cardSeek.size}",
        ),
        storySkipVideoAdHookPoint(
            ready = storyReady,
            classFound = storyPagerPlayer != null,
            scanError = storyScanError,
            evidence = "class=${storyPagerPlayer != null},current=${storyCurrent.size},state=${storyState.size},seek=${storySeek.size}",
        ),
    )
    val missingReason = when {
        playViewMethods.isEmpty() -> "skip video ad play view hook points not found"
        !controllerReady -> "skip video ad controller hook points not found"
        else -> null
    }
    if (missingReason != null) {
        return SymbolScanResult.Missing(missingReason, hookPoints)
    }
    val symbols = SkipVideoAdSymbols(
        playViewMethods = playViewMethods.map(MethodDescriptor::of),
        playerCoreCurrentPositionMethods = playerCoreCurrent.map(MethodDescriptor::of),
        playerCoreStateMethods = playerCoreState.map(MethodDescriptor::of),
        playerCoreSeekMethods = playerCoreSeek.map(MethodDescriptor::of),
        cardCurrentPositionMethods = cardCurrent.map(MethodDescriptor::of),
        cardStateMethods = cardState.map(MethodDescriptor::of),
        cardSeekMethods = cardSeek.map(MethodDescriptor::of),
        storyCurrentPositionMethods = storyCurrent.map(MethodDescriptor::of),
        storyStateMethods = storyState.map(MethodDescriptor::of),
        storySeekMethods = storySeek.map(MethodDescriptor::of),
        evidence = "play=${playViewMethods.size},core=${playerCoreCurrent.size}/${playerCoreState.size}/${playerCoreSeek.size},card=${cardCurrent.size}/${cardState.size}/${cardSeek.size},story=${storyCurrent.size}/${storyState.size}/${storySeek.size}",
    )
    return SymbolScanResult.Found(symbols, "SkipVideoAd", symbols.evidence, hookPoints)
}

internal fun scanSkipVideoAdProgress(
    classLoader: ClassLoader,
): SymbolScanResult<SkipVideoAdProgressSymbols> {
    val progressOnDraw = classLoader.loadClassOrNull(PROGRESS_BAR_CLASS)
        ?.allMethods()
        ?.firstOrNull { it.name == "onDraw" && it.parameterTypes.contentEquals(arrayOf(Canvas::class.java)) }
    val storyOnStart = classLoader.loadClassOrNull(STORY_SEEK_BAR_CLASS)
        ?.allMethods()
        ?.filter {
            it.name == "onStart" &&
                it.parameterCount == 1 &&
                it.parameterTypes.firstOrNull()?.isNumericType() == true
        }
        ?.distinctBy(Method::toGenericString)
        ?.toList()
        .orEmpty()
    val inlineUpdate = INLINE_PROGRESS_CLASSES
        .asSequence()
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .flatMap { type ->
            type.allMethods().asSequence().filter { it.name == "updateProgress" && it.parameterCount == 0 }
        }
        .distinctBy(Method::toGenericString)
        .toList()
    val panelWidget = classLoader.loadClassOrNull(PANEL_WIDGET_KT_CLASS)
    if (progressOnDraw == null && storyOnStart.isEmpty() && inlineUpdate.isEmpty()) {
        return SymbolScanResult.Missing("skip video ad progress hook points not found")
    }
    val symbols = SkipVideoAdProgressSymbols(
        progressOnDraw = progressOnDraw?.let(MethodDescriptor::of),
        storyOnStartMethods = storyOnStart.map(MethodDescriptor::of),
        inlineUpdateMethods = inlineUpdate.map(MethodDescriptor::of),
        panelWidgetKtClassName = panelWidget?.name,
        evidence = "draw=${progressOnDraw != null},story=${storyOnStart.size},inline=${inlineUpdate.size},panel=${panelWidget != null}",
    )
    val hookPoints = listOf(
        childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_DRAW, progressOnDraw != null, "progress onDraw hook not found"),
        childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_STORY, storyOnStart.isNotEmpty(), "story seek bar onStart hook not found", "methods=${storyOnStart.size}"),
        childHookPoint(HP_SKIP_VIDEO_AD_PROGRESS_INLINE, inlineUpdate.isNotEmpty(), "inline progress update hook not found", "methods=${inlineUpdate.size}"),
    )
    return SymbolScanResult.Found(symbols, "SkipVideoAdProgress", symbols.evidence, hookPoints)
}

internal fun scanSkipVideoAdAutoLike(
    classLoader: ClassLoader,
): SymbolScanResult<SkipVideoAdAutoLikeSymbols> {
    val detailLikeInflate = classLoader.loadClassOrNull(DETAIL_LIKE_COMPONENT)
        ?.allMethods()
        ?.firstOrNull { it.isDetailLikeInflateMethod() }
    val detailLikeStateOwner = classLoader.loadClassOrNull(DETAIL_LIKE_STATE_OWNER)
    val storyActionOwner = classLoader.loadClassOrNull(STORY_ACTION_OWNER)
    val storyWidgetClasses = listOf(STORY_LIKE_WIDGET, STORY_LANDSCAPE_LIKE_WIDGET)
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .distinctBy { it.name }
    val storyBindMethods = storyWidgetClasses
        .flatMap { type -> type.allMethods().filter { it.isStoryBindMethod() } }
        .distinctBy(Method::toGenericString)
    val geminiLikeWidget = classLoader.loadClassOrNull(GEMINI_PLAYER_LIKE_WIDGET)
    if (detailLikeInflate == null && storyWidgetClasses.isEmpty() && geminiLikeWidget == null) {
        return SymbolScanResult.Missing("skip video ad auto-like hook points not found")
    }
    val symbols = SkipVideoAdAutoLikeSymbols(
        detailLikeInflateMethod = detailLikeInflate?.let(MethodDescriptor::of),
        detailLikeStateOwnerClassName = detailLikeStateOwner?.name,
        storyWidgetClassNames = storyWidgetClasses.map { it.name },
        storyActionOwnerClassName = storyActionOwner?.name,
        storyBindMethods = storyBindMethods.map(MethodDescriptor::of),
        geminiLikeWidgetClassName = geminiLikeWidget?.name,
        evidence = "detail=${detailLikeInflate != null},detailOwner=${detailLikeStateOwner != null},storyClasses=${storyWidgetClasses.size},storyOwner=${storyActionOwner != null},storyBind=${storyBindMethods.size},gemini=${geminiLikeWidget != null}",
    )
    val hookPoints = listOf(
        childHookPoint(
            HP_SKIP_VIDEO_AD_AUTO_LIKE_DETAIL,
            detailLikeInflate != null && detailLikeStateOwner != null,
            "detail like inflate/state owner not found",
            "inflate=${detailLikeInflate != null},owner=${detailLikeStateOwner != null}",
        ),
        childHookPoint(
            HP_SKIP_VIDEO_AD_AUTO_LIKE_STORY,
            storyWidgetClasses.isNotEmpty() && storyBindMethods.isNotEmpty(),
            "story like widget/bind hook not found",
            "widgets=${storyWidgetClasses.size},owner=${storyActionOwner != null},bind=${storyBindMethods.size}",
        ),
        childHookPoint(HP_SKIP_VIDEO_AD_AUTO_LIKE_GEMINI, geminiLikeWidget != null, "gemini like widget class not found"),
    )
    return SymbolScanResult.Found(symbols, "SkipVideoAdAutoLike", symbols.evidence, hookPoints)
}

private fun Method.isDetailLikeInflateMethod(): Boolean {
    if (name != "b" || parameterCount != 3) return false
    val params = parameterTypes
    return Context::class.java.isAssignableFrom(params[0]) &&
        LayoutInflater::class.java.isAssignableFrom(params[1]) &&
        ViewGroup::class.java.isAssignableFrom(params[2])
}

private fun Method.isStoryBindMethod(): Boolean =
    parameterCount == 1 &&
        returnType == Void.TYPE &&
        parameterTypes[0].isStoryActionOwnerType()

private fun Class<*>.isStoryActionOwnerType(): Boolean {
    if (name == STORY_ACTION_OWNER) return true
    val methods = allMethods().toList()
    return methods.any {
        it.name == "getData" && it.parameterCount == 0
    } && methods.any {
        it.name == "isActive" &&
            it.parameterCount == 0 &&
            (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
    }
}

private data class RelateGameComponentScan(
    val type: Class<*>?,
    val evidence: String,
)

private const val HP_REWARD_AD_ACTIVITY = "RewardAdHook.Activity"

private const val HP_REWARD_AD_HEADER = "RewardAdHook.HeaderTimer"

private const val HP_REWARD_AD_COUNTDOWN = "RewardAdHook.CountDownText"

private const val HP_REWARD_AD_JUMP_CLOCK = "RewardAdHook.JumpClock"

private const val HP_REWARD_AD_TIMER = "RewardAdHook.CountdownTimer"

private const val HP_REWARD_AD_ACTIVITY_SWEEPER = "RewardAdHook.ActivitySweeper"

private const val HP_REWARD_AD_MINI_GAME = "RewardAdHook.MiniGameRewardedVideoAd"

private const val HP_VIDEO_DETAIL_BANNER_PROXY = "VideoDetailBannerAdHook.Proxy"

private const val HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_REQUEST = "VideoDetailBannerAdHook.PausedPageRequest"

private const val HP_VIDEO_DETAIL_BANNER_PAUSED_PAGE_PANEL = "VideoDetailBannerAdHook.PausedPagePanel"

private const val HP_VIDEO_DETAIL_BANNER_RELATE_GAME = "VideoDetailBannerAdHook.RelateGame"

private const val HP_SKIP_VIDEO_AD_PLAY_VIEW = "SkipVideoAdHook.PlayView"

private const val HP_SKIP_VIDEO_AD_PLAYER_CORE = "SkipVideoAdHook.PlayerCore"

private const val HP_SKIP_VIDEO_AD_CARD = "SkipVideoAdHook.CardPlayer"

private const val HP_SKIP_VIDEO_AD_STORY = "SkipVideoAdHook.StoryPlayer"

private const val HP_SKIP_VIDEO_AD_PROGRESS_DRAW = "SkipVideoAdProgressHook.ProgressDraw"

private const val HP_SKIP_VIDEO_AD_PROGRESS_STORY = "SkipVideoAdProgressHook.StorySeekBar"

private const val HP_SKIP_VIDEO_AD_PROGRESS_INLINE = "SkipVideoAdProgressHook.InlineProgress"

private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_DETAIL = "SkipVideoAdAutoLike.Detail"

private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_STORY = "SkipVideoAdAutoLike.Story"

private const val HP_SKIP_VIDEO_AD_AUTO_LIKE_GEMINI = "SkipVideoAdAutoLike.Gemini"

private const val SPLASH_GSON_CLASS = "com.google.gson.Gson"

private val SPLASH_FAST_JSON_CLASSES = arrayOf(
    "com.alibaba.fastjson.JSON",
    "com.alibaba.fastjson2.JSON",
)

private val SPLASH_FAST_JSON_PARSE_METHODS = setOf("parse", "parseObject")

private val SPLASH_KOTLINX_JSON_CLASSES = arrayOf(
    "kotlinx.serialization.json.Json",
)

private val REWARD_ACTIVITY_CLASSES = arrayOf(
    "com.bilibili.ad.reward.activity.RewardAdActivity",
    "com.bilibili.ad.reward.activity.RewardAdDisplayActivity",
    "com.bilibili.ad.reward.activity.BaseRewardAdActivity",
    "com.bilibili.ad.reward.RewardAdActivity",
)

private const val REWARD_HEADER_VIEW = "com.bilibili.ad.reward.view.header.RewardAdHeaderView"

private const val REWARD_COUNT_DOWN_TEXT_VIEW = "com.bilibili.ad.reward.view.header.CountDownTextView"

private const val REWARD_MINI_GAME_ABILITY =
    "com.bilibili.lib.fasthybrid.game.engine.ability.impl.ad.BWARewardAbility"

private val REWARD_JUMP_CLOCK_CLASSES = arrayOf("Ke.m", "Pe.k")

private const val REWARD_PACKAGE = "com.bilibili.ad.reward"

private const val REWARD_DISPLAY = "com.bilibili.ad.reward.RewardAdDisplay"

private const val REWARD_JUMP_REASON = "jump"

private const val ANDROIDX_VIEW_BINDING = "androidx.viewbinding.ViewBinding"

private const val G_AD_BIZ_KT = "com.bilibili.gripper.api.ad.biz.GAdBizKt"

private const val G_AD_VIDEO_DETAIL = "com.bilibili.gripper.api.ad.biz.GAdVideoDetail"

private const val I_AD_UNDER_PLAYER = "com.bilibili.gripper.api.ad.biz.videodetail.underplayer.IAdUnderPlayer"

private const val I_AD_PANEL = "com.bilibili.gripper.api.ad.biz.videodetail.IAdPanel"

private const val I_VD_PAUSED_PAGE = "com.bilibili.gripper.api.ad.biz.videodetail.pausedpage.IVDPausedPage"

private const val I_AD_VIDEO_RELATE = "com.bilibili.gripper.api.ad.biz.videodetail.relate.IAdVideoRelate"

private const val I_AD_MERCHANDISE = "com.bilibili.gripper.api.ad.biz.videodetail.merchandise.IAdMerchandise"

private const val RELATE_GAME_COMPONENT_PACKAGE =
    "com.bilibili.ship.theseus.united.page.intro.module.relate.game"

private val RELATE_GAME_COMPONENT_CANDIDATE_NAMES = listOf(
    "com.bilibili.ship.theseus.united.page.intro.module.relate.game.e",
    "com.bilibili.ship.theseus.united.page.intro.module.relate.game.d",
    "com.bilibili.ship.theseus.united.page.intro.module.relate.game.f",
    "com.bilibili.ship.theseus.united.page.intro.module.relate.game.RelateGameComponent",
)

private val GEMINI_BINDING_COMPONENT_CLASSES = arrayOf(
    "com.bilibili.app.gemini.ui.l",
    "com.bilibili.app.gemini.ui.m",
    "com.bilibili.app.gemini.ui.k",
    "com.bilibili.app.gemini.ui.n",
    "com.bilibili.app.gemini.ui.o",
    "com.bilibili.app.gemini.ui.d",
    "com.bilibili.app.gemini.ui.e",
)

private const val GEMINI_SIMPLE_VIEW_ENTRY = "com.bilibili.app.gemini.ui.UIComponent\$b"

private const val PLAYER_CORE_SERVICE_INTERFACE = "tv.danmaku.biliplayerv2.service.IPlayerCoreService"

private const val CARD_PLAYER_CONTEXT_INTERFACE = "tv.danmaku.video.bilicardplayer.ICardPlayerContext"

private val STATE_METHOD_NAMES = setOf("getState")

private val CARD_STATE_METHOD_NAMES = setOf("getPlayerState", "getState")

private const val PROGRESS_BAR_CLASS = "android.widget.ProgressBar"

private const val PANEL_WIDGET_KT_CLASS = "com.bilibili.inline.panel.PanelWidgetKt"

private val INLINE_PROGRESS_CLASSES = setOf(
    "com.bilibili.app.comm.list.common.inline.widgetV3.InlineProgressWidgetV3",
)

private const val DETAIL_LIKE_COMPONENT =
    "com.bilibili.ship.theseus.united.page.intro.module.kingposition.KingPositionComponent2\$LikeComponent"

private const val DETAIL_LIKE_STATE_OWNER =
    "com.bilibili.ship.theseus.united.page.intro.module.kingposition.KingPositionService\$d"

private const val STORY_ACTION_OWNER = "com.bilibili.video.story.action.g"

private const val STORY_LIKE_WIDGET = "com.bilibili.video.story.action.widget.StoryLikeWidget"

private const val STORY_LANDSCAPE_LIKE_WIDGET = "com.bilibili.video.story.action.widget.StoryLandscapeLikeWidget"

private const val GEMINI_PLAYER_LIKE_WIDGET = "com.bilibili.app.gemini.player.widget.like.GeminiPlayerLikeWidget"
