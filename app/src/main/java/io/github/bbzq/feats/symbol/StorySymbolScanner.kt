package io.github.bbzq.feats.symbol

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.allMethods
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal fun scanStoryPlayerAd(
    classLoader: ClassLoader,
): SymbolScanResult<StoryPlayerAdSymbols> {
    val storyFeedResponse = classLoader.loadClassOrNull(STORY_FEED_RESPONSE)
    val feedGetItems = storyFeedResponse?.declaredMethods?.firstOrNull {
        it.name == "getItems" &&
            it.parameterCount == 0 &&
            List::class.java.isAssignableFrom(it.returnType) &&
            !Modifier.isStatic(it.modifiers) &&
            !Modifier.isAbstract(it.modifiers)
    }?.apply { isAccessible = true }

    val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
    val pagerMethods = storyPagerPlayer?.declaredMethods
        ?.filter(::isStoryPagerListMethod)
        ?.distinctBy(Method::toGenericString)
        .orEmpty()

    val rerankTask = classLoader.loadClassOrNull(STORY_AD_RERANK_TASK)
    val rerankInvoke = rerankTask?.declaredMethods?.firstOrNull {
        it.name == "invokeSuspend" &&
            it.parameterCount == 1 &&
            it.parameterTypes[0] == Any::class.java &&
            it.returnType == Any::class.java &&
            !Modifier.isStatic(it.modifiers) &&
            !Modifier.isAbstract(it.modifiers)
    }?.apply { isAccessible = true }
    val unitField = classLoader.loadClassOrNull(KOTLIN_UNIT)
        ?.getDeclaredField("INSTANCE")
        ?.apply { isAccessible = true }

    if (feedGetItems == null && pagerMethods.isEmpty() && (rerankInvoke == null || unitField == null)) {
        return SymbolScanResult.Missing("story player ad hook points not found")
    }

    val symbols = StoryPlayerAdSymbols(
        feedGetItems = feedGetItems?.let(MethodDescriptor::of),
        pagerListMethods = pagerMethods.map(MethodDescriptor::of),
        rerankInvokeSuspend = rerankInvoke?.let(MethodDescriptor::of),
        kotlinUnitField = if (rerankInvoke != null) unitField?.let(FieldDescriptor::of) else null,
        evidence = "feed=${feedGetItems != null},pager=${pagerMethods.size},rerank=${rerankInvoke != null && unitField != null}",
    )
    val hookPoints = listOf(
        childHookPoint(HP_STORY_PLAYER_AD_FEED, feedGetItems != null, "feed getItems not found", "method=${feedGetItems?.name}"),
        childHookPoint(HP_STORY_PLAYER_AD_PAGER, pagerMethods.isNotEmpty(), "pager list method not found", "methods=${pagerMethods.size}"),
        childHookPoint(
            HP_STORY_PLAYER_AD_RERANK,
            rerankInvoke != null && unitField != null,
            "rerank invoke/unit field not found",
            "invoke=${rerankInvoke != null},unit=${unitField != null}",
        ),
    )
    return SymbolScanResult.Found(symbols, "StoryPlayerAd", symbols.evidence, hookPoints)
}

internal fun scanStoryFullscreen(
    classLoader: ClassLoader,
): SymbolScanResult<StoryFullscreenSymbols> {
    val storyActivity = classLoader.loadClassOrNull(STORY_VIDEO_ACTIVITY)
        ?: return SymbolScanResult.Missing("story video activity class not found")
    val onCreate = storyActivity.findMethod("onCreate", Void.TYPE, Bundle::class.java)
        ?.apply { isAccessible = true }
    val onWindowFocusChanged = storyActivity.findMethod(
        "onWindowFocusChanged",
        Void.TYPE,
        Boolean::class.javaPrimitiveType!!,
    )?.takeIf { it.declaringClass.name == STORY_VIDEO_ACTIVITY }
        ?.apply { isAccessible = true }
    val onResume = storyActivity.findMethod("onResume", Void.TYPE)
        ?.takeIf { it.declaringClass.name == STORY_VIDEO_ACTIVITY }
        ?.apply { isAccessible = true }

    if (onCreate == null || onWindowFocusChanged == null) {
        return SymbolScanResult.Missing(
            "story fullscreen lifecycle methods not found: " +
                "onCreate=${onCreate != null},focus=${onWindowFocusChanged != null}",
        )
    }

    val symbols = StoryFullscreenSymbols(
        onCreate = MethodDescriptor.of(onCreate),
        onWindowFocusChanged = MethodDescriptor.of(onWindowFocusChanged),
        onResume = onResume?.let(MethodDescriptor::of),
        evidence = "onCreate=${onCreate.declaringClass.name},focus=${onWindowFocusChanged.declaringClass.name},onResume=${onResume != null}",
    )
    val hookPoints = listOf(
        childHookPoint(HP_STORY_FULLSCREEN_ON_CREATE, true, "onCreate not found", "method=${onCreate.name}"),
        childHookPoint(HP_STORY_FULLSCREEN_FOCUS, true, "onWindowFocusChanged not found", "method=${onWindowFocusChanged.name}"),
        optionalChildHookPoint(HP_STORY_FULLSCREEN_ON_RESUME, onResume != null, "onResume not declared by StoryVideoActivity", "method=${onResume?.name}"),
    )
    return SymbolScanResult.Found(
        symbols,
        STORY_VIDEO_ACTIVITY,
        symbols.evidence,
        hookPoints,
    )
}

