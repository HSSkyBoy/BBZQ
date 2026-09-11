package io.github.bbzq.feats.hook

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.from
import io.github.bbzq.feats.hookAfterMethod
import java.util.ArrayDeque
import java.util.WeakHashMap

class DynamicPageHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val activeSweepRoots = WeakHashMap<View, Boolean>()

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (
            !ModuleSettings.isDynamicPreferredVideoTabEnabled(prefs) &&
            !ModuleSettings.isDynamicRemoveCityTabEnabled(prefs) &&
            !ModuleSettings.isDynamicRemoveSchoolTabEnabled(prefs)
        ) {
            log("startHook: DynamicPage disabled")
            return
        }

        env.hookAfterMethod(Activity::class.java, "onResume") { param ->
            val activity = param.thisObject as? Activity ?: return@hookAfterMethod
            val name = activity.javaClass.name
            if (!isDynamicActivity(name)) return@hookAfterMethod
            val window = activity.window ?: return@hookAfterMethod
            val decor = runCatching { window.peekDecorView() ?: window.decorView }.getOrNull() ?: return@hookAfterMethod
            scheduleSweep(activity, decor, name)
        }
        val fragmentClass = ANDROIDX_FRAGMENT_CLASS.from(classLoader)
        val fragmentHookCount = fragmentClass?.let { type ->
            env.hookAfterMethod(type, "onViewCreated", View::class.java, Bundle::class.java) { param ->
                val fragment = param.thisObject ?: return@hookAfterMethod
                val view = param.args.getOrNull(0) as? View ?: return@hookAfterMethod
                val activity = fragment.callMethod("getActivity") as? Activity ?: return@hookAfterMethod
                val ownerName = fragment.javaClass.name
                if (!isDynamicFragmentOwner(ownerName) && !isDynamicCandidate(activity, view, ownerName)) {
                    return@hookAfterMethod
                }
                scheduleSweep(activity, view, ownerName)
            }
        } ?: 0
        if (fragmentHookCount == 0) {
            log("DynamicPage Fragment.onViewCreated hook unavailable")
        }

        log("startHook: DynamicPage Activity.onResume + Fragment.onViewCreated")
    }

    private fun scheduleSweep(activity: Activity, root: View, ownerName: String) {
        SWEEP_DELAYS_MS.forEach { delay ->
            root.postDelayed({
                runCatching {
                    sweep(activity, root, ownerName)
                }.onFailure {
                    log("DynamicPage sweep failed for ${activity.javaClass.name}", it)
                }
            }, delay)
        }
        installTemporaryLayoutSweep(activity, root, ownerName)
    }

    private fun installTemporaryLayoutSweep(activity: Activity, root: View, ownerName: String) {
        if (activeSweepRoots.put(root, true) != null) return

        val startedAt = SystemClock.uptimeMillis()
        var lastSweepAt = 0L
        var removed = false
        lateinit var listener: ViewTreeObserver.OnGlobalLayoutListener

        fun removeListener() {
            if (removed) return
            removed = true
            runCatching {
                val observer = root.viewTreeObserver
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
            }
            activeSweepRoots.remove(root)
        }

        listener = ViewTreeObserver.OnGlobalLayoutListener {
            val now = SystemClock.uptimeMillis()
            if (now - startedAt > GLOBAL_SWEEP_WINDOW_MS) {
                removeListener()
                return@OnGlobalLayoutListener
            }
            if (now - lastSweepAt < GLOBAL_SWEEP_THROTTLE_MS) return@OnGlobalLayoutListener
            lastSweepAt = now
            runCatching {
                sweep(activity, root, ownerName)
            }.onFailure {
                log("DynamicPage layout sweep failed for ${activity.javaClass.name}", it)
            }
        }

        val observer = root.viewTreeObserver
        if (observer.isAlive) {
            observer.addOnGlobalLayoutListener(listener)
            root.postDelayed(::removeListener, GLOBAL_SWEEP_WINDOW_MS)
        } else {
            activeSweepRoots.remove(root)
        }
    }

    private fun sweep(activity: Activity, root: View, ownerName: String): Boolean {
        if (!isDynamicCandidate(activity, root, ownerName)) return false

        var changed = false
        if (ModuleSettings.isDynamicPreferredVideoTabEnabled(prefs)) {
            changed = clickPreferredVideoTab(root) || changed
        }
        if (ModuleSettings.isDynamicRemoveCityTabEnabled(prefs)) {
            changed = hideMatchingTab(root, CITY_TAB_LABELS) || changed
        }
        if (ModuleSettings.isDynamicRemoveSchoolTabEnabled(prefs)) {
            changed = hideMatchingTab(root, SCHOOL_TAB_LABELS) || changed
        }

        if (changed) {
            log("DynamicPage updated for ${activity.javaClass.name}")
        }
        return changed
    }

    private fun isDynamicCandidate(activity: Activity, root: View, ownerName: String): Boolean {
        if (isDynamicFragmentOwner(ownerName)) return true
        return findMatchingTextView(root, CITY_TAB_LABELS) != null ||
            findMatchingTextView(root, SCHOOL_TAB_LABELS) != null
    }

    private fun clickPreferredVideoTab(root: View): Boolean {
        val videoTab = findMatchingTextView(root, VIDEO_TAB_LABELS) ?: return false
        if (isSelectedOrActivated(videoTab)) return false
        return performClick(videoTab)
    }

    private fun hideMatchingTab(root: View, labels: Set<String>): Boolean {
        val matches = findAllMatchingTextViews(root, labels)
        var changed = false
        matches.forEach { view ->
            val target = resolveHideTarget(view)
            if (target.visibility != View.GONE) {
                target.visibility = View.GONE
                changed = true
            }
        }
        return changed
    }

    private fun resolveHideTarget(view: View): View {
        findClickableAncestor(view)?.let { return it }
        val parent = view.parent as? View
        if (parent == null || parent === view) return view
        if (parent is ViewGroup && parent.childCount <= 3) return parent
        return view
    }

    private fun findMatchingTextView(root: View, labels: Set<String>): TextView? =
        findAllMatchingTextViews(root, labels).firstOrNull()

    private fun findAllMatchingTextViews(root: View, labels: Set<String>): List<TextView> {
        val out = ArrayList<TextView>()
        val queue = ArrayDeque<View>()
        queue += root
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_VIEW_SCAN_NODES) {
            val view = queue.removeFirst()
            visited += 1

            if (view is TextView && view.isShown && matchesAnyLabel(view.text, labels)) {
                out += view
            }

            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue += view.getChildAt(index)
                }
            }
        }

        return out
    }

    private fun matchesAnyLabel(rawText: CharSequence?, labels: Set<String>): Boolean {
        val normalized = normalizeText(rawText)
        if (normalized.isEmpty()) return false
        return labels.any { label ->
            normalized == label || normalized.startsWith(label)
        }
    }

    private fun normalizeText(rawText: CharSequence?): String =
        rawText?.toString()
            ?.replace("\u3000", " ")
            ?.replace("\n", "")
            ?.replace("\r", "")
            ?.replace("\t", "")
            ?.replace(" ", "")
            ?.trim()
            .orEmpty()

    private fun performClick(view: View): Boolean {
        val target = findClickableAncestor(view) ?: return false
        return target.performClick()
    }

    private fun findClickableAncestor(view: View): View? {
        var current: View? = view
        var depth = 0
        while (current != null && depth <= MAX_PARENT_DEPTH) {
            if (
                current.isEnabled &&
                current.isShown &&
                (current.isClickable || current.hasOnClickListeners())
            ) {
                return current
            }
            current = current.parent as? View
            depth += 1
        }
        return null
    }

    private fun isSelectedOrActivated(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth <= MAX_PARENT_DEPTH) {
            if (current.isSelected || current.isActivated) return true
            current = current.parent as? View
            depth += 1
        }
        return false
    }

    private fun isDynamicFragmentOwner(rawName: String): Boolean =
        matchesOwner(rawName, DYNAMIC_FRAGMENT_KEYWORDS)

    private fun matchesOwner(rawName: String, keywords: List<String>): Boolean {
        val className = rawName.lowercase()
        return keywords.any(className::contains)
    }

    private fun isDynamicActivity(name: String): Boolean =
        name.contains("MainActivity") || name.contains("Dynamic") || name.contains("Main2")

    private companion object {
        private val SWEEP_DELAYS_MS = longArrayOf(0L, 120L, 300L, 700L, 1500L, 3000L)
        private const val GLOBAL_SWEEP_WINDOW_MS = 4000L
        private const val GLOBAL_SWEEP_THROTTLE_MS = 120L
        private const val MAX_VIEW_SCAN_NODES = 1500
        private const val MAX_PARENT_DEPTH = 8
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private val DYNAMIC_FRAGMENT_KEYWORDS = listOf(
            "followinglist.home.mediator",
            "mediatorfragment",
            "followinglist",
        )
        private val VIDEO_TAB_LABELS = setOf("视频")
        private val CITY_TAB_LABELS = setOf("同城")
        private val SCHOOL_TAB_LABELS = setOf("校园")
    }
}
