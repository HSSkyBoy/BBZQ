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
import java.util.Locale
import java.util.WeakHashMap

class DynamicPageHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val activeSweepRoots = WeakHashMap<View, Boolean>()
    private val lastClickAtByActivity = WeakHashMap<Activity, Long>()

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
            changed = clickPreferredVideoTab(activity, root) || changed
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

    /**
     * 页面级判定：只要页面里出现过标签文案就算动态页，用于在标签栏尚未布局完成时也能继续尝试。
     * 真正的点击/隐藏一律走 [findTabView]，避免在动态列表正文里误命中。
     */
    private fun isDynamicCandidate(activity: Activity, root: View, ownerName: String): Boolean {
        if (isDynamicFragmentOwner(ownerName)) return true
        return findLooseTextView(root, CITY_TAB_LABELS) != null ||
            findLooseTextView(root, SCHOOL_TAB_LABELS) != null
    }

    private fun clickPreferredVideoTab(activity: Activity, root: View): Boolean {
        val now = SystemClock.uptimeMillis()
        // 布局监听会在 4 秒窗口内反复触发，必须限流，否则会连点。
        if (now - lastTabClickAt < TAB_CLICK_COOLDOWN_MS) return false
        val lastForActivity = lastClickAtByActivity[activity] ?: 0L
        if (now - lastForActivity < TAB_CLICK_ACTIVITY_COOLDOWN_MS) return false

        val videoTab = findTabView(root, VIDEO_TAB_LABELS) ?: return false
        if (isSelectedOrActivated(videoTab)) return false
        val target = findClickableAncestor(videoTab) ?: return false
        // 标签栏不会挂在列表容器内，命中列表项说明扫错了目标，直接放行。
        if (hasListLikeAncestor(target)) return false
        if (!target.performClick()) return false

        lastTabClickAt = now
        lastClickAtByActivity[activity] = now
        return true
    }

    private fun hideMatchingTab(root: View, labels: Set<String>): Boolean {
        val matches = findAllTabViews(root, labels)
        var changed = false
        matches.forEach { view ->
            val target = resolveHideTarget(view)
            if (hasListLikeAncestor(target)) return@forEach
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

    private fun findTabView(root: View, labels: Set<String>): TextView? =
        findAllTabViews(root, labels).firstOrNull()

    private fun findAllTabViews(root: View, labels: Set<String>): List<TextView> =
        findAllMatchingTextViews(root, labels, MAX_TAB_SCAN_DEPTH).filter(::isTabTextView)

    private fun findLooseTextView(root: View, labels: Set<String>): TextView? =
        findAllMatchingTextViews(root, labels, MAX_PAGE_SCAN_DEPTH).firstOrNull()

    private fun findAllMatchingTextViews(root: View, labels: Set<String>, maxDepth: Int): List<TextView> {
        val out = ArrayList<TextView>()
        val queue = ArrayDeque<Pair<View, Int>>()
        queue += root to 0
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_VIEW_SCAN_NODES) {
            val (view, depth) = queue.removeFirst()
            visited += 1

            if (view is TextView && view.isShown && matchesAnyLabel(view.text, labels)) {
                out += view
            }

            if (view is ViewGroup && depth < maxDepth) {
                for (index in 0 until view.childCount) {
                    queue += view.getChildAt(index) to depth + 1
                }
            }
        }

        return out
    }

    /**
     * 标签栏校验：文案相同的 TextView 在动态正文里非常常见（例如正文含"视频"），
     * 必须确认它确实位于标签栏中，否则会把整条动态当成标签点开。
     */
    private fun isTabTextView(view: View): Boolean {
        if (hasTabAncestor(view)) return true
        return hasSiblingTabLabel(view)
    }

    private fun hasTabAncestor(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth <= MAX_PARENT_DEPTH) {
            val name = current.javaClass.name.lowercase(Locale.ROOT)
            if (name.contains("tab") || name.contains("indicator") || name.contains("segment")) return true
            current = current.parent as? View
            depth += 1
        }
        return false
    }

    private fun hasSiblingTabLabel(view: View): Boolean {
        val parent = view.parent as? ViewGroup ?: return false
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child === view) continue
            val text = (child as? TextView)?.text ?: findFirstTextView(child)?.text
            val normalized = normalizeText(text)
            if (normalized.isNotEmpty() && normalized in DYNAMIC_TAB_NEIGHBORS) return true
        }
        return false
    }

    private fun findFirstTextView(view: View): TextView? {
        if (view is TextView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findFirstTextView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun hasListLikeAncestor(view: View): Boolean {
        var current: View? = view
        var depth = 0
        while (current != null && depth <= MAX_PARENT_DEPTH) {
            val name = current.javaClass.name.lowercase(Locale.ROOT)
            if (
                name.contains("recycler") || name.contains("viewpager") ||
                name.contains("item") || name.contains("card")
            ) {
                return true
            }
            current = current.parent as? View
            depth += 1
        }
        return false
    }

    private fun matchesAnyLabel(rawText: CharSequence?, labels: Set<String>): Boolean {
        val normalized = normalizeText(rawText)
        if (normalized.isEmpty()) return false
        return labels.any { label -> normalized == label }
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
        val className = rawName.lowercase(Locale.ROOT)
        return keywords.any(className::contains)
    }

    private fun isDynamicActivity(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        // 动态详情页同样含 "dynamic"，在那里扫描会误点页面正文里的标签文案。
        if (DETAIL_ACTIVITY_KEYWORDS.any(lower::contains)) return false
        return lower.contains("mainactivity") || lower.contains("main2") || lower.contains("dynamic")
    }

    private companion object {
        private val SWEEP_DELAYS_MS = longArrayOf(0L, 120L, 300L, 700L, 1500L, 3000L)
        private const val GLOBAL_SWEEP_WINDOW_MS = 4000L
        private const val GLOBAL_SWEEP_THROTTLE_MS = 120L
        private const val TAB_CLICK_COOLDOWN_MS = 1500L
        private const val TAB_CLICK_ACTIVITY_COOLDOWN_MS = 10_000L
        private const val MAX_VIEW_SCAN_NODES = 1500
        private const val MAX_TAB_SCAN_DEPTH = 14
        private const val MAX_PAGE_SCAN_DEPTH = 16
        private const val MAX_PARENT_DEPTH = 8
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private val DYNAMIC_FRAGMENT_KEYWORDS = listOf(
            "followinglist.home.mediator",
            "mediatorfragment",
            "followinglist",
        )
        private val DETAIL_ACTIVITY_KEYWORDS = listOf(
            "detail",
            "publish",
            "edit",
            "search",
            "player",
            "comment",
            "setting",
        )
        private val VIDEO_TAB_LABELS = setOf("视频")
        private val CITY_TAB_LABELS = setOf("同城")
        private val SCHOOL_TAB_LABELS = setOf("校园")
        private val DYNAMIC_TAB_NEIGHBORS = setOf(
            "综合",
            "全部",
            "视频",
            "图文",
            "直播",
            "关注",
            "同城",
            "校园",
            "热门",
            "最新",
            "动态",
            "投稿",
        )
        private var lastTabClickAt = 0L
    }
}
