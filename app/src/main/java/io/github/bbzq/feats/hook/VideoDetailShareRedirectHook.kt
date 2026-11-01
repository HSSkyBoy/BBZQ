package io.github.bbzq.feats.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookBefore

/**
 * Redirects the video detail page share button click events to the toolbar overflow (more) menu.
 *
 * The share button region in Bilibili's video details layout consists of:
 * - Outer row container: LinearLayout (`@id/frame_share`)
 * - Inner icon: `DetailsShareAnimViewV2` (`@id/share_icon`)
 *
 * This hook replaces their OnClickListener with one that locates `@id/toolbar_action_overflow`
 * and triggers `performClick()`, falling back to the original share action if the feature is disabled
 * or if the overflow view cannot be found.
 */
class VideoDetailShareRedirectHook(env: RoamingEnv) : BaseRoamingHook(env) {

    private var frameShareId: Int = 0
    private var shareIconId: Int = 0
    private var overflowId: Int = 0

    override fun startHook() {
        if (env.processName != env.packageName) return

        runCatching {
            val setOnClickListener = View::class.java.getDeclaredMethod(
                "setOnClickListener",
                View.OnClickListener::class.java,
            )
            env.hookBefore(setOnClickListener) { param ->
                val view = param.thisObject as? View ?: return@hookBefore
                val originalListener = param.args.firstOrNull() as? View.OnClickListener ?: return@hookBefore
                if (originalListener is RedirectClickListener) return@hookBefore

                if (!isTargetShareView(view)) return@hookBefore

                param.args[0] = RedirectClickListener(originalListener)
            }
            isInstalled = true
            log("startHook: VideoDetailShareRedirectHook installed")
        }.onFailure { error ->
            log("startHook: Failed to install VideoDetailShareRedirectHook", error)
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
    }
}