internal fun scanStoryDanmaku(
    classLoader: ClassLoader,
    bridge: () -> DexKitBridge? = { null },
): SymbolScanResult<StoryDanmakuSymbols> {
    val storyDetail = classLoader.loadClassOrNull(STORY_DETAIL)
        ?: return SymbolScanResult.Missing("story detail class not found")
    val storyPagerPlayer = classLoader.loadClassOrNull(STORY_PAGER_PLAYER)
        ?: return SymbolScanResult.Missing("story pager player class not found")
    val interactLayerService = classLoader.loadClassOrNull(INTERACT_LAYER_SERVICE)
        ?: return SymbolScanResult.Missing("interact layer service class not found")
    val introCommentService = STORY_INTRO_COMMENT_SERVICES
        .mapNotNull { classLoader.loadClassOrNull(it) }
        .firstOrNull { it.findStoryIntroCommentShow() != null }

    fun isStoryCommentShowMethod(method: Method): Boolean {
        val params = method.parameterTypes
        return method.returnType == Void.TYPE &&
            params.size in 6..10 &&
            params[0] == storyDetail &&
            params.getOrNull(2) == Long::class.javaPrimitiveType &&
            params.getOrNull(3) == Long::class.javaPrimitiveType &&
            params.getOrNull(4) == String::class.java
    }

    fun isStoryCommentHideMethod(method: Method, showMethodName: String? = null): Boolean {
        return (showMethodName == null || method.name != showMethodName) &&
            method.parameterCount == 0 &&
            (method.returnType == Void.TYPE || method.returnType == Boolean::class.javaPrimitiveType)
    }

    val showMethods = ArrayList<Method>()
    val hideMethods = ArrayList<Method>()

    // 1. DexKit-based resolution (fast & version-independent)
    val currentBridge = bridge()
    if (currentBridge != null) {
        runCatching {
            val candidateMethods = currentBridge.findMethod(
                FindMethod.create()
                    .searchPackages("com.bilibili.video.story")
                    .matcher(MethodMatcher.create().returnType("void"))
            ).mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
                .filter { isStoryCommentShowMethod(it) }

            val ifaceMethod = candidateMethods.firstOrNull { it.declaringClass.isInterface }
            val ifaceHideName = ifaceMethod?.declaringClass?.allMethods()
                ?.firstOrNull { isStoryCommentHideMethod(it, ifaceMethod.name) }?.name

            val targetClasses = candidateMethods
                .filter { !it.declaringClass.isInterface && !Modifier.isAbstract(it.declaringClass.modifiers) }
                .map { it.declaringClass }
                .toMutableSet()

            if (ifaceMethod != null) {
                val ifaceName = ifaceMethod.declaringClass.name
                currentBridge.findClass(
                    FindClass.create().matcher(ClassMatcher.create().addInterface(ifaceName))
                ).forEach { classData ->
                    classLoader.loadClassOrNull(classData.name)?.let { cls ->
                        if (!cls.isInterface && !Modifier.isAbstract(cls.modifiers)) {
                            targetClasses.add(cls)
                        }
                    }
                }
            }

            for (cls in targetClasses) {
                val showM = cls.allMethods().firstOrNull { isStoryCommentShowMethod(it) } ?: continue
                val hideM = (ifaceHideName?.let { cls.findMethod(it, Void.TYPE) ?: cls.findMethod(it, Boolean::class.javaPrimitiveType!!) })
                    ?: cls.allMethods().firstOrNull { isStoryCommentHideMethod(it, showM.name) }
                if (hideM != null) {
                    showMethods.add(showM.apply { isAccessible = true })
                    hideMethods.add(hideM.apply { isAccessible = true })
                }
            }
        }
    }

    // 2. Pure reflection fallback if DexKit unavailable or found nothing
    if (showMethods.isEmpty() || hideMethods.isEmpty()) {
        showMethods.clear()
        hideMethods.clear()

        val packagePrefixes = listOf(
            "com.bilibili.video.story.action",
            "com.bilibili.video.story.action.widget.comment",
            "com.bilibili.video.story.comment",
        )
        val outerNames = listOf("StoryCommentHelper", "C", "B", "A") +
            ('a'..'z').map { it.toString() } +
            ('D'..'Z').map { it.toString() }

        searchLoop@ for (pkg in packagePrefixes) {
            for (name in outerNames) {
                val prefix = "$pkg.$name"
                val outerClass = classLoader.loadClassOrNull(prefix) ?: continue

                val interfaceCandidates = buildList {
                    for (suffix in listOf("b", "c", "d", "a", "e", "f", "h", "g", "i")) {
                        classLoader.loadClassOrNull("$prefix\$$suffix")?.let(::add)
                    }
                    outerClass.declaredClasses.filterTo(this) { it.isInterface }
                }.distinct()

                var containerInterface: Class<*>? = null
                var ifaceShow: Method? = null
                for (iface in interfaceCandidates) {
                    val showCandidate = iface.allMethods().firstOrNull { isStoryCommentShowMethod(it) }
                    if (showCandidate != null) {
                        containerInterface = iface
                        ifaceShow = showCandidate
                        break
                    }
                }

                if (containerInterface == null || ifaceShow == null) continue

                val ifaceHideName = containerInterface.allMethods()
                    .firstOrNull { isStoryCommentHideMethod(it, ifaceShow.name) }?.name

                val concreteImplementors = buildList {
                    listOf("VerticalContainerV2", "VerticalContainer", "LandscapeContainer", "f", "g", "d", "e", "h", "i", "c", "b").forEach { suffix ->
                        classLoader.loadClassOrNull("$prefix\$$suffix")?.let(::add)
                    }
                    outerClass.declaredClasses.forEach(::add)
                }.distinct().filter { cls ->
                    !cls.isInterface && !Modifier.isAbstract(cls.modifiers) && (
                        containerInterface.isAssignableFrom(cls) ||
                            cls.allMethods().any { isStoryCommentShowMethod(it) }
                    )
                }

                for (cls in concreteImplementors) {
                    val showM = cls.allMethods().firstOrNull { isStoryCommentShowMethod(it) } ?: continue
                    val hideM = (ifaceHideName?.let { cls.findMethod(it, Void.TYPE) ?: cls.findMethod(it, Boolean::class.javaPrimitiveType!!) })
                        ?: cls.allMethods().firstOrNull { isStoryCommentHideMethod(it, showM.name) }
                    if (hideM != null) {
                        showMethods.add(showM.apply { isAccessible = true })
                        hideMethods.add(hideM.apply { isAccessible = true })
                    }
                }

                if (showMethods.isNotEmpty() && hideMethods.isNotEmpty()) {
                    break@searchLoop
                }
            }
        }
    }

    val introCommentShow = introCommentService?.findStoryIntroCommentShow()
        ?.apply { isAccessible = true }
    // dismissSheet() returns Boolean since 9.15; older builds return Unit.
    val introCommentDismiss = introCommentService?.allMethods()
        ?.singleOrNull { method ->
            method.name == "a" &&
                method.parameterCount == 0 &&
                (method.returnType == Void.TYPE || method.returnType == Boolean::class.javaPrimitiveType)
        }
        ?.apply { isAccessible = true }

    // Fallback to intro comment show/dismiss if normal show/hide are still empty
    if (showMethods.isEmpty() && introCommentShow != null) {
        showMethods.add(introCommentShow)
    }
    if (hideMethods.isEmpty() && introCommentDismiss != null) {
        hideMethods.add(introCommentDismiss)
    }

    val setDanmakuOpacity = interactLayerService.findMethod(
        "setDanmakuOpacity",
        Void.TYPE,
        Float::class.javaPrimitiveType!!,
        Boolean::class.javaPrimitiveType!!,
    )?.apply { isAccessible = true }
    val updateCanvasCandidates = storyPagerPlayer.allMethods().filter { method ->
        method.returnType == Void.TYPE &&
            method.declaringClass.name == STORY_PAGER_PLAYER &&
            method.parameterTypes.map { it.name } == listOf("int", "int", "int")
    }.toList()
    val updateCanvas = updateCanvasCandidates.singleOrNull()?.apply { isAccessible = true }

    val distinctShow = showMethods.distinctBy(Method::toGenericString)
    val distinctHide = hideMethods.distinctBy(Method::toGenericString)

    if (distinctShow.isEmpty() || distinctHide.isEmpty() || setDanmakuOpacity == null || updateCanvas == null) {
        return SymbolScanResult.Missing(
            "story danmaku hook points not found: " +
                "show=${distinctShow.size},hide=${distinctHide.size}," +
                "setOpacity=${setDanmakuOpacity != null},updateCanvas=${updateCanvas != null}",
        )
    }

    val symbols = StoryDanmakuSymbols(
        commentShowMethods = distinctShow.map(MethodDescriptor::of),
        commentHideMethods = distinctHide.map(MethodDescriptor::of),
        introCommentShowMethod = introCommentShow?.let(MethodDescriptor::of),
        introCommentDismissMethod = introCommentDismiss?.let(MethodDescriptor::of),
        setDanmakuOpacity = MethodDescriptor.of(setDanmakuOpacity),
        updateCanvas = MethodDescriptor.of(updateCanvas),
        evidence = "show=${distinctShow.size},hide=${distinctHide.size}," +
            "introShow=${introCommentShow != null},introDismiss=${introCommentDismiss != null}," +
            "setOpacity=true,updateCanvas=true",
    )
    val hookPoints = listOf(
        childHookPoint(HP_STORY_DANMAKU_COMMENT_SHOW, distinctShow.isNotEmpty(), "comment show method not found", "methods=${distinctShow.size}"),
        childHookPoint(HP_STORY_DANMAKU_COMMENT_HIDE, distinctHide.isNotEmpty(), "comment hide method not found", "methods=${distinctHide.size}"),
        optionalChildHookPoint(
            HP_STORY_DANMAKU_INTRO_COMMENT_SHOW,
            introCommentShow != null,
            "intro comment show method not found",
            "method=${introCommentShow?.name}",
        ),
        optionalChildHookPoint(
            HP_STORY_DANMAKU_INTRO_COMMENT_DISMISS,
            introCommentDismiss != null,
            "intro comment dismiss method not found",
            "method=${introCommentDismiss?.name}",
        ),
        childHookPoint(HP_STORY_DANMAKU_SET_OPACITY, true, "setDanmakuOpacity method not found", "method=${setDanmakuOpacity.name}"),
        childHookPoint(
            HP_STORY_DANMAKU_UPDATE_CANVAS,
            true,
            "story updateCanvas method not unique: candidates=${updateCanvasCandidates.size}",
            "method=${updateCanvas.name}",
        ),
    )
    return SymbolScanResult.Found(
        symbols,
        "StoryDanmaku",
        symbols.evidence,
        hookPoints,
    )
}

