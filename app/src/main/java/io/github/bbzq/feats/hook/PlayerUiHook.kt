package io.github.bbzq.feats.hook

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier

class PlayerUiHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var currentVideoDetailActivity: java.lang.ref.WeakReference<Activity>? = null

    override fun startHook() {
        if (env.processName != env.packageName) return
        val application = env.hostContext as? Application ?: return

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (isVideoDetailActivity(activity) || isStoryActivity(activity)) {
                    currentVideoDetailActivity = java.lang.ref.WeakReference(activity)
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
        // 1. GeminiPlayerFullStoryWidget: 橫屏短視頻/看一看入口
        runCatching {
            val storyWidgetClass = classLoader.findClassOrNull("com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullStoryWidget")
            if (storyWidgetClass != null) {
                // Hook 構造函數，實例化時即刻隱藏並物理收縮
                storyWidgetClass.declaredConstructors.forEach { constructor ->
                    env.hookAfter(constructor) { param ->
                        if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                            (param.thisObject as? View)?.let { view ->
                                view.visibility = View.GONE
                                hideViewPhysically(view)
                            }
                        }
                    }
                }

                // Hook 所有返回 Boolean 的無參方法（如判斷是否允許顯示 story 的開關 m91790H 等）
                storyWidgetClass.declaredMethods
                    .filter {
                        !Modifier.isStatic(it.modifiers) &&
                            it.parameterTypes.isEmpty() &&
                            (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java)
                    }
                    .forEach { method ->
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.result = false
                            }
                        }
                    }
            }
        }.onFailure {
            log("PlayerUi: installLegacyStoryWidgetHook (FullStory) failed", it)
        }

        // 2. GeminiPlayerFullscreenWidget: 豎屏模式下全屏轉 Story 入口
        runCatching {
            val fullscreenWidgetClass = classLoader.findClassOrNull("com.bilibili.app.gemini.player.widget.story.GeminiPlayerFullscreenWidget")
            if (fullscreenWidgetClass != null) {
                fullscreenWidgetClass.declaredMethods
                    .filter {
                        !Modifier.isStatic(it.modifiers) &&
                            it.parameterTypes.isEmpty() &&
                            (it.returnType == java.lang.Boolean.TYPE || it.returnType == java.lang.Boolean::class.java)
                    }
                    .forEach { method ->
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.result = false
                            }
                        }
                    }
            }
        }.onFailure {
            log("PlayerUi: installLegacyStoryWidgetHook (FullscreenWidget) failed", it)
        }
    }

    /**
     * 針對 9.17.0+ / 9.18.0 等 Theseus2 Compose 架構的看一看與豎屏控制項
     */
    private fun installComposeStoryHook() {
        // 1. Hook TheseusStoryEntranceInitial 構造函數 (Long?, boolean)
        // 將 playerStoryControlsEnabled (args[1]) 強制置為 false
        runCatching {
            val initialClass = classLoader.findClassOrNull("com.bilibili.ship.theseus2.united.page.story.S")
            if (initialClass != null) {
                initialClass.declaredConstructors.forEach { constructor ->
                    val paramTypes = constructor.parameterTypes
                    if (paramTypes.size >= 2 && (paramTypes[1] == java.lang.Boolean.TYPE || paramTypes[1] == java.lang.Boolean::class.java)) {
                        env.hookBefore(constructor) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.args[1] = false
                            }
                        }
                    }
                }
            }
        }.onFailure {
            log("PlayerUi: installComposeStoryHook (Initial) failed", it)
        }

        // 2. Hook StoryEntranceService:
        // 在其構造完成後，將內部可見性狀態 MutableState 與 boolean 開關全數壓制為 false
        runCatching {
            val serviceClass = classLoader.findClassOrNull("com.bilibili.ship.theseus2.united.page.story.StoryEntranceService")
            if (serviceClass != null) {
                serviceClass.declaredConstructors.forEach { constructor ->
                    env.hookAfter(constructor) { param ->
                        if (!ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) return@hookAfter
                        val instance = param.thisObject ?: return@hookAfter
                        suppressStoryEntranceServiceStates(instance)
                    }
                }
            }
        }.onFailure {
            log("PlayerUi: installComposeStoryHook (StoryEntranceService) failed", it)
        }

        // 3. Hook 所有依賴 StoryEntranceService 的 Function0 / Composable Lambda 提供者
        // 當 Compose 控制容器查詢 storyControlUi / fullscreenControlUi 時直接返回 null
        runCatching {
            val lambdaClasses = listOf(
                "com.bilibili.ship.theseus2.united.page.story.e",
                "com.bilibili.ship.theseus2.united.page.story.f",
                "com.bilibili.ship.theseus2.united.page.story.i",
                "com.bilibili.ship.theseus2.united.page.story.j",
            )
            lambdaClasses.forEach { className ->
                val clazz = classLoader.findClassOrNull(className) ?: return@forEach
                clazz.declaredMethods
                    .filter { it.name == "invoke" }
                    .forEach { method ->
                        env.hookBefore(method) { param ->
                            if (ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                                param.result = null
                            }
                        }
                    }
            }
        }.onFailure {
            log("PlayerUi: installComposeStoryHook (Lambdas) failed", it)
        }
    }

    private fun suppressStoryEntranceServiceStates(instance: Any) {
        runCatching {
            instance.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                val fieldType = field.type
                if (fieldType == java.lang.Boolean.TYPE) {
                    field.setBoolean(instance, false)
                } else if (fieldType.name.contains("MutableState")) {
                    val stateObj = field.get(instance) ?: return@forEach
                    runCatching {
                        val setValueMethod = stateObj.javaClass.methods.firstOrNull {
                            it.name == "setValue" && it.parameterTypes.size == 1
                        }
                        setValueMethod?.invoke(stateObj, false)
                    }
                }
            }
        }
    }

    private fun schedulePlayerUiTuning(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        applyTuningInternal(activity, decor)
        installViewHierarchyListener(decor)
        CONTROL_RECHECK_DELAYS_MS.forEach { delay ->
            decor.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed && decor.isAttachedToWindow) {
                    applyTuningInternal(activity, decor)
                }
            }, delay)
        }
    }

    private fun installViewHierarchyListener(root: View) {
        if (root !is ViewGroup) return
        root.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                if (child != null && ModuleSettings.isHidePlayerPortraitControlEnabled(prefs)) {
                    revealPortraitControls(child)
                    if (child is ViewGroup) {
                        installViewHierarchyListener(child)
                    }
                }
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        })
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
        val className = view.javaClass.name
        if (PORTRAIT_CLASS_MARKERS.any { className.contains(it, ignoreCase = true) }) {
            return true
        }

        if (view is ViewGroup) {
            if (className.startsWith("android.view.")) return false
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

        if (view.id != View.NO_ID) {
            val entry = runCatching { view.resources.getResourceEntryName(view.id) }
                .getOrNull()?.lowercase() ?: return false

            if (EXACT_PORTRAIT_IDS.contains(entry)) {
                return true
            }
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
