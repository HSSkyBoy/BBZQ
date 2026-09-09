package io.github.biliz.feats.hook

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.biliz.ModuleSettings
import io.github.biliz.SkipVideoAdMode
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.BilibiliSponsorBlock
import io.github.biliz.feats.MethodHookParam
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allMethods
import io.github.biliz.feats.callMethod
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.symbol.RestoredSkipVideoAdSymbols
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class SkipVideoAdHook(env: RoamingEnv) : BaseRoamingHook(env) {
    @Volatile private var lastSeekTime = 0L
    @Volatile private var duration = -1L
    @Volatile private var bvid = ""
    @Volatile private var cid = ""
    @Volatile private var playbackKey = ""
    private val pendingAutoLikeVideos = ConcurrentHashMap.newKeySet<String>()
    private val pendingMarkerDetections = ConcurrentHashMap<String, PendingMarkerDetection>()
    private val completedMarkerDetections = ConcurrentHashMap.newKeySet<String>()

    private var waitTime = CHECK_INTERVAL_MS
    private var playerCoreServiceRef: WeakReference<Any>? = null
    private var cardPlayerContextRef: WeakReference<Any>? = null
    private var storyPlayerRef: WeakReference<Any>? = null
    private val reflectionFailureLogs = ConcurrentHashMap.newKeySet<String>()
    private val noArgMethods = ConcurrentHashMap<String, Method>()
    private val missingNoArgMethods = ConcurrentHashMap.newKeySet<String>()
    private val seekMethodsByControllerClass = ConcurrentHashMap<Class<*>, List<Method>>()
    private val hookedCurrentPositionMethods = ConcurrentHashMap.newKeySet<String>()
    private val hookedStateMethods = ConcurrentHashMap.newKeySet<String>()
    private val registeredRuntimeControllerClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val runtimeControllerLock = Any()
    private val playerCoreService: Any?
        get() = playerCoreServiceRef?.get()
    private val cardPlayerContext: Any?
        get() = cardPlayerContextRef?.get()
    private val storyPlayer: Any?
        get() = storyPlayerRef?.get()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun startHook() {
        val config = ModuleSettings.refreshSkipVideoAdCache(prefs)
        if (!config.enabled) return
        if (config.autoLikeEnabled) {
            SkipVideoAdAutoLike.install(env)
        }
        val symbols = env.symbols?.skipVideoAd?.restore(classLoader)
        if (symbols == null) {
            log("startHook: SkipVideoAd skipped because symbols are unavailable")
            if (env.processName == env.packageName) {
                toast("跳过视频广告未找到播放器接口")
            }
            return
        }
        activeInstance = this
        ensureActivityTracking()
        SkipVideoAdState.observeMarkerDrawn(::detectAfterMarkerDrawn)
        cacheSeekMethods(symbols)
        val count = installHookGroup("playView") { hookPlayViewUnite(symbols) } +
            installHookGroup("playerCore") { hookPlayerCoreService(symbols) } +
            installHookGroup("cardPlayer") { hookCardPlayerContext(symbols) } +
            installHookGroup("storyPlayer") { hookStoryPlayer(symbols) }
        log("startHook: SkipVideoAd, methods=$count")
        if (count == 0 && env.processName == env.packageName) {
            toast("跳过视频广告未找到播放器接口")
        }
        isInstalled = true
    }

    private fun installHookGroup(label: String, block: () -> Int): Int =
        runCatching(block).getOrElse {
            log("SkipVideoAd $label hook group failed", it)
            0
        }

    private fun ensureActivityTracking() {
        val application = env.hostContext as? Application ?: return
        if (!callbacksRegistered.compareAndSet(false, true)) return
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) {
                    topActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    if (topActivity?.get() === activity) {
                        topActivity = null
                    }
                }
            },
        )
    }

    private fun hookPlayViewUnite(symbols: RestoredSkipVideoAdSymbols): Int {
        var count = 0
        symbols.playViewMethods.forEach { method ->
            count += runCatching {
                env.hookBefore(method) { param ->
                    runCatching {
                        updateVideoIdentityFromRequest(param.args.firstOrNull())
                        if (method.name == "playViewUnite") {
                            param.args.getOrNull(1)?.let(::hookHandlerIfNeeded)
                        }
                    }.onFailure {
                        log("SkipVideoAd play view hook failed at ${method.declaringClass.name}.${method.name}", it)
                    }
                }
                1
            }.getOrElse {
                log("SkipVideoAd failed to hook ${method.declaringClass.name}.${method.name}", it)
                0
            }
        }
        return count
    }

    private val hookedHandlerClasses = java.util.Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    private fun hookHandlerIfNeeded(handler: Any) {
        val handlerClass = handler.javaClass
        if (!hookedHandlerClasses.add(handlerClass)) return
        val onNextMethods = handlerClass.allMethods().filter { it.name == "onNext" && it.parameterCount == 1 }
        onNextMethods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    updateVideoIdentityFromReply(param.args.firstOrNull())
                }.onFailure {
                    log("SkipVideoAd onNext handler failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }
    }

    private fun updateVideoIdentityFromRequest(req: Any?) {
        if (!isEnabled() || req == null) return
        var nextBvid = req.callMethod("getBvid") as? String ?: ""
        val vod = req.callMethod("getVod") ?: return
        if (nextBvid.isEmpty()) {
            val aid = vod.callMethod("getAid").asLong() ?: return
            if (aid == -1L) return
            nextBvid = SkipVideoAdState.bvidFromAid(aid)
        }
        val nextCid = vod.callMethod("getCid").asLong()?.toString() ?: return
        updateVideoIdentity(nextBvid, nextCid, resetMarkers = true)
    }

    private fun updateVideoIdentityFromReply(reply: Any?) {
        if (!isEnabled()) return
        val playArc = reply?.callMethod("getPlayArc") ?: return
        val aid = playArc.callMethod("getAid").asLong() ?: return
        if (aid == -1L) return
        val nextCid = playArc.callMethod("getCid").asLong()?.toString() ?: return
        updateVideoIdentity(SkipVideoAdState.bvidFromAid(aid), nextCid)
    }

    private fun updateVideoIdentity(nextBvid: String, nextCid: String, resetMarkers: Boolean = false) {
        val identity = SkipVideoAdState.resolveVideoIdentity(nextBvid, nextCid) ?: return
        val previousKey = playbackKey
        val samePlaybackIdentity = isSamePlaybackIdentity(previousKey, identity.key)
        if (resetMarkers || !samePlaybackIdentity) {
            SkipVideoAdState.resetProgressForVideo(identity.key)
            clearMarkerDetectionFor(identity.key)
        }
        if (!samePlaybackIdentity) {
            SkipVideoAdState.resetProgressForVideo(SkipVideoAdState.playbackIdentityKey(previousKey))
        }
        if (identity.bvid == bvid && identity.cid == cid) {
            SkipVideoAdState.activateVideo(identity)
            lastSeekTime = 0L
            waitTime = CHECK_INTERVAL_MS
            return
        }

        bvid = identity.bvid
        cid = identity.cid
        playbackKey = identity.key
        duration = -1L
        SkipVideoAdState.activateVideo(identity)
        bindControllerToIdentityIfNeeded(playerCoreService, identity)
        bindControllerToIdentityIfNeeded(cardPlayerContext, identity)
        bindControllerToIdentityIfNeeded(storyPlayer, identity)
        if (!samePlaybackIdentity) {
            clearMarkerDetectionFor(SkipVideoAdState.playbackIdentityKey(previousKey))
        }
        lastSeekTime = 0L
        waitTime = CHECK_INTERVAL_MS
    }

    private fun hookPlayerCoreService(symbols: RestoredSkipVideoAdSymbols): Int {
        return hookCurrentPositionMethods(
            methods = symbols.playerCoreCurrentPositionMethods,
            controllerKind = ControllerKind.PLAYER_CORE,
        ) + hookPlayerStateMethods(
            methods = symbols.playerCoreStateMethods,
            controllerKind = ControllerKind.PLAYER_CORE,
        )
    }

    private fun hookCardPlayerContext(symbols: RestoredSkipVideoAdSymbols): Int {
        return hookCurrentPositionMethods(
            methods = symbols.cardCurrentPositionMethods,
            controllerKind = ControllerKind.CARD,
        ) + hookPlayerStateMethods(
            methods = symbols.cardStateMethods,
            controllerKind = ControllerKind.CARD,
        )
    }

    private fun hookStoryPlayer(symbols: RestoredSkipVideoAdSymbols): Int {
        return hookCurrentPositionMethods(
            methods = symbols.storyCurrentPositionMethods,
            controllerKind = ControllerKind.STORY,
        ) + hookPlayerStateMethods(
            methods = symbols.storyStateMethods,
            controllerKind = ControllerKind.STORY,
        )
    }

    private fun cacheSeekMethods(symbols: RestoredSkipVideoAdSymbols) {
        seekMethodsByControllerClass.clear()
        cacheSeekMethods(symbols.playerCoreSeekMethods + symbols.cardSeekMethods + symbols.storySeekMethods)
    }

    private fun cacheSeekMethods(methods: List<Method>) {
        methods
            .groupBy { it.declaringClass }
            .forEach { (type, methods) ->
                seekMethodsByControllerClass.compute(type) { _, existing ->
                    (existing.orEmpty() + methods).distinctBy(Method::toGenericString)
                }
            }
    }

    private fun hookCurrentPositionMethods(
        methods: List<Method>,
        controllerKind: ControllerKind,
    ): Int = methods
        .distinctBy(Method::toGenericString)
        .sumOf { method -> hookCurrentPositionMethod(method, controllerKind) }

    private fun hookCurrentPositionMethod(method: Method, controllerKind: ControllerKind): Int {
        val methodKey = method.toGenericString()
        if (!hookedCurrentPositionMethods.add(methodKey)) return 0
        return runCatching {
            env.hookAfter(method) { param ->
                runCatching {
                    handleCurrentPosition(param, controllerKind)
                }.onFailure {
                    log("SkipVideoAd currentPosition callback failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
            1
        }.getOrElse {
            hookedCurrentPositionMethods.remove(methodKey)
            log("SkipVideoAd failed to hook ${method.declaringClass.name}.${method.name}", it)
            0
        }
    }

    private fun handleCurrentPosition(
        param: MethodHookParam,
        controllerKind: ControllerKind,
    ) {
        if (!isEnabled()) return
        val controller = param.thisObject ?: return
        val key = rememberPlayerController(controller, controllerKind) ?: return
        if (duration <= 0) {
            duration = resolveDuration(controller)
            if (duration > 0) {
                SkipVideoAdState.updateDuration(key, duration)
            }
        }
        val position = param.result.asLong() ?: return
        val now = System.currentTimeMillis()
        val videoKey = SkipVideoAdState.playbackIdentityKey(key)
        val markerDetection = pendingMarkerDetections[videoKey]
        if (markerDetection != null) {
            if (now - markerDetection.firstObservedAt < MARKER_DETECTION_SETTLE_MS) return
            val detectionPosition = markerDetection.positionMs
            if (detectionPosition <= 0L) {
                pendingMarkerDetections.remove(videoKey)
                lastSeekTime = now
                waitTime = CHECK_INTERVAL_MS
                return
            }
            pendingMarkerDetections.remove(videoKey)
            completedMarkerDetections.add(markerDetection.key)
            lastSeekTime = now
            waitTime = if (seekTo(
                    position = detectionPosition,
                    key = markerDetection.key,
                    controller = controller,
                    scope = SegmentDetectionScope.CONTAINING,
                )
            ) {
                SKIP_COOLDOWN_MS
            } else {
                CHECK_INTERVAL_MS
            }
            return
        }
        if (now - lastSeekTime > waitTime) {
            lastSeekTime = now
            waitTime = if (seekTo(position, key, controller, SegmentDetectionScope.OPENING)) {
                SKIP_COOLDOWN_MS
            } else {
                CHECK_INTERVAL_MS
            }
        }
    }

    private fun hookPlayerStateMethods(
        methods: List<Method>,
        controllerKind: ControllerKind,
    ): Int = methods
        .distinctBy(Method::toGenericString)
        .sumOf { method -> hookPlayerStateMethod(method, controllerKind) }

    private fun hookPlayerStateMethod(method: Method, controllerKind: ControllerKind): Int {
        val methodKey = method.toGenericString()
        if (!hookedStateMethods.add(methodKey)) return 0
        return runCatching {
            env.hookAfter(method) { param ->
                runCatching {
                    if (!isEnabled()) return@runCatching
                    val controller = param.thisObject ?: return@runCatching
                    val key = rememberPlayerController(controller, controllerKind) ?: return@runCatching
                    val state = param.result.asInt() ?: return@runCatching
                    if (state in 3..5 && duration <= 0) {
                        duration = resolveDuration(controller)
                        if (duration > 0) {
                            SkipVideoAdState.updateDuration(key, duration)
                        }
                    }
                }.onFailure {
                    log("SkipVideoAd playerState callback failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
            1
        }.getOrElse {
            hookedStateMethods.remove(methodKey)
            log("SkipVideoAd failed to hook ${method.declaringClass.name}.${method.name}", it)
            0
        }
    }

    internal fun registerRuntimePlayerController(controller: Any?) {
        if (controller == null || !isEnabled()) return
        synchronized(runtimeControllerLock) {
            val type = controller.javaClass
            if (!registeredRuntimeControllerClasses.add(type)) return
            val methods = try {
                type.allMethods().toList()
            } catch (throwable: Throwable) {
                registeredRuntimeControllerClasses.remove(type)
                log("SkipVideoAd failed to inspect runtime player ${type.name}", throwable)
                return
            }
            val currentPosition = methods.filter { it.isRuntimeCurrentPositionMethod() }
            val state = methods.filter { it.isRuntimeStateMethod() }
            val seek = methods.filter { it.isRuntimeSeekMethod() }
            cacheSeekMethods(seek)
            val currentHooks = hookCurrentPositionMethods(currentPosition, ControllerKind.PLAYER_CORE)
            val stateHooks = hookPlayerStateMethods(state, ControllerKind.PLAYER_CORE)
            log(
                "SkipVideoAd runtime player ${type.name}: " +
                    "current=${currentPosition.size},state=${state.size},seek=${seek.size}," +
                    "hooked=${currentHooks + stateHooks}",
            )
        }
    }

    private fun resolveDuration(controller: Any): Long {
        return controller.callMethod("getDuration").asLong()
            ?: controller.callMethod("getRealDuration").asLong()
            ?: -1L
    }

    private fun rememberPlayerController(controller: Any, controllerKind: ControllerKind): String? {
        if (controllerKind == ControllerKind.STORY) {
            val identity = resolveIdentityFromStoryController(controller)
            if (identity == null) {
                return null
            }
            storyPlayerRef = WeakReference(controller)
            val key = bindResolvedVideoIdentity(identity, controller)
            updateDurationFromController(key, controller)
            return key
        }

        when (controllerKind) {
            ControllerKind.PLAYER_CORE -> playerCoreServiceRef = WeakReference(controller)
            ControllerKind.CARD -> cardPlayerContextRef = WeakReference(controller)
            ControllerKind.STORY -> Unit
        }
        val key = SkipVideoAdState.keyForController(controller) ?: videoKey()
        updatePlaybackKey(key)
        SkipVideoAdState.bindController(controller, key)
        return key
    }

    private fun bindResolvedVideoIdentity(
        identity: SkipVideoAdState.VideoIdentity,
        controller: Any,
    ): String {
        progressSessionKeyForController(controller)?.let { key ->
            val progressIdentity = SkipVideoAdState.identityForKey(key) ?: identity
            if (progressIdentity.bvid != bvid || progressIdentity.cid != cid) {
                updateVideoIdentity(progressIdentity.bvid, progressIdentity.cid)
            } else {
                SkipVideoAdState.activateVideo(progressIdentity)
            }
            updatePlaybackKey(key)
            return key
        }

        if (identity.bvid != bvid || identity.cid != cid) {
            updateVideoIdentity(identity.bvid, identity.cid)
        } else {
            SkipVideoAdState.activateVideo(identity)
        }
        val key = SkipVideoAdState.keyForController(controller)
            ?.takeIf { SkipVideoAdState.keyMatchesIdentity(it, identity) }
            ?: identity.key
        updatePlaybackKey(key)
        if (key == identity.key) {
            SkipVideoAdState.bindController(controller, key)
        }
        return key
    }

    private fun progressSessionKeyForController(controller: Any): String? {
        val key = SkipVideoAdState.keyForController(controller)
            ?.takeIf(SkipVideoAdState::isProgressSessionKey)
            ?: return null
        SkipVideoAdState.identityForKey(key) ?: return null
        SkipVideoAdState.stateForKey(key) ?: return null
        return key
    }

    private fun bindControllerToIdentityIfNeeded(
        controller: Any?,
        identity: SkipVideoAdState.VideoIdentity,
    ) {
        if (controller == null) return
        if (SkipVideoAdState.keyMatchesIdentity(SkipVideoAdState.keyForController(controller), identity)) return
        SkipVideoAdState.bindController(controller, identity.key)
    }

    private fun updateDurationFromController(key: String, controller: Any) {
        val nextDuration = resolveDuration(controller)
        if (nextDuration > 0) {
            duration = nextDuration
            SkipVideoAdState.updateDuration(key, nextDuration)
        }
    }

    private fun updatePlaybackKey(key: String) {
        if (key.isBlank() || key == playbackKey) return
        val previousKey = playbackKey
        playbackKey = key
        duration = -1L
        if (!isSamePlaybackIdentity(previousKey, key)) {
            clearMarkerDetectionFor(SkipVideoAdState.playbackIdentityKey(previousKey))
            lastSeekTime = 0L
            waitTime = CHECK_INTERVAL_MS
        }
    }

    private fun isSamePlaybackIdentity(previousKey: String, nextKey: String): Boolean {
        if (previousKey.isBlank() || nextKey.isBlank()) return false
        return SkipVideoAdState.playbackIdentityKey(previousKey) == SkipVideoAdState.playbackIdentityKey(nextKey)
    }

    private fun videoKey(): String =
        SkipVideoAdState.resolveVideoIdentity(bvid, cid)?.key ?: ""

    private fun seekTo(
        position: Long,
        key: String,
        controller: Any,
        scope: SegmentDetectionScope,
    ): Boolean {
        val config = ModuleSettings.getSkipVideoAdCache(prefs)
        if (!config.enabled) return false
        val state = SkipVideoAdState.stateForKey(key) ?: return false
        if (!SkipVideoAdState.hasDrawnSegments(state.key)) return false
        val videoDuration = state.durationMs.takeIf { it > 0L } ?: duration
        if (videoDuration > 0 && position > videoDuration) return false

        state.segments.forEach { segment ->
            val mode = config.modes[segment.category] ?: SkipVideoAdMode.IGNORE
            if (mode == SkipVideoAdMode.IGNORE) return@forEach
            val start = (segment.segment[0] * 1000).toLong()
            val end = (segment.segment[1] * 1000).toLong()
            if (end <= start) return@forEach

            val playbackKey = SkipVideoAdState.playbackIdentityKey(state.key)
            val seekTarget = skipTargetAfter(end, videoDuration)
            when (mode) {
                SkipVideoAdMode.AUTO_SKIP -> {
                    if (!matchesSegmentScope(position, start, end, scope)) return@forEach
                    return seekPlayerTo(seekTarget, segment, state.key, controller).also { skipped ->
                        if (skipped) requestAutoLikeAfterSkip(playbackKey)
                    }
                }
                SkipVideoAdMode.MANUAL_SKIP -> {
                    if (!matchesSegmentScope(position, start, end, scope)) return@forEach
                    showManualSkipPrompt(seekTarget, segment, controller, state.key)
                }
                SkipVideoAdMode.SHOW_IN_BAR,
                SkipVideoAdMode.IGNORE -> Unit
            }
        }
        return false
    }

    private fun matchesSegmentScope(
        position: Long,
        start: Long,
        end: Long,
        scope: SegmentDetectionScope,
    ): Boolean =
        when (scope) {
            SegmentDetectionScope.OPENING -> isSegmentOpening(position, start, end)
            SegmentDetectionScope.CONTAINING -> isInsideSegment(position, start, end)
        }

    private fun isSegmentOpening(position: Long, start: Long, end: Long): Boolean =
        isInsideSegment(position, start, end) && position <= start + SEGMENT_OPENING_WINDOW_MS

    private fun isInsideSegment(position: Long, start: Long, end: Long): Boolean =
        position >= start && position < end

    private fun skipTargetAfter(end: Long, videoDuration: Long): Long {
        val target = end + POST_SKIP_PADDING_MS
        return if (videoDuration > 0) target.coerceAtMost(videoDuration) else target
    }

    private fun detectAfterMarkerDrawn(event: SkipVideoAdState.MarkerDrawEvent) {
        if (event.key in completedMarkerDetections) return
        val now = System.currentTimeMillis()
        val existing = pendingMarkerDetections[event.videoKey]
        pendingMarkerDetections[event.videoKey] = if (existing == null || existing.key != event.key) {
            PendingMarkerDetection(event.key, now, event.positionMs)
        } else {
            existing.copy(positionMs = event.positionMs)
        }
    }

    private fun clearMarkerDetectionFor(videoKey: String) {
        if (videoKey.isBlank()) return
        pendingMarkerDetections.remove(videoKey)
        completedMarkerDetections
            .filter { SkipVideoAdState.playbackIdentityKey(it) == videoKey }
            .forEach(completedMarkerDetections::remove)
    }

    private fun seekPlayerTo(
        position: Long,
        segment: BilibiliSponsorBlock.Segment,
        key: String,
        preferredController: Any? = null,
    ): Boolean {
        val controllers = buildList {
            preferredController?.let(::add)
            storyPlayer?.let(::add)
            cardPlayerContext?.let(::add)
            playerCoreService?.let(::add)
        }
            .distinctBy { System.identityHashCode(it) }
            .filter { controllerMatchesPlaybackKey(it, key) }
        if (controllers.isEmpty()) return false

        controllers.forEach { controller ->
            if (invokeSeek(controller, position)) {
                log("SkipVideoAd skipped ${segment.category} to $position via ${controller.javaClass.name}")
                toast(skipToastMessage(segment))
                return true
            }
        }
        return false
    }

    private fun controllerMatchesPlaybackKey(controller: Any, key: String): Boolean {
        val controllerKey = SkipVideoAdState.keyForController(controller) ?: return false
        return SkipVideoAdState.playbackIdentityKey(controllerKey) == SkipVideoAdState.playbackIdentityKey(key)
    }

    private fun invokeSeek(controller: Any, position: Long): Boolean {
        seekMethodsFor(controller).forEach { method ->
            val args = when (method.parameterCount) {
                1 -> arrayOf(position.coerceToMethodType(method.parameterTypes[0]))
                2 -> arrayOf(position.coerceToMethodType(method.parameterTypes[0]), true)
                else -> return@forEach
            }
            val invoked = runCatching {
                method.invoke(controller, *args)
                true
            }.onFailure {
                log("SkipVideoAd seekTo failed via ${controller.javaClass.name}", it)
            }.getOrDefault(false)
            if (invoked) return true
        }
        return false
    }

    private fun seekMethodsFor(controller: Any): List<Method> {
        val controllerType = controller.javaClass
        seekMethodsByControllerClass[controllerType]?.let { return it }
        return seekMethodsByControllerClass.entries
            .firstOrNull { (type, _) -> type.isAssignableFrom(controllerType) }
            ?.value
            .orEmpty()
    }

    private fun resolveIdentityFromStoryController(controller: Any): SkipVideoAdState.VideoIdentity? =
        resolveIdentityFromPlayableParams(controller.callNoArg("getCurrentPlayableParam"))
            ?: resolveIdentityFromPlayableParams(controller.callNoArg("getCurrentPlayableParams"))

    private fun resolveIdentityFromPlayableParams(params: Any?): SkipVideoAdState.VideoIdentity? {
        if (params == null) return null
        val directIdentity = SkipVideoAdState.resolveVideoIdentity(
            bvid = params.callNoArg("getBvid") as? String,
            cid = params.callNoArg("getCid"),
            aid = params.callNoArg("getAvid") ?: params.callNoArg("getAid"),
        )
        if (directIdentity != null) return directIdentity

        val displayParams = params.callNoArg("getDisplayParams")
        val displayIdentity = SkipVideoAdState.resolveVideoIdentity(
            bvid = displayParams?.callNoArg("getBvid") as? String,
            cid = displayParams?.callNoArg("getCid"),
            aid = displayParams?.callNoArg("getAvid") ?: displayParams?.callNoArg("getAid"),
        )
        if (displayIdentity != null) return displayIdentity

        val danmakuParams = params.callNoArg("getDanmakuResolveParams")
        return SkipVideoAdState.resolveVideoIdentity(
            bvid = danmakuParams?.callNoArg("getBvid") as? String,
            cid = danmakuParams?.callNoArg("getCid"),
            aid = danmakuParams?.callNoArg("getAvid") ?: danmakuParams?.callNoArg("getAid"),
        )
    }

    private fun Any.callNoArg(name: String): Any? {
        val type = javaClass
        val cacheKey = type.name + "#" + name
        noArgMethods[cacheKey]?.let { method ->
            return runCatching { method.invoke(this) }.getOrNull()
        }
        if (cacheKey in missingNoArgMethods) return null

        val method = type.findNoArgMethod(name)
        if (method == null) {
            missingNoArgMethods.add(cacheKey)
            return null
        }
        noArgMethods[cacheKey] = method
        return runCatching { method.invoke(this) }.getOrNull()
    }

    private fun Class<*>.findNoArgMethod(name: String): Method? =
        safeAllMethods("method $name").firstOrNull { method ->
            method.name == name && method.parameterCount == 0
        } ?: runCatching {
            methods.firstOrNull { method ->
                method.name == name && method.parameterCount == 0
            }?.apply { isAccessible = true }
        }.getOrNull()

    private fun Class<*>.safeAllMethods(reason: String): List<Method> =
        runCatching { allMethods().toList() }
            .getOrElse {
                logReflectionFailure(reason, name, it)
                emptyList()
            }

    private fun logReflectionFailure(reason: String, typeName: String, throwable: Throwable) {
        if (reflectionFailureLogs.add("$reason#$typeName")) {
            log("SkipVideoAd failed to inspect $typeName for $reason", throwable)
        }
    }

    private fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(env.hostContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showManualSkipPrompt(
        position: Long,
        segment: BilibiliSponsorBlock.Segment,
        controller: Any?,
        key: String,
    ): Boolean {
        val activity = topActivity?.get()
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            toast(manualSkipToastMessage(segment))
            return false
        }

        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                return@runOnUiThread
            }

            val root = activity.findViewById<ViewGroup>(android.R.id.content)
                ?: activity.window?.decorView as? ViewGroup
                ?: return@runOnUiThread
            if (root.findViewWithTag<View>(MANUAL_PROMPT_TAG) != null) {
                return@runOnUiThread
            }

            val prompt = LinearLayout(activity).apply {
                tag = MANUAL_PROMPT_TAG
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                elevation = dp(12).toFloat()
                setPadding(dp(12), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat()
                    setColor(MANUAL_PROMPT_BACKGROUND)
                }
                setOnClickListener {
                    removeFromParent(this)
                    skipFromManualPrompt(position, segment, controller, key)
                }
            }

            val textColumn = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(activity).apply {
                        text = manualPromptTitle(segment)
                        textSize = 13f
                        maxWidth = dp(190)
                        maxLines = 1
                        setTextColor(Color.WHITE)
                    },
                )
                addView(
                    TextView(activity).apply {
                        text = manualPromptTimeRange(segment)
                        textSize = 11f
                        maxWidth = dp(190)
                        maxLines = 1
                        setTextColor(MANUAL_PROMPT_SECONDARY_TEXT)
                    },
                )
            }

            val actionView = TextView(activity).apply {
                text = "跳过"
                textSize = 13f
                gravity = Gravity.CENTER
                minWidth = dp(54)
                minHeight = dp(32)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(MANUAL_PROMPT_ACTION_BACKGROUND)
                }
                setOnClickListener {
                    removeFromParent(prompt)
                    skipFromManualPrompt(position, segment, controller, key)
                }
            }

            prompt.addView(
                textColumn,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = dp(12)
                },
            )
            prompt.addView(actionView)

            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(manualPromptBottomMarginDp(controller))
                marginStart = dp(16)
                marginEnd = dp(16)
            }
            root.addView(prompt, params)
            mainHandler.postDelayed({
                removeFromParent(prompt)
            }, MANUAL_PROMPT_DURATION_MS)
        }
        return true
    }

    private fun skipFromManualPrompt(
        position: Long,
        segment: BilibiliSponsorBlock.Segment,
        controller: Any?,
        key: String,
    ) {
        if (seekPlayerTo(position, segment, key, controller)) {
            requestAutoLikeAfterSkip(SkipVideoAdState.playbackIdentityKey(key))
        }
    }

    private fun requestAutoLikeAfterSkip(key: String) {
        if (!ModuleSettings.getSkipVideoAdCache(prefs).autoLikeEnabled) return
        val videoLikeKey = autoLikeKey(key) ?: return
        if (!pendingAutoLikeVideos.add(videoLikeKey)) return

        mainHandler.postDelayed({
            attemptAutoLike(videoLikeKey, 1)
        }, AUTO_LIKE_DELAY_MS)
    }

    private fun attemptAutoLike(videoLikeKey: String, attempt: Int) {
        if (!ModuleSettings.getSkipVideoAdCache(prefs).autoLikeEnabled) {
            pendingAutoLikeVideos.remove(videoLikeKey)
            return
        }

        when (SkipVideoAdAutoLike.likeCurrentVideo(::log)) {
            SkipVideoAdAutoLike.AutoLikeResult.PERFORMED -> {
                log("SkipVideoAd auto-liked current video")
                pendingAutoLikeVideos.remove(videoLikeKey)
            }
            SkipVideoAdAutoLike.AutoLikeResult.ALREADY_LIKED -> {
                pendingAutoLikeVideos.remove(videoLikeKey)
            }
            SkipVideoAdAutoLike.AutoLikeResult.NO_CANDIDATE -> {
                if (attempt >= AUTO_LIKE_MAX_ATTEMPTS) {
                    log("SkipVideoAd auto-like found no ready like action")
                    pendingAutoLikeVideos.remove(videoLikeKey)
                } else {
                    mainHandler.postDelayed({
                        attemptAutoLike(videoLikeKey, attempt + 1)
                    }, AUTO_LIKE_RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun autoLikeKey(key: String): String? =
        key.ifBlank { videoKey() }
            .ifBlank {
                listOf(bvid, cid)
                    .filter { it.isNotBlank() }
                    .joinToString(":")
            }
            .ifBlank { null }

    private fun removeFromParent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun dp(value: Int): Int =
        (value * env.hostContext.resources.displayMetrics.density + 0.5f).toInt()

    private fun manualPromptBottomMarginDp(controller: Any?): Int {
        val controllerName = controller?.javaClass?.name.orEmpty()
        return if (".video.story." in controllerName) {
            STORY_MANUAL_PROMPT_BOTTOM_MARGIN_DP
        } else {
            MANUAL_PROMPT_BOTTOM_MARGIN_DP
        }
    }

    private fun isEnabled(): Boolean =
        ModuleSettings.isSkipVideoAdEnabledCached(prefs)

    private fun Method.isRuntimeCurrentPositionMethod(): Boolean =
        isRuntimeConcreteMethod() &&
            name == "getCurrentPosition" &&
            parameterCount == 0 &&
            returnType.isRuntimeNumericType()

    private fun Method.isRuntimeStateMethod(): Boolean =
        isRuntimeConcreteMethod() &&
            name in RUNTIME_STATE_METHOD_NAMES &&
            parameterCount == 0 &&
            returnType.isRuntimeNumericType()

    private fun Method.isRuntimeSeekMethod(): Boolean =
        isRuntimeConcreteMethod() &&
            name == "seekTo" &&
            parameterCount in 1..2 &&
            parameterTypes[0].isRuntimeNumericType() &&
            (parameterCount == 1 || parameterTypes[1].isRuntimeBooleanType())

    private fun Method.isRuntimeConcreteMethod(): Boolean =
        !Modifier.isStatic(modifiers) &&
            !Modifier.isAbstract(modifiers) &&
            !declaringClass.isInterface

    private fun Class<*>.isRuntimeNumericType(): Boolean =
        this == Int::class.javaPrimitiveType ||
            this == Int::class.javaObjectType ||
            this == Long::class.javaPrimitiveType ||
            this == Long::class.javaObjectType

    private fun Class<*>.isRuntimeBooleanType(): Boolean =
        this == Boolean::class.javaPrimitiveType || this == Boolean::class.javaObjectType

    private fun Any?.asInt(): Int? = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull()
        else -> null
    }

    private fun Any?.asLong(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    private fun Long.coerceToMethodType(type: Class<*>): Any =
        when (type) {
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType,
            Short::class.javaPrimitiveType,
            Short::class.javaObjectType -> toInt()
            else -> this
        }

    private fun skipToastMessage(segment: BilibiliSponsorBlock.Segment): String =
        "已跳过${categoryLabel(segment)}"

    private fun manualSkipToastMessage(segment: BilibiliSponsorBlock.Segment): String =
        "检测到${categoryLabel(segment)}片段"

    private fun manualPromptTitle(segment: BilibiliSponsorBlock.Segment): String =
        "检测到${categoryLabel(segment)}片段"

    private fun manualPromptTimeRange(segment: BilibiliSponsorBlock.Segment): String =
        "${formatSeconds(segment.segment[0])} - ${formatSeconds(segment.segment[1])}"

    private fun categoryLabel(segment: BilibiliSponsorBlock.Segment): String =
        ModuleSettings.skipVideoAdCategories
            .firstOrNull { it.key == segment.category }
            ?.label
            ?: segment.category

    private fun formatSeconds(value: Float): String = String.format(Locale.US, "%.1fs", value)

    companion object {
        private const val CHECK_INTERVAL_MS = 1000L
        private const val SEGMENT_OPENING_WINDOW_MS = 3000L
        private const val MARKER_DETECTION_SETTLE_MS = 800L
        private const val POST_SKIP_PADDING_MS = 500L
        private const val SKIP_COOLDOWN_MS = 1000L
        private const val MANUAL_PROMPT_DURATION_MS = 3000L
        private const val AUTO_LIKE_DELAY_MS = 400L
        private const val AUTO_LIKE_RETRY_DELAY_MS = 350L
        private const val AUTO_LIKE_MAX_ATTEMPTS = 5
        private const val MANUAL_PROMPT_BOTTOM_MARGIN_DP = 92
        private const val STORY_MANUAL_PROMPT_BOTTOM_MARGIN_DP = 216
        private const val MANUAL_PROMPT_TAG = "biliz_skip_video_ad_manual_prompt"
        private val MANUAL_PROMPT_BACKGROUND = Color.argb(230, 18, 18, 18)
        private val MANUAL_PROMPT_ACTION_BACKGROUND = Color.rgb(251, 114, 153)
        private val MANUAL_PROMPT_SECONDARY_TEXT = Color.argb(190, 255, 255, 255)
        private val RUNTIME_STATE_METHOD_NAMES = setOf("getState", "getPlayerState")

        private val callbacksRegistered = java.util.concurrent.atomic.AtomicBoolean(false)

        @Volatile
        private var activeInstance: SkipVideoAdHook? = null

        internal fun registerRuntimePlayerController(controller: Any?) {
            activeInstance?.registerRuntimePlayerController(controller)
        }

        @Volatile
        private var topActivity: WeakReference<Activity>? = null
    }

    private enum class ControllerKind {
        PLAYER_CORE,
        CARD,
        STORY,
    }

    private enum class SegmentDetectionScope {
        OPENING,
        CONTAINING,
    }

    private data class PendingMarkerDetection(
        val key: String,
        val firstObservedAt: Long,
        val positionMs: Long,
    )
}