internal fun scanStoryComponentAlpha(
    classLoader: ClassLoader,
): SymbolScanResult<StoryComponentAlphaSymbols> {
    val infoModule = classLoader.loadClassOrNull(STORY_INFO_MODULE)
        ?: return SymbolScanResult.Missing("story info module class not found")
    val rightModule = classLoader.loadClassOrNull(STORY_RIGHT_MODULE)
        ?: return SymbolScanResult.Missing("story right module class not found")
    val bottomModule = classLoader.loadClassOrNull(STORY_BOTTOM_MODULE)
        ?: return SymbolScanResult.Missing("story bottom module class not found")
    val storySeekBar = classLoader.loadClassOrNull(STORY_SEEK_BAR_CLASS)
        ?: return SymbolScanResult.Missing("story seek bar class not found")
    val storyFragment = classLoader.loadClassOrNull(STORY_VIDEO_FRAGMENT)
        ?: return SymbolScanResult.Missing("story video fragment class not found")

    val infoConstructors = infoModule.storyModuleConstructors()
    val rightConstructors = rightModule.storyModuleConstructors()
    val bottomConstructors = bottomModule.storyModuleConstructors()
    val seekBarConstructors = storySeekBar.storyViewConstructors()
    val onCreateView = storyFragment.findMethod(
        "onCreateView",
        View::class.java,
        LayoutInflater::class.java,
        ViewGroup::class.java,
        Bundle::class.java,
    )?.takeIf { it.declaringClass.name == STORY_VIDEO_FRAGMENT }
        ?.apply { isAccessible = true }

    if (
        infoConstructors.isEmpty() ||
        rightConstructors.isEmpty() ||
        bottomConstructors.isEmpty() ||
        seekBarConstructors.isEmpty() ||
        onCreateView == null
    ) {
        return SymbolScanResult.Missing(
            "story component alpha hook points not found: " +
                "info=${infoConstructors.size},right=${rightConstructors.size},bottom=${bottomConstructors.size}," +
                "seekBar=${seekBarConstructors.size},top=${onCreateView != null}",
        )
    }

    val symbols = StoryComponentAlphaSymbols(
        infoConstructors = infoConstructors.map(ConstructorDescriptor::of),
        rightConstructors = rightConstructors.map(ConstructorDescriptor::of),
        bottomConstructors = bottomConstructors.map(ConstructorDescriptor::of),
        seekBarConstructors = seekBarConstructors.map(ConstructorDescriptor::of),
        fragmentOnCreateView = MethodDescriptor.of(onCreateView),
        evidence = "info=${infoConstructors.size},right=${rightConstructors.size},bottom=${bottomConstructors.size}," +
            "seekBar=${seekBarConstructors.size},top=${onCreateView.name}",
    )
    val hookPoints = listOf(
        childHookPoint(
            HP_STORY_COMPONENT_ALPHA_INFO,
            infoConstructors.isNotEmpty(),
            "story info constructors not found",
            "constructors=${infoConstructors.size}",
        ),
        childHookPoint(
            HP_STORY_COMPONENT_ALPHA_RIGHT,
            rightConstructors.isNotEmpty(),
            "story right constructors not found",
            "constructors=${rightConstructors.size}",
        ),
        childHookPoint(
            HP_STORY_COMPONENT_ALPHA_BOTTOM,
            bottomConstructors.isNotEmpty(),
            "story bottom constructors not found",
            "constructors=${bottomConstructors.size}",
        ),
        childHookPoint(
            HP_STORY_COMPONENT_ALPHA_SEEKBAR,
            seekBarConstructors.isNotEmpty(),
            "story seek bar constructors not found",
            "constructors=${seekBarConstructors.size}",
        ),
        childHookPoint(
            HP_STORY_COMPONENT_ALPHA_TOP,
            true,
            "story fragment onCreateView not found",
            "method=${onCreateView.name}",
        ),
    )
    return SymbolScanResult.Found(
        symbols,
        "StoryComponentAlpha",
        symbols.evidence,
        hookPoints,
    )
}

