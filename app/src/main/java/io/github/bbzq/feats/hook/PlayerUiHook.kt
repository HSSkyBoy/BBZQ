package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

class PlayerUiHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var currentVideoDetailActivity: WeakReference<Activity>? = null
    private val gestureExclusionAttached = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val globalLayoutAttached = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val trackedBooleanStates = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val hookedStateClasses = Collections.synchronizedSet(HashSet<String>())
    private val hookedInitialClasses = Collections.synchronizedSet(HashSet<String>())
    @Volatile
    private var serviceSchemaLogged = false
    @Volatile
    private var cachedNoOpFunction2: Any? = null

    override fun startHook() {
        if (env.processName != env.packageName) return
        val application = env.hostContext as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (isVideoDetailActivity(activity) || isStoryActivity(activity)) {
                    currentVideoDetailActivity = WeakReference(activity)
                    if (ModuleSettings.isPlayerTransparentStatusBarEnabled(prefs)) {
                        applyTransparentStatusBar(activity)
                    }
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                } else if (isPotentialPlayerActivity(activity)) {
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentVideoDetailActivity?.get() === activity) {
                    currentVideoDetailActivity = null
                }
            }
        })

        application.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
                val activity = currentVideoDetailActivity?.get() ?: return
                if (!activity.isFinishing && !activity.isDestroyed) {
                    ensureSystemGestureExclusionSafe(activity)
                    schedulePlayerUiTuning(activity)
                }
            }
            override fun onLowMemory() = Unit
            override fun onTrimMemory(level: Int) = Unit
        })

        installLegacyStoryWidgetHook()
        installComposeStoryHook()

        log("startHook: PlayerUi installed (portrait story button control & gesture safe)")
        isInstalled = true
    }

    @Suppress("DEPRECATION")
    private fun applyTransparentStatusBar(activity: Activity) {
        val window = activity.window ?: return
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    // 清理播放器向系统注册的边缘手势排除区（systemGestureExclusionRects），防止侧滑返回失效
    private fun ensureSystemGestureExclusionSafe(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val decor = activity.window?.decorView ?: return@runCatching
                decor.systemGestureExclusionRects = emptyList<Rect>()
                synchronized(gestureExclusionAttached) {
                    if (gestureExclusionAttached.containsKey(decor)) return@runCatching
                    gestureExclusionAttached[decor] = true
                }
                decor.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (decor.systemGestureExclusionRects.isNotEmpty()) {
                        decor.systemGestureExclusionRects = emptyList<Rect>()
                    }
                }
            }
        }
    }

    /**
     * 针对 9.0.0 ~ 9.15.0 以及双轨运行时保留的传统 Widget（GeminiPlayerFullStoryWidget / GeminiPlayerFullscreenWidget）
     */
    private fun installLegacyStoryWidgetHook() {
        LEGACY_STORY_WIDGET_CLASSES.forEach { className ->
            runCatching {
                val widgetClass = classLoader.findClassOrNull(className) ?: return@runCatching
                hookLegacyWidgetClass(widgetClass)
            }.onFailure {
                log("PlayerUi: installLegacyStoryWidgetHook ($className) failed", it)
            }
        }
    }

    private fun hookLegacyWidgetClass(widgetClass: Class<*>) {
        widgetClass.declaredConstructors.forEach { constructor ->
            env.hookAfter(constructor) { param ->
                if (!ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) return@hookAfter
                (param.thisObject as? View)?.let { view ->
                    view.visibility = View.GONE
                    hideViewPhysically(view)
                }
            }
        }

        widgetClass.declaredMethods
            .filter { it.name == "setVisibility" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
            .forEach { method ->
                env.hookBefore(method) { param ->
                    if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                        param.args[0] = View.GONE
                    }
                }
            }

        widgetClass.declaredMethods
            .filter {
                !Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.isEmpty() &&
                    (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java) &&
                    PlayerPortraitControlPolicy.isSafeLegacyWidgetBooleanMethod(it.name)
            }
            .forEach { method ->
                env.hookBefore(method) { param ->
                    if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                        param.result = false
                    }
                }
            }
    }

    /**
     * 针对 9.17.0+ / 9.18.0 等 Theseus2 Compose 架构的看一看与竖屏控制项。
     * 通过 StoryEntranceService 的类型签名动态识别状态与入口方法，避免硬编码单字母混淆名导致崩溃。
     */
    private fun installComposeStoryHook() {
        val serviceClass = STORY_ENTRANCE_SERVICE_CANDIDATES
            .firstNotNullOfOrNull { classLoader.findClassOrNull(it) } ?: return

        discoverAndHookInitialConfigFromService(serviceClass)

        runCatching {
            serviceClass.declaredConstructors.forEach { constructor ->
                env.hookAfter(constructor) { param ->
                    val instance = param.thisObject ?: return@hookAfter
                    logStoryServiceSchemaOnce(instance.javaClass)
                    if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                        suppressStoryEntranceServiceStates(instance)
                    }
                }
            }
        }.onFailure {
            log("PlayerUi: installComposeStoryHook (StoryEntranceService ctor) failed", it)
        }

        hookStoryEntranceServiceMethods(serviceClass)
    }

    private fun discoverAndHookInitialConfigFromService(serviceClass: Class<*>) {
        val servicePkg = serviceClass.packageName
        val candidateTypes = LinkedHashSet<Class<*>>()
        serviceClass.declaredConstructors.forEach { ctor ->
            ctor.parameterTypes.filterTo(candidateTypes) { it.packageName == servicePkg }
        }
        serviceClass.declaredFields.forEach { field ->
            if (field.type.packageName == servicePkg) {
                candidateTypes.add(field.type)
            }
        }
        candidateTypes.forEach { candidate ->
            if (candidate == serviceClass || !hookedInitialClasses.add(candidate.name)) return@forEach
            candidate.declaredConstructors.forEach { ctor ->
                val paramNames = ctor.parameterTypes.map { it.name }
                if (PlayerPortraitControlPolicy.isStoryInitialConfigConstructor(paramNames)) {
                    env.hookBefore(ctor) { param ->
                        if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs) &&
                            param.args.size >= 2 &&
                            param.args[1] is Boolean
                        ) {
                            param.args[1] = false
                        }
                    }
                    log("PlayerUi: hooked StoryEntrance initial config ${candidate.name}(${paramNames.joinToString(",")})")
                }
            }
        }
    }

    private fun hookStoryEntranceServiceMethods(serviceClass: Class<*>) {
        runCatching {
            val function2Class = classLoader.findClassOrNull("kotlin.jvm.functions.Function2")
            var hookedCount = 0
            serviceClass.declaredMethods.forEach { method ->
                if (Modifier.isStatic(method.modifiers)) return@forEach
                val paramNames = method.parameterTypes.map { it.name }
                val returnName = method.returnType.name

                when {
                    PlayerPortraitControlPolicy.isComposableMethodSignature(returnName, paramNames) -> {
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.result = null
                            }
                        }
                        hookedCount++
                    }

                    PlayerPortraitControlPolicy.isStoryFunction2Method(method.name, returnName) &&
                        method.returnType.isInterface -> {
                        val noOp = function2Class?.let(::getOrCreateNoOpFunction2)
                        if (noOp != null && method.returnType.isInstance(noOp)) {
                            env.hookAfter(method) { param ->
                                if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs) && param.result != null) {
                                    param.result = noOp
                                }
                            }
                            hookedCount++
                        }
                    }

                    method.parameterTypes.isEmpty() &&
                        (method.returnType == java.lang.Boolean.TYPE || method.returnType == java.lang.Boolean::class.java) &&
                        PlayerPortraitControlPolicy.isStoryServiceBooleanMethod(method.name) -> {
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.result = false
                            }
                        }
                        hookedCount++
                    }
                }
            }
            if (hookedCount > 0) {
                log("PlayerUi: hooked $hookedCount StoryEntranceService method(s)")
            }
        }.onFailure {
            log("PlayerUi: installComposeStoryHook (methods) failed", it)
        }
    }

    private fun getOrCreateNoOpFunction2(function2Class: Class<*>): Any? {
        cachedNoOpFunction2?.let { return it }
        return runCatching {
            val unitClass = classLoader.findClassOrNull("kotlin.Unit") ?: return null
            val unitInstance = unitClass.getField("INSTANCE").get(null) ?: return null
            Proxy.newProxyInstance(function2Class.classLoader, arrayOf(function2Class)) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> unitInstance
                    "toString" -> "BBZQNoOpStoryUi"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }.also { cachedNoOpFunction2 = it }
        }.getOrNull()
    }

    private fun logStoryServiceSchemaOnce(serviceClass: Class<*>) {
        if (serviceSchemaLogged) return
        serviceSchemaLogged = true
        runCatching {
            val fields = serviceClass.declaredFields.joinToString(", ") { "${it.name}:${it.type.simpleName}" }
            val methods = serviceClass.declaredMethods.joinToString(", ") {
                "${it.name}(${it.parameterTypes.joinToString(",") { p -> p.simpleName }}):${it.returnType.simpleName}"
            }
            log("PlayerUi: StoryEntranceService schema fields=[$fields] methods=[$methods]")
        }
    }

    private fun suppressStoryEntranceServiceStates(instance: Any) {
        runCatching {
            instance.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                val fieldType = field.type
                if (fieldType == java.lang.Boolean.TYPE) {
                    if (PlayerPortraitControlPolicy.isSafeBooleanFieldToSuppress(field.name)) {
                        field.setBoolean(instance, false)
                    }
                } else if (isMutableStateContainerType(fieldType)) {
                    val stateObj = field.get(instance) ?: return@forEach
                    suppressBooleanStateContainer(stateObj)
                }
            }
        }.onFailure {
            log("PlayerUi: suppressStoryEntranceServiceStates failed", it)
        }
    }

    private fun isMutableStateContainerType(type: Class<*>): Boolean =
        type.name.contains("MutableState")

    private fun suppressBooleanStateContainer(stateObj: Any) {
        runCatching {
            val getValueMethod = stateObj.javaClass.methods.firstOrNull {
                it.name == "getValue" && it.parameterTypes.isEmpty()
            } ?: return
            val currentValue = getValueMethod.invoke(stateObj)
            if (currentValue !is Boolean) return

            val setValueMethod = stateObj.javaClass.methods.firstOrNull {
                it.name == "setValue" && it.parameterTypes.size == 1
            }
            if (currentValue) {
                setValueMethod?.invoke(stateObj, false)
            }

            trackedBooleanStates[stateObj] = true
            ensureStateContainerMutationHooked(stateObj.javaClass)
        }
    }

    private fun ensureStateContainerMutationHooked(stateClass: Class<*>) {
        if (!hookedStateClasses.add(stateClass.name)) return
        stateClass.methods
            .filter {
                (it.name == "setValue" && it.parameterTypes.size == 1) ||
                    (it.name == "tryEmit" && it.parameterTypes.size == 1) ||
                    (it.name == "emit" && it.parameterTypes.size == 2)
            }
            .forEach { method ->
                env.hookBefore(method) { param ->
                    val target = param.thisObject ?: return@hookBefore
                    if (trackedBooleanStates.containsKey(target) &&
                        ModuleSettings.isHidePlayerPortraitControlEnabled(prefs) &&
                        param.args.firstOrNull() is Boolean
                    ) {
                        param.args[0] = false
                    }
                }
            }
    }

    private fun schedulePlayerUiTuning(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        applyTuningInternal(activity, decor)
        installGlobalLayoutWatcher(activity, decor)
        CONTROL_RECHECK_DELAYS_MS.forEach { delay ->
            decor.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && decor.isAttachedToWindow) {
                    applyTuningInternal(activity, decor)
                }
            }, delay)
        }
    }

    private fun installGlobalLayoutWatcher(activity: Activity, decor: View) {
        synchronized(globalLayoutAttached) {
            if (globalLayoutAttached.containsKey(decor)) return
            globalLayoutAttached[decor] = true
        }
        val activityRef = WeakReference(activity)
        val decorRef = WeakReference(decor)
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val targetActivity = activityRef.get()
                val targetDecor = decorRef.get()
                if (targetActivity == null || targetDecor == null ||
                    targetActivity.isFinishing || targetActivity.isDestroyed ||
                    !targetDecor.isAttachedToWindow
                ) {
                    targetDecor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
                    return
                }
                applyTuningInternal(targetActivity, targetDecor)
            }
        }
        decor.viewTreeObserver.takeIf { it.isAlive }?.addOnGlobalLayoutListener(listener)
    }

    private fun applyTuningInternal(activity: Activity, decor: View) {
        val isStory = isStoryActivity(activity)

        // 隐藏特定“转短视频流/看一看”跳转按键
        if (!isStory && ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
            revealPortraitControls(decor)
        }
    }

    private fun revealPortraitControls(view: View) {
        if (isPortraitControl(view)) {
            if (view.visibility != View.GONE) {
                view.visibility = View.GONE
            }
            hideViewPhysically(view)
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index) ?: continue
                revealPortraitControls(child)
            }
        }
    }

    private fun hideViewPhysically(view: View) {
        view.layoutParams?.let { lp ->
            if (lp.width != 0 || lp.height != 0) {
                lp.width = 0
                lp.height = 0
                view.layoutParams = lp
            }
        }
        view.isClickable = false
        view.setOnClickListener(null)
    }

    private fun isPortraitControl(view: View): Boolean {
        if (view === view.rootView) return false
        val className = view.javaClass.name
        if (PORTRAIT_CLASS_MARKERS.any { className.contains(it, ignoreCase = true) }) {
            return true
        }

        if (view.id != View.NO_ID) {
            val entry = runCatching { view.resources.getResourceEntryName(view.id) }
                .getOrNull()?.lowercase()
            if (entry != null && EXACT_PORTRAIT_IDS.contains(entry)) {
                return true
            }
        }

        if (view is ViewGroup) {
            if (PlayerPortraitControlPolicy.isProtectedContainerClassName(className)) return false
            if (view.childCount > 3 || (view.width > 300 && view.height > 300)) return false
        }

        val description = view.contentDescription?.toString().orEmpty()
        if (description.isNotBlank() && PORTRAIT_DESCRIPTION_MARKERS.any { description.contains(it, ignoreCase = true) }) {
            return true
        }

        val text = (view as? TextView)?.text?.toString().orEmpty()
        if (text.isNotBlank() && PORTRAIT_DESCRIPTION_MARKERS.any { text.contains(it, ignoreCase = true) }) {
            return true
        }
        return false
    }

    private fun isStoryActivity(activity: Activity?): Boolean {
        if (activity == null) return false
        val name = activity.javaClass.name
        return name.contains("Story", ignoreCase = true)
    }

    private fun isVideoDetailActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return (name.contains("VideoDetail", ignoreCase = true) ||
            name.contains("DetailActivity", ignoreCase = true) ||
            name.contains("UnitedBizDetailsActivity", ignoreCase = true)) &&
            !name.contains("Story", ignoreCase = true)
    }

    private fun isPotentialPlayerActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return isVideoDetailActivity(activity) ||
            isStoryActivity(activity) ||
            name.contains("Player", ignoreCase = true) ||
            name.contains("Bangumi", ignoreCase = true)
    }

    private companion object {
        private val CONTROL_RECHECK_DELAYS_MS = longArrayOf(50L, 200L, 500L, 1_000L, 2_000L, 3_500L)
        private val LEGACY_STORY_WIDGET_CLASSES = listOf(
            "com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullStoryWidget",
            "com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullscreenWidget",
        )
        private val STORY_ENTRANCE_SERVICE_CANDIDATES = listOf(
            "com.bilibili.ship.theseus2.united.page.story.StoryEntranceService",
            "com.bilibili.p5797ship.theseus2.united.p5850page.story.StoryEntranceService",
        )
        private val PORTRAIT_CLASS_MARKERS = listOf(
            "FullStoryWidget",
            "GeminiPlayerFullStoryWidget",
            "PlayerFullStory",
        )
        private val PORTRAIT_DESCRIPTION_MARKERS = listOf(
            "进入看一看",
            "看一看",
            "竖屏模式",
            "展开竖屏",
            "切换竖屏",
            "切为竖屏",
            "竖屏全屏",
            "展开全屏",
        )
        private val EXACT_PORTRAIT_IDS = setOf(
            "bbplayer_halfscreen_story",
            "gemini_halfscreen_story",
            "preloading_landscape_portrait_toggle",
            "story_ctrl_screen",
            "story_fullscreen",
            "outside_portrait",
            "portrait_fullscreen",
            "ver_fullscreen",
        )
    }
}
