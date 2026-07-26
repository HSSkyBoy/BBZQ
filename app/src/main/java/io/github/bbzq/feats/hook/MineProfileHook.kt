package io.github.bbzq.feats.hook

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.symbol.RestoredMineProfileSymbols
import kotlin.LazyThreadSafetyMode
import java.util.Collections
import java.util.WeakHashMap

class MineProfileHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val attachedLayoutListeners =
        Collections.synchronizedMap(WeakHashMap<View, android.view.ViewTreeObserver.OnGlobalLayoutListener>())
    private val originalVisibilityByModuleViews = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val mineSymbols: RestoredMineProfileSymbols? by lazy(LazyThreadSafetyMode.NONE) {
        env.symbols?.mineProfile?.restore(classLoader)
    }

    override fun startHook() {
        if (env.processName != env.packageName) return

        val symbols = mineSymbols ?: run {
            log("MineProfileHook skipped: symbols missing")
            return
        }

        env.hookBefore(symbols.onResume) { param ->
            runCatching {
                if (!ModuleSettings.isMineRemoveVipEnabled(prefs)) return@runCatching
                val fragment = param.thisObject ?: return@runCatching
                val vipView = symbols.resolveVipView(fragment) ?: return@runCatching
                vipView.visibility = if (ModuleSettings.isMineKeepVipSpaceEnabled(prefs)) {
                    View.INVISIBLE
                } else {
                    View.GONE
                }
            }.onFailure {
                log("MineProfile vip hook failed at ${symbols.onResume.declaringClass.name}.${symbols.onResume.name}", it)
            }
        }
        env.hookAfter(symbols.onResume) { param ->
            runCatching {
                val fragment = param.thisObject ?: return@runCatching
                val root = fragment.javaClass.methods
                    .firstOrNull { it.name == "getView" && it.parameterCount == 0 }
                    ?.apply { isAccessible = true }
                    ?.invoke(fragment) as? View
                    ?: return@runCatching
                attachMineComponentWatcher(root)
                collectAndApplyMineComponents(root)
            }.onFailure {
                log("MineProfile component hook failed", it)
            }
        }
        log("startHook: MineProfile, methods=2")
    }

    private fun attachMineComponentWatcher(root: View) {
        if (attachedLayoutListeners.containsKey(root)) return
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            collectAndApplyMineComponents(root)
        }
        root.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        attachedLayoutListeners[root] = listener
    }

    private fun collectAndApplyMineComponents(root: View) {
        val components = linkedMapOf<String, View>()
        mineListItemRoots(root).forEach { itemRoot ->
            val itemComponents = linkedMapOf<String, MutableList<TextView>>()
            collectTextViews(itemRoot) { textView ->
                val name = textView.text?.toString()?.trim().orEmpty()
                if (name.isBlank() || name.length > MAX_COMPONENT_NAME_LENGTH || name.contains('\n')) return@collectTextViews
                itemComponents.getOrPut(name) { mutableListOf() } += textView
            }
            itemComponents.forEach { (name, textViews) ->
                val componentRoot = if (itemComponents.size == 1) {
                    // A one-component adapter item must be removed as a whole; hiding only its
                    // clickable child leaves the RecyclerView row's padding behind.
                    itemRoot
                } else {
                    textViews.asSequence()
                        .mapNotNull { findComponentContainer(it, itemRoot) }
                        .firstOrNull()
                }
                componentRoot?.let { components.putIfAbsent(name, it) }
            }
        }
        if (components.isEmpty()) return

        saveKnownComponents(components.keys)
        val hidden = if (ModuleSettings.isCustomMineComponentHideEnabled(prefs)) {
            ModuleSettings.getHiddenMineComponents(prefs)
        } else {
            emptySet()
        }
        components.forEach { (name, view) ->
            if (name in hidden) {
                originalVisibilityByModuleViews.putIfAbsent(view, view.visibility)
                view.visibility = View.GONE
            } else {
                originalVisibilityByModuleViews.remove(view)?.let { originalVisibility ->
                    view.visibility = originalVisibility
                }
            }
        }
    }

    private fun mineListItemRoots(root: View): List<View> {
        val recyclerViews = ArrayList<ViewGroup>()
        collectRecyclerViews(root, recyclerViews)
        return recyclerViews.flatMap { recyclerView ->
            buildList {
                for (index in 0 until recyclerView.childCount) {
                    add(recyclerView.getChildAt(index))
                }
            }
        }
    }

    private fun collectRecyclerViews(view: View, output: MutableList<ViewGroup>) {
        if (view is ViewGroup && view.javaClass.name.contains(".recyclerview.widget.RecyclerView")) {
            output += view
            return
        }
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) {
            collectRecyclerViews(view.getChildAt(index), output)
        }
    }

    private fun collectTextViews(view: View, action: (TextView) -> Unit) {
        if (view is TextView) action(view)
        if (view !is ViewGroup) return
        for (index in 0 until view.childCount) collectTextViews(view.getChildAt(index), action)
    }

    private fun findComponentContainer(textView: TextView, root: View): View? {
        var current: View = textView
        var outermostCell: View? = null
        while (current !== root) {
            val parent = current.parent as? ViewGroup ?: break
            if (parent !== root && parent.width > 0 && parent.height > 0 &&
                parent.width * 3 <= root.width * 2 && parent.height <= MAX_COMPONENT_HEIGHT_PX
            ) {
                // Prefer the outermost cell below the RecyclerView item.  A clickable child is
                // often wrapped by a fixed-size layout, and hiding that child alone leaves a gap.
                outermostCell = parent
            }
            current = parent
        }
        return outermostCell
    }

    private fun saveKnownComponents(names: Set<String>) {
        val known = ModuleSettings.getKnownMineComponents(prefs).toMutableSet()
        if (!known.addAll(names)) return
        ModuleSettings.cacheKnownMineComponents(known)
        prefs.edit().putStringSet(ModuleSettings.KEY_KNOWN_MINE_COMPONENTS, known).apply()
    }

    private companion object {
        // Grid cells in the current Mine page are compact; this avoids cataloguing profile headers.
        const val MAX_COMPONENT_HEIGHT_PX = 600
        const val MAX_COMPONENT_NAME_LENGTH = 24
    }
}