private fun Class<*>.findStoryIntroCommentShow(): Method? =
    allMethods().singleOrNull { method ->
        method.name == "b" &&
            method.returnType == Void.TYPE &&
            method.parameterCount == 1 &&
            isStoryIntroCommentTabParameter(method.parameterTypes[0])
    }

private fun isStoryIntroCommentTabParameter(type: Class<*>): Boolean =
    type.name.startsWith(STORY_TAB_PACKAGE_PREFIX) ||
        type.allFields().any { field ->
            field.type.name.startsWith(STORY_TAB_PACKAGE_PREFIX) || field.type.name == STORY_DETAIL
        }

private fun isStoryPagerListMethod(method: Method): Boolean =
    method.returnType == Void.TYPE &&
        method.declaringClass.name == STORY_PAGER_PLAYER &&
        !Modifier.isStatic(method.modifiers) &&
        !Modifier.isAbstract(method.modifiers) &&
        !method.isSynthetic &&
        !method.isBridge &&
        method.parameterCount == 1 &&
        List::class.java.isAssignableFrom(method.parameterTypes[0]) &&
        isStoryDetailListParameter(method)

private fun isStoryDetailListParameter(method: Method): Boolean {
    val parameterType = method.genericParameterTypes.firstOrNull()?.typeName ?: return true
    return parameterType == List::class.java.name || parameterType.contains(STORY_DETAIL)
}

