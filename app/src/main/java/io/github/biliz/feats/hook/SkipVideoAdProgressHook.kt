package io.github.biliz.feats.hook

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import io.github.biliz.ModuleSettings
import io.github.biliz.SkipVideoAdMode
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.allMethods
import io.github.biliz.feats.callMethod
import io.github.biliz.feats.callStaticMethod
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.symbol.RestoredSkipVideoAdProgressSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

class SkipVideoAdProgressHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val noArgMethods = ConcurrentHashMap<String, Method>()
    private val missingNoArgMethods = ConcurrentHashMap.newKeySet<String>()
    private val storyControllerFields = ConcurrentHashMap<Class<*>, Field>()
    private val missingStoryControllerFields = ConcurrentHashMap.newKeySet<Class<*>>()
    private val playerContainerFields = ConcurrentHashMap<Class<*>, Field>()
    private val missingPlayerContainerFields = ConcurrentHashMap.newKeySet<Class<*>>()
    private val directorObservers = Collections.synchronizedMap(WeakHashMap<Any, Any>())
    private val reflectionFailureLogs = ConcurrentHashMap.newKeySet<String>()
    private val attachedProgressBars = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ProgressBar, Boolean>()))

    private var restoredSymbols: RestoredSkipVideoAdProgressSymbols? = null
    private val panelWidgetKtClass: Class<*>?
        get() = restoredSymbols?.panelWidgetKtClass
    private val videoDirectorObserverType: Class<*>? by lazy {
        runCatching { Class.forName(VIDEO_DIRECTOR_OBSERVER_CLASS, false, classLoader) }
            .getOrNull()
    }
    override fun startHook() {
        if (env.processName != env.packageName) return
        ModuleSettings.refreshSkipVideoAdCache(prefs)
        if (!ModuleSettings.isSkipVideoAdEnabledCached(prefs)) return
        val symbols = env.symbols?.skipVideoAdProgress?.restore(classLoader)
        if (symbols == null) {
            log("startHook: SkipVideoAdProgress skipped because symbols are unavailable")
            return
        }
        restoredSymbols = symbols
        instance = this

        runCatching {
            val setProgressDrawableMethod = ProgressBar::class.java.getMethod("setProgressDrawable", Drawable::class.java)
            env.hookAfter(setProgressDrawableMethod) { param ->
                val progressBar = param.thisObject as? ProgressBar ?: return@hookAfter
                if (isSupportedProgressView(progressBar)) {
                    attachMarkerDrawable(progressBar)
                }
            }
        }.onFailure { log("Failed to hook ProgressBar.setProgressDrawable", it) }

        val count = hookPlayerSeekLifecycle(symbols) +
            hookStorySeekBarLifecycle(symbols) +
            hookInlineProgressUpdates(symbols)
        log("startHook: SkipVideoAdProgress (WrappedProgressDrawable architecture, 0 onDraw hooks), methods=$count")
    }

    private fun hookPlayerSeekLifecycle(symbols: RestoredSkipVideoAdProgressSymbols): Int {
        var count = 0
        symbols.playerSeekAttachMethods.forEach { method ->
            count += runCatching {
                env.hookAfter(method) { param ->
                    runCatching {
                        val progressBar = param.thisObject as? ProgressBar ?: return@runCatching
                        attachMarkerDrawable(progressBar)
                        bindPlayerSeekView(progressBar, requestSegments = true)
                    }.onFailure {
                        log("SkipVideoAdProgress player seek bind failed at ${method.name}", it)
                    }
                }
                1
            }.getOrElse {
                log("SkipVideoAdProgress failed to hook ${method.declaringClass.name}.${method.name}", it)
                0
            }
        }
        return count
    }

    private fun hookStorySeekBarLifecycle(symbols: RestoredSkipVideoAdProgressSymbols): Int {
        var count = 0
        symbols.storyOnStartMethods.forEach { method ->
            count += runCatching {
                env.hookAfter(method) { param ->
                    runCatching {
                        val progressBar = param.thisObject as? ProgressBar ?: return@runCatching
                        attachMarkerDrawable(progressBar)
                        bindStoryView(progressBar, requestSegments = false)
                    }.onFailure {
                        log("SkipVideoAdProgress story bind failed at ${method.name}", it)
                    }
                }
                1
            }.getOrElse {
                log("SkipVideoAdProgress failed to hook ${method.declaringClass.name}.${method.name}", it)
                0
            }
        }

        return count
    }

    private fun hookInlineProgressUpdates(symbols: RestoredSkipVideoAdProgressSymbols): Int {
        var count = 0
        symbols.inlineUpdateMethods.forEach { method ->
            count += runCatching {
                env.hookAfter(method) { param ->
                    runCatching {
                        val progressBar = param.thisObject as? ProgressBar ?: return@runCatching
                        attachMarkerDrawable(progressBar)
                        bindInlineProgressView(progressBar, requestSegments = true)
                    }.onFailure {
                        log("SkipVideoAdProgress inline bind failed at ${method.declaringClass.name}.${method.name}", it)
                    }
                }
                1
            }.getOrElse {
                log("SkipVideoAdProgress failed to hook ${method.declaringClass.name}.${method.name}", it)
                0
            }
        }
        return count
    }

    fun attachMarkerOverlay(progressBar: ProgressBar) {
        attachMarkerDrawable(progressBar)
    }

    fun attachMarkerDrawable(progressBar: ProgressBar) {
        if (!isSupportedProgressView(progressBar)) return
        val progressDrawable = progressBar.progressDrawable ?: return

        if (progressDrawable is LayerDrawable) {
            val bgId = android.R.id.background
            val bg = progressDrawable.findDrawableByLayerId(bgId)
                ?: if (progressDrawable.numberOfLayers > 0) progressDrawable.getDrawable(0) else null
            if (bg != null && bg !is MarkerBackgroundDrawable) {
                val wrappedBg = MarkerBackgroundDrawable(bg) { canvas ->
                    drawSegmentsWithOffset(progressBar, canvas)
                }
                val layerIndex = (0 until progressDrawable.numberOfLayers).firstOrNull {
                    progressDrawable.getDrawable(it) === bg
                }
                if (layerIndex != null) {
                    progressDrawable.setDrawable(layerIndex, wrappedBg)
                } else {
                    progressDrawable.setDrawableByLayerId(bgId, wrappedBg)
                }
                progressBar.invalidate()
            }
        } else if (progressDrawable !is MarkerProgressDrawableWrapper) {
            val wrapped = MarkerProgressDrawableWrapper(progressDrawable) { canvas ->
                drawSegmentsWithOffset(progressBar, canvas)
            }
            progressBar.progressDrawable = wrapped
            progressBar.invalidate()
        }

        if (attachedProgressBars.add(progressBar)) {
            progressBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                attachMarkerDrawable(progressBar)
            }
        }
    }

    private fun drawSegmentsWithOffset(progressBar: ProgressBar, canvas: Canvas) {
        val saveCount = canvas.save()
        if (progressBar.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            canvas.scale(-1.0f, 1.0f)
            canvas.translate(-(progressBar.width - progressBar.paddingRight).toFloat(), -progressBar.paddingTop.toFloat())
        } else {
            canvas.translate(-progressBar.paddingLeft.toFloat(), -progressBar.paddingTop.toFloat())
        }
        drawSegments(progressBar, canvas)
        canvas.restoreToCount(saveCount)
    }

    fun findAndAttachProgressOverlays(rootView: View) {
        if (rootView is ProgressBar && isSupportedProgressView(rootView)) {
            attachMarkerDrawable(rootView)
            resolveMarkerState(rootView)
            return
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i) ?: continue
                findAndAttachProgressOverlays(child)
            }
        }
    }

    private class MarkerBackgroundDrawable(
        wrapped: Drawable,
        private val onDrawMarkers: (Canvas) -> Unit,
    ) : DrawableWrapper(wrapped) {
        override fun draw(canvas: Canvas) {
            super.draw(canvas)
            onDrawMarkers(canvas)
        }
    }

    private class MarkerProgressDrawableWrapper(
        wrapped: Drawable,
        private val onDrawMarkers: (Canvas) -> Unit,
    ) : DrawableWrapper(wrapped) {
        override fun draw(canvas: Canvas) {
            super.draw(canvas)
            onDrawMarkers(canvas)
        }
    }

    private fun drawSegments(progressBar: ProgressBar, canvas: Canvas?) {
        if (canvas == null) return
        val config = ModuleSettings.getSkipVideoAdCache(prefs)
        if (!config.enabled) return

        val state = resolveMarkerState(progressBar) ?: return
        val durationMs = durationForDrawing(progressBar, state) ?: return
        val segments = state.segments.filter { segment ->
            (config.modes[segment.category] ?: SkipVideoAdMode.IGNORE) != SkipVideoAdMode.IGNORE
        }
        if (segments.isEmpty()) return

        SkipVideoAdMarkerRenderer.draw(
            progressBar = progressBar,
            canvas = canvas,
            durationMs = durationMs,
            segments = segments,
            colorForCategory = ::colorFor,
        )
        SkipVideoAdState.markSegmentsDrawn(state.key, progressBar.markerDetectionPositionMs(durationMs))
    }

    private fun resolveMarkerState(progressBar: ProgressBar): SkipVideoAdState.TimelineMarkerState? {
        return when {
            isStorySeekBar(progressBar) -> bindStoryView(progressBar, requestSegments = true)
            isInlineProgressView(progressBar) -> bindInlineProgressView(progressBar, requestSegments = true)
            isPlayerSeekView(progressBar) -> bindPlayerSeekView(progressBar, requestSegments = true)
            else -> SkipVideoAdState.stateForView(progressBar)
        }
    }

    private fun bindPlayerSeekView(
        progressBar: ProgressBar?,
        requestSegments: Boolean,
    ): SkipVideoAdState.TimelineMarkerState? {
        if (progressBar == null) return null
        val controller = progressBar.callNoArg("getPlayerCoreService")
        val directors = resolvePlayerContainer(progressBar)?.let(::currentDirectors).orEmpty()
        observeDirectors(directors)
        val identity = resolveIdentityFromDirectors(directors)
        val key = identity?.let(SkipVideoAdState::activateVideo)
            ?: SkipVideoAdState.keyForController(controller)
            ?: return null

        SkipVideoAdState.bindController(controller, key)
        SkipVideoAdHook.registerRuntimePlayerController(controller)
        val durationMs = if (controller != null) {
            updateDurationFromController(key, controller)
        } else {
            progressBar.playerSeekDurationMs()?.let { durationMs ->
                SkipVideoAdState.updateDuration(key, durationMs)
                durationMs
            }
        }
        if (requestSegments && identity != null && durationMs != null && durationMs > 0L) {
            return bindProgressSegments(progressBar, identity, durationMs, listOf(controller), delayMs = 0L)
        }
        SkipVideoAdState.bindView(progressBar, key)
        return SkipVideoAdState.stateForKey(key)
    }

    private fun bindInlineProgressView(
        progressBar: ProgressBar?,
        requestSegments: Boolean,
    ): SkipVideoAdState.TimelineMarkerState? {
        if (progressBar == null) return null
        val controller = resolveInlinePlayerContext(progressBar) ?: return null
        val identity = resolveIdentityFromPlayableParams(controller.callNoArg("getCurrentPlayableParams"))
        val key = identity?.let(SkipVideoAdState::activateVideo)
            ?: SkipVideoAdState.keyForController(controller)
            ?: return null

        SkipVideoAdState.bindController(controller, key)
        SkipVideoAdHook.registerRuntimePlayerController(controller)
        val durationMs = updateDurationFromController(key, controller)
        if (requestSegments && identity != null && durationMs != null && durationMs > 0L) {
            return bindProgressSegments(progressBar, identity, durationMs, listOf(controller), delayMs = 0L)
        }
        SkipVideoAdState.bindView(progressBar, key)
        return SkipVideoAdState.stateForKey(key)
    }

    private fun bindStoryView(
        progressBar: ProgressBar?,
        requestSegments: Boolean,
    ): SkipVideoAdState.TimelineMarkerState? {
        if (progressBar == null) return null
        val controller = resolveStoryController(progressBar) ?: return null
        val player = controller.callNoArg("getPlayer")
        val detail = controller.callNoArg("getData")
        val identity = resolveIdentityFromStoryPlayer(player)
            ?: detail?.let(::resolveIdentityFromDetail)
            ?: return null
        val key = SkipVideoAdState.activateVideo(identity)

        SkipVideoAdState.bindController(controller, key)
        if (player != null) {
            SkipVideoAdState.bindController(player, key)
        }

        val durationMs = resolveStoryDurationMs(player, progressBar, detail, key)
        if (requestSegments && durationMs > 0L) {
            return bindProgressSegments(
                progressBar,
                identity,
                durationMs,
                listOf(controller, player),
                delayMs = storySegmentRequestDelayMs(progressBar),
            )
        }
        SkipVideoAdState.bindView(progressBar, key)
        SkipVideoAdState.updateDuration(key, durationMs)
        return SkipVideoAdState.stateForKey(key)
    }

    private fun bindProgressSegments(
        progressBar: ProgressBar,
        identity: SkipVideoAdState.VideoIdentity,
        durationMs: Long,
        controllers: List<Any?>,
        delayMs: Long,
    ): SkipVideoAdState.TimelineMarkerState? {
        val config = ModuleSettings.getSkipVideoAdCache(prefs)
        if (!config.enabled) return SkipVideoAdState.stateForKey(identity.key)
        return SkipVideoAdState.requestSegmentsAfterProgress(
            view = progressBar,
            controllers = controllers,
            identity = identity,
            durationMs = durationMs,
            enabledCategories = config.enabledCategories,
            delayMs = delayMs,
        ) { message, throwable ->
            log(message, throwable)
        }
    }

    private fun updateDurationFromController(key: String, controller: Any): Long? {
        val duration = controller.callNoArg("getDuration").asLong()
            ?: controller.callNoArg("getRealDuration").asLong()
            ?: return null
        SkipVideoAdState.updateDuration(key, duration)
        return duration
    }

    private fun durationForDrawing(
        progressBar: ProgressBar,
        state: SkipVideoAdState.TimelineMarkerState,
    ): Long? {
        val stateDuration = state.durationMs.takeIf { it > 0L }
        if (isPlayerSeekView(progressBar)) {
            return progressBar.playerSeekDurationMs() ?: stateDuration
        }
        return stateDuration ?: progressBar.maxDurationMs()
    }

    private fun resolveStoryDurationMs(
        player: Any?,
        progressBar: ProgressBar,
        detail: Any?,
        key: String,
    ): Long {
        player?.callNoArg("getDuration").asLong()?.takeIf { it > 0L }?.let { return it }
        player?.callNoArg("getRealDuration").asLong()?.takeIf { it > 0L }?.let { return it }
        progressBar.max.takeIf { it > 0 }?.toLong()?.let { return it }

        val detailDuration = detail?.callNoArg("getDuration").asLong()?.takeIf { it > 0L } ?: return 0L
        val existingDuration = SkipVideoAdState.stateForKey(key)?.durationMs ?: 0L
        val detailDurationMs = detailDuration * STORY_DETAIL_DURATION_SCALE
        if (existingDuration <= 0L) return detailDurationMs

        return if (kotlin.math.abs(existingDuration - detailDurationMs) <
            kotlin.math.abs(existingDuration - detailDuration)
        ) {
            detailDurationMs
        } else {
            detailDuration
        }
    }

    private fun resolveInlinePlayerContext(progressBar: ProgressBar): Any? =
        panelWidgetKtClass?.callStaticMethod("getPlayerContext", progressBar)

    private fun resolveIdentityFromStoryPlayer(player: Any?): SkipVideoAdState.VideoIdentity? =
        resolveIdentityFromPlayableParams(player?.callNoArg("getCurrentPlayableParam"))
            ?: resolveIdentityFromPlayableParams(player?.callNoArg("getCurrentPlayableParams"))

    private fun resolveIdentityFromDirectors(directors: List<Any>): SkipVideoAdState.VideoIdentity? {
        directors.forEach { director ->
            resolveIdentityFromPlayableParams(director.callNoArg("getCurrentPlayableParams"))?.let { return it }
        }
        return null
    }

    private fun observeDirectors(directors: List<Any>) {
        val observerType = videoDirectorObserverType ?: return
        directors.forEach { director ->
            if (directorObserverFor(director) != null) return@forEach
            val observer = createVideoDirectorObserver(observerType)
            var shouldInstall = false
            synchronized(directorObservers) {
                if (!directorObservers.containsKey(director)) {
                    directorObservers[director] = observer
                    shouldInstall = true
                }
            }
            if (shouldInstall) {
                director.callMethod("addVideoDirectorObserver", observer)
            }
        }
    }

    private fun directorObserverFor(director: Any): Any? =
        synchronized(directorObservers) {
            directorObservers[director]
        }

    private fun createVideoDirectorObserver(observerType: Class<*>): Any {
        val holder = arrayOfNulls<Any>(1)
        val observer = Proxy.newProxyInstance(
            observerType.classLoader ?: classLoader,
            arrayOf(observerType),
        ) { _, method, args ->
            when {
                method.name == "toString" && method.parameterCount == 0 -> "BILIZSkipVideoAdDirectorObserver"
                method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(holder[0])
                method.name == "equals" && method.parameterCount == 1 -> holder[0] === args?.firstOrNull()
                method.name in VIDEO_DIRECTOR_EVENT_METHODS -> {
                    runCatching {
                        bindPlayableParamsFromDirectorEvent(method.name, args)
                    }.onFailure {
                        log("SkipVideoAdProgress director observer failed at ${method.name}", it)
                    }
                    defaultReturnValue(method.returnType)
                }
                else -> defaultReturnValue(method.returnType)
            }
        }
        holder[0] = observer
        return observer
    }

    private fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0.0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun bindPlayableParamsFromDirectorEvent(methodName: String, args: Array<Any?>?) {
        val params = when (methodName) {
            "onItemWillChange" -> args?.getOrNull(1) ?: args?.getOrNull(0)
            else -> args?.firstOrNull()
        }
        val identity = resolveIdentityFromPlayableParams(params) ?: return
        if (methodName != "onItemWillChange") {
            SkipVideoAdState.activateVideo(identity)
        }
    }

    private fun currentDirectors(container: Any): List<Any> {
        val v1Director = container.callNoArg("getVideoPlayDirectorService")
        val v3Director = container.callNoArg("getPlayDirectorServiceV3")
        val directorVersion = container.callNoArg("getPlayerParams")
            ?.callNoArg("getConfig")
            ?.callNoArg("getDirectorVersion")
            ?.toString()

        val preferred = when (directorVersion) {
            "V1" -> listOf(v1Director, v3Director)
            "V3" -> listOf(v3Director, v1Director)
            else -> listOf(v3Director, v1Director)
        }
        return preferred.filterNotNull().distinctBy { System.identityHashCode(it) }
    }

    private fun resolvePlayerContainer(view: View): Any? {
        val type = view.javaClass
        playerContainerFields[type]?.let { field ->
            return runCatching { field.get(view) }.getOrNull()
        }
        if (type in missingPlayerContainerFields) return null

        val field = type.safeAllFields("player container").firstOrNull { candidate ->
            runCatching {
                candidate.type.name == PLAYER_CONTAINER_CLASS ||
                    (
                        candidate.type.hasNoArgMethod("getPlayDirectorServiceV3") &&
                            candidate.type.hasNoArgMethod("getVideoPlayDirectorService")
                        )
            }.getOrDefault(false)
        }
        if (field == null) {
            missingPlayerContainerFields.add(type)
            return null
        }

        playerContainerFields[type] = field
        return runCatching { field.get(view) }.getOrNull()
    }

    private fun resolveStoryController(view: View): Any? {
        val type = view.javaClass
        storyControllerFields[type]?.let { field ->
            return runCatching { field.get(view) }.getOrNull()
        }
        if (type in missingStoryControllerFields) return null

        val field = type.safeAllFields("story controller").firstOrNull { candidate ->
            runCatching {
                candidate.type.hasNoArgMethod("getData") &&
                    candidate.type.hasNoArgMethod("getPlayer")
            }.getOrDefault(false)
        }
        if (field == null) {
            missingStoryControllerFields.add(type)
            return null
        }

        storyControllerFields[type] = field
        return runCatching { field.get(view) }.getOrNull()
    }

    private fun resolveIdentityFromDetail(detail: Any): SkipVideoAdState.VideoIdentity? =
        SkipVideoAdState.resolveVideoIdentity(
            bvid = detail.callNoArg("getBvid") as? String,
            cid = detail.callNoArg("getCid"),
            aid = detail.callNoArg("getAid"),
        )

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

    private fun Class<*>.hasNoArgMethod(name: String): Boolean =
        findNoArgMethod(name) != null

    private fun Class<*>.findNoArgMethod(name: String): Method? =
        safeAllMethods("method $name").firstOrNull { method ->
            method.name == name && method.parameterCount == 0
        } ?: runCatching {
            methods.firstOrNull { method ->
                method.name == name && method.parameterCount == 0
            }?.apply { isAccessible = true }
        }.getOrNull()

    private fun Class<*>.findNoArgCanvasMethod(name: String): Method? =
        safeAllMethods("canvas method $name").firstOrNull { method ->
            method.name == name &&
                method.parameterCount == 1 &&
                method.parameterTypes.firstOrNull() == Canvas::class.java
        }

    private fun Class<*>.safeAllMethods(reason: String): List<Method> =
        runCatching { allMethods().toList() }
            .getOrElse {
                logReflectionFailure(reason, name, it)
                emptyList()
            }

    private fun Class<*>.safeAllFields(reason: String): List<Field> =
        runCatching { allFields().toList() }
            .getOrElse {
                logReflectionFailure(reason, name, it)
                emptyList()
            }

    private fun logReflectionFailure(reason: String, typeName: String, throwable: Throwable) {
        if (reflectionFailureLogs.add("$reason#$typeName")) {
            log("SkipVideoAdProgress failed to inspect $typeName for $reason", throwable)
        }
    }

    private fun isPlayerSeekView(view: ProgressBar): Boolean {
        val name = view.javaClass.name
        return name == PLAYER_SEEK_WIDGET_CLASS ||
            (name.endsWith(".PlayerSeekWidget3") && "playerbizcommon" in name)
    }

    private fun isInlineProgressView(view: ProgressBar): Boolean {
        val name = view.javaClass.name
        return name in INLINE_PROGRESS_CLASSES ||
            (name.endsWith(".InlineProgressWidgetV3") && ".inline." in name)
    }

    private fun isStorySeekBar(view: ProgressBar): Boolean {
        val name = view.javaClass.name
        return name == STORY_SEEK_BAR_CLASS ||
            (name.endsWith(".StorySeekBar") && ".video.story." in name)
    }

    private fun storySegmentRequestDelayMs(view: View): Long =
        if (isStoryFeedProgressView(view)) STORY_SEGMENT_REQUEST_DELAY_MS else 0L

    private fun isStoryFeedProgressView(view: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            val name = parent.javaClass.name
            if (name == STORY_VIEW_PAGER_WRAPPER_CLASS || name.endsWith(".StoryViewPagerWrapper")) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun isSupportedProgressView(view: ProgressBar): Boolean =
        isPlayerSeekView(view) || isStorySeekBar(view) || isInlineProgressView(view)

    private fun colorFor(category: String): Int =
        ModuleSettings.skipVideoAdCategories
            .firstOrNull { it.key == category }
            ?.color
            ?: 0xFFFB7299.toInt()

    private fun Class<*>.isNumericType(): Boolean =
        this == Int::class.javaPrimitiveType ||
            this == Int::class.javaObjectType ||
            this == Long::class.javaPrimitiveType ||
            this == Long::class.javaObjectType

    private fun Any?.asLong(): Long? = when (this) {
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

    private fun ProgressBar.maxDurationMs(): Long? =
        max.takeIf { it > 0 }?.toLong()

    private fun ProgressBar.playerSeekDurationMs(): Long? =
        maxDurationMs()?.takeIf { it >= MIN_PLAYER_SEEK_DURATION_MS }

    private fun ProgressBar.currentPositionMs(durationMs: Long): Long {
        val maxValue = max.takeIf { it > 0 } ?: return 0L
        if (durationMs <= 0L) return 0L
        val progressValue = progress.coerceIn(0, maxValue)
        return (progressValue.toDouble() / maxValue.toDouble() * durationMs)
            .roundToLong()
            .coerceIn(0L, durationMs)
    }

    private fun ProgressBar.markerDetectionPositionMs(durationMs: Long): Long {
        if (isStorySeekBar(this)) {
            return storyPlayerPositionMs(durationMs) ?: 0L
        }
        return currentPositionMs(durationMs)
    }

    private fun ProgressBar.storyPlayerPositionMs(durationMs: Long): Long? {
        val player = resolveStoryController(this)?.callNoArg("getPlayer") ?: return null
        val position = player.callNoArg("getCurrentPosition").asLong()?.takeIf { it > 0L } ?: return null
        if (durationMs > 0L && position > durationMs) return null
        return position
    }

    companion object {
        @Volatile
        var instance: SkipVideoAdProgressHook? = null
            private set

        private const val PLAYER_CONTAINER_CLASS = "tv.danmaku.biliplayerv2.PlayerContainer"
        private const val PLAYER_SEEK_WIDGET_CLASS = "com.bilibili.playerbizcommonv2.widget.seek.v3.PlayerSeekWidget3"
        private const val STORY_SEEK_BAR_CLASS = "com.bilibili.video.story.view.StorySeekBar"
        private const val STORY_VIEW_PAGER_WRAPPER_CLASS = "com.bilibili.video.story.StoryViewPagerWrapper"
        private const val VIDEO_DIRECTOR_OBSERVER_CLASS = "tv.danmaku.biliplayerv2.service.VideoDirectorObserver"
        private const val STORY_DETAIL_DURATION_SCALE = 1000L
        private const val STORY_SEGMENT_REQUEST_DELAY_MS = 3000L
        private const val MIN_PLAYER_SEEK_DURATION_MS = 1000L
        private val VIDEO_DIRECTOR_EVENT_METHODS = setOf(
            "onItemStart",
            "onPlayableParamsChanged",
            "onItemWillChange",
        )

        private val INLINE_PROGRESS_CLASSES = setOf(
            "com.bilibili.app.comm.list.common.inline.widgetV3.InlineProgressWidgetV3",
            "com.bilibili.p4439app.p4450comm.p4472list.common.inline.widgetV3.InlineProgressWidgetV3",
            "com.bilibili.p4440app.p4451comm.p4473list.common.inline.widgetV3.InlineProgressWidgetV3",
        )
    }
}
