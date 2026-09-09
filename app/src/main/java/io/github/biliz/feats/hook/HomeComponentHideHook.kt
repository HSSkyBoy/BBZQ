package io.github.biliz.feats.hook

import android.view.View
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.callMethod
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.symbol.RestoredHomeComponentHideSymbols
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

class HomeComponentHideHook(env: io.github.biliz.feats.RoamingEnv) : BaseRoamingHook(env) {
    private val knownComponents = linkedMapOf<String, String>()
    private val attachedLayoutListeners =
        Collections.synchronizedMap(WeakHashMap<View, android.view.ViewTreeObserver.OnGlobalLayoutListener>())

    override fun startHook() {
        if (env.processName != env.packageName) return
        val symbols = env.symbols?.homeComponentHide?.restore(classLoader)
        if (symbols == null) {
            log("startHook: HomeComponentHide skipped because symbols are unavailable")
            return
        }

        var count = 0
        count += hookFragmentLifecycle(symbols.fragmentLifecycleMethods)
        count += hookHomeComponentCatalog(symbols)

        if (count == 0) {
            log("startHook: HomeComponentHide no hook point found")
        } else {
            log("startHook: HomeComponentHide methods=$count")
        }
    }

    private fun hookFragmentLifecycle(methods: List<Method>): Int {
        var count = 0
        methods.forEach { method ->
            runCatching {
                env.hookAfter(method) { param ->
                    processFragment(param.thisObject)
                }
                count++
            }.onFailure {
                log("HomeComponentHide failed to hook ${method.declaringClass.name}.${method.name}", it)
            }
        }
        return count
    }

    private fun processFragment(fragment: Any?) {
        if (fragment == null) return
        val component = resolveHomeComponent(fragment) ?: return
        val root = component.callMethod("getView") as? View ?: return
        val className = component.javaClass.name
        if (!isCandidateComponent(className)) return

        saveKnownComponent(className)
        if (shouldHide(className)) {
            attachPersistentHider(root, className)
            applyVisibility(root, className)
        }
    }

    private fun hookHomeComponentCatalog(symbols: RestoredHomeComponentHideSymbols): Int {
        val catalogMethod = symbols.componentCatalogMethod ?: return 0
        val methods = symbols.baseHomeFragmentMethods
        var count = 0
        methods.forEach { method ->
            runCatching {
                env.hookAfter(method) { param ->
                    runCatching {
                        collectHomeComponentCatalog(param.thisObject, catalogMethod)
                    }.onFailure {
                        log("HomeComponentHide component catalog callback failed at ${method.declaringClass.name}.${method.name}", it)
                    }
                }
                count++
            }.onFailure {
                log("HomeComponentHide failed to hook ${method.declaringClass.name}.${method.name}", it)
            }
        }
        return count
    }

    private fun collectHomeComponentCatalog(fragment: Any?, catalogMethod: Method) {
        if (fragment == null) return
        val fragmentName = fragment.javaClass.name
        if (!BASE_HOME_FRAGMENT_NAMES.any { baseName ->
                fragmentName.equals(baseName, ignoreCase = true) ||
                    fragmentName.contains(baseName, ignoreCase = true)
            }) return

        val components = runCatching {
            catalogMethod.invoke(fragment) as? List<*>
        }.getOrNull().orEmpty()

        components.forEach { component ->
            val className = component?.javaClass?.name ?: return@forEach
            if (className.isBlank()) return@forEach
            saveKnownComponent(className)
        }
    }

    private fun resolveHomeComponent(fragment: Any): Any? {
        var current: Any? = fragment
        var parent = fragment.callMethod("getParentFragment")
        var guard = 0
        while (current != null && parent != null && guard < 20) {
            guard += 1
            if (isHomeContainer(parent)) return current
            current = parent
            parent = current.callMethod("getParentFragment")
        }
        return null
    }

    private fun isCandidateComponent(className: String): Boolean {
        if (!className.startsWith("com.bilibili") && !className.startsWith("tv.danmaku")) return false
        val classNameLower = className.lowercase()
        if (EXCLUDED_KEYWORDS.any(classNameLower::contains)) return false
        return true
    }

    private fun isHomeContainer(fragment: Any): Boolean {
        val name = fragment.javaClass.name.lowercase()
        return HOME_CONTAINER_KEYWORDS.any(name::contains)
    }

    private fun shouldHide(className: String): Boolean {
        if (ModuleSettings.isHideAllHomeComponentsEnabled(prefs)) return true
        if (!ModuleSettings.isCustomHomeComponentHideEnabled(prefs)) return false
        return className in ModuleSettings.getHiddenHomeComponents(prefs)
    }

    private fun saveKnownComponent(className: String) {
        if (knownComponents.containsKey(className)) return
        val snapshot = ModuleSettings.getKnownHomeComponents(prefs)
            .mapNotNull(::decodeComponent)
            .associateByTo(linkedMapOf(), { it.className }, { it.name })
        if (className in snapshot) {
            knownComponents.putAll(snapshot)
            return
        }

        val name = className.substringAfterLast('.').ifBlank { className }
        knownComponents.clear()
        knownComponents.putAll(snapshot)
        knownComponents[className] = name

        val encoded = knownComponents.entries.mapIndexed { index, entry ->
            encodeComponent(index, entry.value, entry.key)
        }.toMutableSet()
        ModuleSettings.cacheKnownHomeComponents(encoded)
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_KNOWN_HOME_COMPONENTS, encoded)
            .apply()
    }

    private fun attachPersistentHider(root: View, className: String) {
        if (attachedLayoutListeners.containsKey(root)) return
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            if (shouldHide(className)) {
                applyVisibility(root, className)
            }
        }
        runCatching {
            root.viewTreeObserver?.addOnGlobalLayoutListener(listener)
            attachedLayoutListeners[root] = listener
        }.onFailure {
            log("HomeComponentHide failed to attach listener for $className", it)
        }
    }

    private fun applyVisibility(root: View, className: String) {
        if (shouldHide(className)) {
            if (root.visibility != View.GONE) {
                root.visibility = View.GONE
            }
        }
    }

    private fun String.sanitizePart(): String =
        replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private fun encodeComponent(order: Int, name: String, className: String): String =
        listOf(order.toString(), name.sanitizePart(), className.sanitizePart()).joinToString("\t")

    private fun decodeComponent(raw: String): HomeComponentItem? {
        val parts = raw.split('\t', limit = 3)
        if (parts.size != 3) return null
        val order = parts[0].toIntOrNull() ?: return null
        return HomeComponentItem(order, parts[1], parts[2])
    }

    private data class HomeComponentItem(
        val order: Int,
        val name: String,
        val className: String,
    )

    private companion object {
        private val BASE_HOME_FRAGMENT_CLASSES = arrayOf(
            "tv.danmaku.bili.home.tab.page.BaseHomeFragment",
            "tv.danmaku.p9138bili.p9170home.p9173tab.p9174page.BaseHomeFragment",
        )
        private val BASE_HOME_FRAGMENT_NAMES = BASE_HOME_FRAGMENT_CLASSES.map { it.lowercase() }
        private val HOME_CONTAINER_KEYWORDS = listOf(
            "basehomefragment",
            "homefragment",
            "homeframe",
            "pagebuildcomponent",
            "main2.homefragment",
            "homefragmentv2",
        )
        private val EXCLUDED_KEYWORDS = listOf(
            "search",
            "dynamic",
            "following",
            "history",
            "favorite",
            "space",
            "reply",
            "detail",
            "mainfragment",
            "basemainframefragment",
            "mainactivity",
            "mine",
            "mall",
            "channel",
        )
    }
}