private fun Class<*>.storyModuleConstructors() =
    declaredConstructors
        .filter { constructor ->
            constructor.parameterTypes.let { params ->
                params.size in 1..2 &&
                    params[0] == Context::class.java &&
                    (params.size == 1 || params[1] == AttributeSet::class.java)
            }
        }
        .onEach { it.isAccessible = true }

private fun Class<*>.storyViewConstructors() =
    declaredConstructors
        .filter { constructor ->
            constructor.parameterTypes.let { params ->
                params.size in 1..3 &&
                    params[0] == Context::class.java &&
                    (params.size == 1 || params[1] == AttributeSet::class.java) &&
                    (params.size < 3 || params[2] == Int::class.javaPrimitiveType)
            }
        }
        .onEach { it.isAccessible = true }

private const val HP_STORY_FULLSCREEN_ON_CREATE = "StoryFullscreenHook.OnCreate"

private const val HP_STORY_FULLSCREEN_FOCUS = "StoryFullscreenHook.OnWindowFocusChanged"

private const val HP_STORY_FULLSCREEN_ON_RESUME = "StoryFullscreenHook.OnResume"

private const val HP_STORY_DANMAKU_COMMENT_SHOW = "StoryDanmakuHook.CommentShow"

private const val HP_STORY_DANMAKU_COMMENT_HIDE = "StoryDanmakuHook.CommentHide"

