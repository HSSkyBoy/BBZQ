package io.github.bbzq.feats.hook.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfterAllConstructors
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/**
 * Redirects the video detail page share button click events to the toolbar overflow (more) menu.
 *
 * The share button region in Bilibili's video details layout consists of:
 * - Outer row container: LinearLayout (`@id/frame_share`)
 * - Inner icon: `DetailsShareAnimViewV2` (`@id/share_icon`)
 * - Bilibili 9.20+ (`theseus2` Compose): `UgcKingPositionService` / `OgvKingPositionShareService` -> `ToolbarRepository`
 *
 * This hook replaces their OnClickListener (or intercepts `ShareLayerSource.Default` in half-screen)
 * to open the toolbar overflow menu, falling back to the original share action if the feature is disabled
 * or if the overflow entry cannot be found.
 */
class VideoDetailShareRedirectHook(env: RoamingEnv) : BaseRoamingHook(env) {

    private var frameShareId: Int = 0
    private var shareIconId: Int = 0
    private var overflowId: Int = 0
    private val toolbarRepositories = Collections.synchronizedMap(WeakHashMap<Any, Long>())

    override fun startHook() {
        if (env.processName != env.packageName) return

        var installedCount = 0
        runCatching {
            val setOnClickListener = View::class.java.getDeclaredMethod(
                "setOnClickListener",
                View.OnClickListener::class.java,
            )
            if (env.hookBefore(setOnClickListener) { param ->
                    val view = param.thisObject as? View ?: return@hookBefore
                    val originalListener = param.args.firstOrNull() as? View.OnClickListener ?: return@hookBefore
                    if (originalListener is RedirectClickListener) return@hookBefore

                    if (!isTargetShareView(view)) return@hookBefore

                    param.args[0] = RedirectClickListener(originalListener)
                }
            ) {
                installedCount++
            }
        }.onFailure { error ->
            log("startHook: Failed to install View share redirect hook", error)
        }

        installedCount += installTheseus2ShareRedirect()
        if (installedCount > 0) {
            isInstalled = true
            log("startHook: VideoDetailShareRedirectHook installed (points=$installedCount)")
        }
    }