private const val HP_STORY_DANMAKU_INTRO_COMMENT_SHOW = "StoryDanmakuHook.IntroCommentShow"

private const val HP_STORY_DANMAKU_INTRO_COMMENT_DISMISS = "StoryDanmakuHook.IntroCommentDismiss"

private const val HP_STORY_DANMAKU_SET_OPACITY = "StoryDanmakuHook.SetDanmakuOpacity"

private const val HP_STORY_DANMAKU_UPDATE_CANVAS = "StoryDanmakuHook.UpdateCanvas"

private const val HP_STORY_COMPONENT_ALPHA_INFO = "StoryComponentAlphaHook.InfoModule"

private const val HP_STORY_COMPONENT_ALPHA_RIGHT = "StoryComponentAlphaHook.RightModule"

private const val HP_STORY_COMPONENT_ALPHA_BOTTOM = "StoryComponentAlphaHook.BottomModule"

private const val HP_STORY_COMPONENT_ALPHA_SEEKBAR = "StoryComponentAlphaHook.SeekBar"

private const val HP_STORY_COMPONENT_ALPHA_TOP = "StoryComponentAlphaHook.TopControls"

private const val HP_STORY_PLAYER_AD_FEED = "StoryPlayerAdHook.Feed"

private const val HP_STORY_PLAYER_AD_PAGER = "StoryPlayerAdHook.Pager"

private const val HP_STORY_PLAYER_AD_RERANK = "StoryPlayerAdHook.Rerank"

private const val STORY_VIDEO_ACTIVITY = "com.bilibili.video.story.StoryVideoActivity"

private const val STORY_VIDEO_FRAGMENT = "com.bilibili.video.story.StoryVideoFragment"

private const val STORY_FEED_RESPONSE = "com.bilibili.video.story.api.StoryFeedResponse"

private const val STORY_AD_RERANK_TASK = "com.bilibili.video.story.action.service.StoryAdReRankService\$2"

private const val STORY_INFO_MODULE = "com.bilibili.video.story.module.StoryInfoModule"

private const val STORY_RIGHT_MODULE = "com.bilibili.video.story.module.StoryRightModule"

private const val STORY_BOTTOM_MODULE = "com.bilibili.video.story.module.StoryBottomModule"

private const val STORY_DETAIL = "com.bilibili.video.story.StoryDetail"

// 混淆字母随版本漂移(9.15 为 t,9.17 为 w,9.19 为 q),已知名称优先,其余按结构逐个检查
private val STORY_INTRO_COMMENT_SERVICES = (listOf("q", "w", "t", "p") + ('a'..'z').map { it.toString() })
    .distinct()
    .map { "com.bilibili.video.story.action.widget.comment.$it" }

private const val STORY_TAB_PACKAGE_PREFIX = "com.bilibili.video.story.tab."