    private fun installTheseus2ShareRedirect(): Int {
        val toolbarClass = classLoader.findClassOrNull(THESEUS2_TOOLBAR_REPOSITORY_CLASS) ?: return 0
        val shareSourceClass = classLoader.findClassOrNull(THESEUS2_SHARE_LAYER_SOURCE_CLASS) ?: return 0
        val openOverflowMethod = toolbarClass.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(String::class.java))
        }?.apply { isAccessible = true } ?: return 0

        val vertexContextClass = classLoader.findClassOrNull(VERTEX_CONTEXT_CLASS)
        val toolbarVertexField = vertexContextClass?.let { vcType ->
            toolbarClass.declaredFields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.type == vcType }
                ?.apply { isAccessible = true }
        }
        val slotField = toolbarClass.declaredFields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                runCatching {
                    val types = field.type.declaredFields.map { it.type.name }
                    "kotlinx.coroutines.CoroutineScope" in types &&
                        "kotlinx.coroutines.Job" in types &&
                        "kotlinx.coroutines.flow.MutableStateFlow" in types
                }.getOrDefault(false)
        }?.apply { isAccessible = true }
        val slotActionField = slotField?.type?.declaredFields?.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name !in SLOT_INFRA_FIELD_TYPES
        }?.apply { isAccessible = true }

        val count = env.hookAfterAllConstructors(toolbarClass) { param ->
            param.thisObject?.let { toolbarRepositories[it] = System.nanoTime() }
        }

        var shareHooks = 0
        THESEUS2_KING_POSITION_SHARE_SERVICES.forEach { serviceName ->
            val serviceClass = classLoader.findClassOrNull(serviceName) ?: return@forEach
            val shareMethod = serviceClass.declaredMethods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(shareSourceClass))
            } ?: return@forEach
            val serviceVertexField = vertexContextClass?.let { vcType ->
                serviceClass.declaredFields.firstOrNull { !Modifier.isStatic(it.modifiers) && it.type == vcType }
                    ?.apply { isAccessible = true }
            }
            val screenStateAccessor = resolveScreenStatePlayerModeReader(serviceClass)
            if (env.hookBefore(shareMethod) { param ->
                    if (!ModuleSettings.isShareRedirectToOverflowEnabled(prefs)) return@hookBefore
                    val sourceName = (param.args.firstOrNull() as? Enum<*>)?.name ?: return@hookBefore
                    if (sourceName != "Default") return@hookBefore
                    val service = param.thisObject ?: return@hookBefore
                    if (screenStateAccessor?.invoke(service) == "Fullscreen") return@hookBefore
                    val targetToolbar = findReadyToolbarRepository(
                        service = service,
                        serviceVertexField = serviceVertexField,
                        toolbarVertexField = toolbarVertexField,
                        slotField = slotField,
                        slotActionField = slotActionField,
                    ) ?: return@hookBefore
                    runCatching {
                        openOverflowMethod.invoke(targetToolbar, "")
                        param.result = null
                        log("Redirected theseus2 share click to ToolbarRepository overflow menu")
                    }.onFailure { error ->
                        log("Failed to invoke ToolbarRepository overflow menu, falling back to share", error)
                    }
                }
            ) {
                shareHooks++
            }
        }
        return if (shareHooks > 0) count + shareHooks else 0
    }

    private fun findReadyToolbarRepository(
        service: Any,
        serviceVertexField: Field?,
        toolbarVertexField: Field?,
        slotField: Field?,
        slotActionField: Field?,
    ): Any? {
        val snapshot = synchronized(toolbarRepositories) {
            toolbarRepositories.entries.map { it.key to it.value }
        }.sortedByDescending { it.second }.map { it.first }
        if (snapshot.isEmpty()) return null

        fun isReady(repo: Any): Boolean {
            if (slotField == null || slotActionField == null) return true
            val slot = runCatching { slotField.get(repo) }.getOrNull() ?: return false
            return runCatching { slotActionField.get(slot) != null }.getOrDefault(false)
        }

        val serviceVertex = serviceVertexField?.let { runCatching { it.get(service) }.getOrNull() }
        if (serviceVertex != null && toolbarVertexField != null) {
            snapshot.firstOrNull { repo ->
                runCatching { toolbarVertexField.get(repo) === serviceVertex }.getOrDefault(false) && isReady(repo)
            }?.let { return it }
        }
        return snapshot.firstOrNull(::isReady)
    }

    private fun resolveScreenStatePlayerModeReader(ownerClass: Class<*>): ((Any) -> String?)? {
        val playerModeClass = classLoader.findClassOrNull(THESEUS2_PLAYER_MODE_CLASS) ?: return null
        val repoField = ownerClass.declaredFields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                field.type.name.startsWith("com.bilibili.ship.theseus2.united.page.screenstate.")
        }?.apply { isAccessible = true } ?: return null
        val stateGetter: Method = repoField.type.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                runCatching { method.returnType.declaredFields.any { it.type == playerModeClass } }.getOrDefault(false)
        }?.apply { isAccessible = true } ?: return null
        val modeField = stateGetter.returnType.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == playerModeClass
        }?.apply { isAccessible = true } ?: return null
        return { owner ->
            runCatching {
                val repo = repoField.get(owner) ?: return@runCatching null
                val state = stateGetter.invoke(repo) ?: return@runCatching null
                (modeField.get(state) as? Enum<*>)?.name
            }.getOrNull()
        }
    }

    private fun isTargetShareView(view: View): Boolean {
        ensureIdsResolved(view.context)
        val id = view.id
        if (id != View.NO_ID) {
            if ((frameShareId != 0 && id == frameShareId) || (shareIconId != 0 && id == shareIconId)) {
                return true
            }
        }
        val className = view.javaClass.name
        if (className == TARGET_SHARE_ANIM_VIEW_CLASS || className.endsWith("DetailsShareAnimViewV2")) {
            return true
        }
        return false
    }

    private fun ensureIdsResolved(context: Context) {
        if (frameShareId == 0) {
            frameShareId = context.resources.getIdentifier("frame_share", "id", context.packageName)
                .takeIf { it != 0 } ?: FALLBACK_FRAME_SHARE_ID
        }
        if (shareIconId == 0) {
            shareIconId = context.resources.getIdentifier("share_icon", "id", context.packageName)
                .takeIf { it != 0 } ?: FALLBACK_SHARE_ICON_ID
        }
        if (overflowId == 0) {
            overflowId = context.resources.getIdentifier("toolbar_action_overflow", "id", context.packageName)
                .takeIf { it != 0 } ?: FALLBACK_OVERFLOW_ID
        }
    }

    private fun findOverflowView(view: View): View? {
        val targetId = overflowId
        if (targetId == 0) return null

        val activity = findActivity(view.context)
        val fromActivity = activity?.findViewById<View>(targetId)
        if (fromActivity != null) return fromActivity

        val fromDecor = activity?.window?.peekDecorView()?.findViewById<View>(targetId)
        if (fromDecor != null) return fromDecor

        return view.rootView?.findViewById(targetId)
    }

    private tailrec fun findActivity(context: Context?): Activity? =
        when (context) {
            is Activity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }

    private inner class RedirectClickListener(
        private val original: View.OnClickListener?,
    ) : View.OnClickListener {
        override fun onClick(v: View) {
            if (!ModuleSettings.isShareRedirectToOverflowEnabled(prefs)) {
                original?.onClick(v)
                return
            }
            ensureIdsResolved(v.context)
            val overflowView = findOverflowView(v)
            if (overflowView != null) {
                val handled = overflowView.performClick()
                log("Redirected share click to overflow menu: handled=$handled")
            } else {
                log("Overflow menu view not found, falling back to original share listener")
                original?.onClick(v)
            }
        }
    }

    companion object {
        const val TARGET_SHARE_ANIM_VIEW_CLASS = "com.bilibili.ship.theseus.united.widget.DetailsShareAnimViewV2"
        const val FALLBACK_FRAME_SHARE_ID = 0x7f09186b
        const val FALLBACK_SHARE_ICON_ID = 0x7f094067
        const val FALLBACK_OVERFLOW_ID = 0x7f09472c

        private const val VERTEX_CONTEXT_CLASS = "kntr.common.trio.vertex.VertexContext"
        private const val THESEUS2_TOOLBAR_REPOSITORY_CLASS =
            "com.bilibili.ship.theseus2.united.page.toolbar.ToolbarRepository"
        private const val THESEUS2_SHARE_LAYER_SOURCE_CLASS =
            "com.bilibili.ship.theseus2.united.page.autofloat.ShareLayerSource"
        private const val THESEUS2_PLAYER_MODE_CLASS =
            "com.bilibili.ship.theseus2.united.page.screenstate.PlayerMode"
        private val THESEUS2_KING_POSITION_SHARE_SERVICES = listOf(
            "com.bilibili.ship.theseus2.ugc.intro.kingposition.UgcKingPositionService",
            "com.bilibili.ship.theseus2.ogv.intro.kingposition.OgvKingPositionShareService",
        )
        private val SLOT_INFRA_FIELD_TYPES = setOf(
            "kotlinx.coroutines.CoroutineScope",
            "kotlinx.coroutines.Job",
            "kotlinx.coroutines.flow.MutableStateFlow",
        )
    }
}
