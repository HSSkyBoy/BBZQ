package io.github.biliz.feats.hook

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.hookAfter
import io.github.biliz.feats.hookBefore
import org.json.JSONObject
import java.util.Collections
import java.util.WeakHashMap

class BottomBarHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var cachedBottomEntriesLoaded = false
    private var cacheReaderFailureLogged = false
    private val attachedContainerListeners =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, View.OnLayoutChangeListener>())

    override fun startHook() {
        ModuleSettings.refreshKnownBottomBarItemsCache(prefs)
        saveCachedBottomEntries()
        Handler(Looper.getMainLooper()).postDelayed(
            { saveCachedBottomEntries() },
            HOME_TAB_CACHE_READ_DELAY_MS,
        )
        val symbols = env.symbols?.bottomBar?.restore(classLoader)
        val tabHostSetTabsMethods = symbols?.tabHostSetTabsMethods.orEmpty()
        val tabHostGetTabsMethods = symbols?.tabHostGetTabsMethods.orEmpty()
        val baseOnViewCreatedMethods = symbols?.baseOnViewCreatedMethods.orEmpty()

        tabHostSetTabsMethods.forEach { method ->
            env.hookBefore(method) { param ->
                runCatching {
                    val tabs = param.args.getOrNull(0) as? List<*> ?: return@runCatching
                    dispatch(tabs)
                }.onFailure {
                    log("Bottom bar TabHost processor failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
            env.hookAfter(method) { param ->
                runCatching {
                    val tabs = param.args.getOrNull(0) as? List<*> ?: return@runCatching
                    hideTabs(param.thisObject, tabs)
                }.onFailure {
                    log("Bottom bar TabHost post processor failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }

        val tabHostClass = tabHostSetTabsMethods.firstOrNull()?.declaringClass
        baseOnViewCreatedMethods.forEach { method ->
            env.hookAfter(method) { param ->
                runCatching {
                    val fragment = param.thisObject ?: return@runCatching
                    saveCachedBottomEntries()
                    fragment.extractSourceBottomEntries()
                        .takeIf { it.isNotEmpty() }
                        ?.let { entries ->
                            saveKnownEntries(
                                entries,
                                preserveMissing = shouldPreserveMissing(entries.map(BottomBarEntry::id)),
                            )
                        }
                    val host = fragment.findTabHost(tabHostClass) ?: return@runCatching
                    val getTabs = tabHostGetTabsMethods.firstOrNull { it.declaringClass.isInstance(host) }
                        ?: return@runCatching
                    val tabs = getTabs.invoke(host) as? List<*> ?: return@runCatching
                    dispatch(tabs)
                    hideTabs(host, tabs)
                }.onFailure {
                    log("Bottom bar onViewCreated processor failed at ${method.declaringClass.name}.${method.name}", it)
                }
            }
        }

        val totalMethods = tabHostSetTabsMethods.size + baseOnViewCreatedMethods.size
        if (totalMethods == 0) {
            log("startHook: BottomBar, no hook point found")
        } else {
            log("startHook: BottomBar, methods=$totalMethods")
        }
    }

    private fun dispatch(tabs: List<*>) {
        saveCachedBottomEntries()
        val hiddenIds = ModuleSettings.getHiddenBottomBarItems(prefs)
        val knownItems = linkedSetOf<String>()
        val observedIds = linkedSetOf<String>()

        tabs.forEach { item ->
            val entry = item?.extractBottomEntry()
            if (entry == null) return@forEach
            observedIds += entry.id
            knownItems += encodeBottomItem(
                order = knownItems.size,
                id = entry.id,
                name = entry.name,
                uri = entry.uri,
            )
        }

        saveKnownItems(knownItems, preserveMissing = hiddenIds.any { it !in observedIds })
    }

    private fun hideTabs(host: Any?, tabs: List<*>) {
        val container = host?.findTabContainer(tabs.size) ?: return
        val enabled = ModuleSettings.isCustomBottomBarEnabled(prefs)
        val hiddenKeys = if (enabled) ModuleSettings.getHiddenBottomBarItems(prefs) else emptySet()
        val knownItems = ModuleSettings.getKnownBottomBarItems(prefs).mapNotNull(::decodeBottomItem)

        fun applyTabVisibilities() {
            tabs.forEachIndexed { index, item ->
                val entry = item?.extractBottomEntry() ?: return@forEachIndexed
                val child = container.getChildAt(index) ?: return@forEachIndexed
                val hidden = enabled && isEntryHidden(entry, hiddenKeys, knownItems)
                val targetVisibility = if (hidden) View.GONE else View.VISIBLE
                if (child.visibility != targetVisibility) {
                    child.visibility = targetVisibility
                }
                child.isClickable = !hidden
                child.isEnabled = !hidden
                child.alpha = if (hidden) 0f else 1f
            }
        }

        applyTabVisibilities()
        attachContainerLayoutListener(container, ::applyTabVisibilities)
    }

    private fun attachContainerLayoutListener(container: ViewGroup, onLayout: () -> Unit) {
        if (attachedContainerListeners.containsKey(container)) return
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onLayout()
        }
        container.addOnLayoutChangeListener(listener)
        attachedContainerListeners[container] = listener
    }

    private fun isEntryHidden(
        entry: BottomBarEntry,
        hiddenKeys: Set<String>,
        knownItems: Collection<KnownBottomBarItem>,
    ): Boolean {
        if (hiddenKeys.isEmpty()) return false
        if (entry.id in hiddenKeys || entry.name in hiddenKeys || (entry.uri.isNotBlank() && entry.uri in hiddenKeys)) {
            return true
        }
        for (known in knownItems) {
            val matchesEntry = (entry.id.isNotBlank() && entry.id.equals(known.id, ignoreCase = true)) ||
                (entry.name.isNotBlank() && entry.name.equals(known.name, ignoreCase = true)) ||
                (entry.uri.isNotBlank() && entry.uri.equals(known.uri, ignoreCase = true))
            if (matchesEntry) {
                if (known.id in hiddenKeys || known.name in hiddenKeys || (known.uri.isNotBlank() && known.uri in hiddenKeys)) {
                    return true
                }
            }
        }
        return false
    }

    private fun Any.findTabContainer(tabCount: Int): ViewGroup? {
        if (this is ViewGroup && childCount >= tabCount) {
            return this
        }
        val directChild = (this as? ViewGroup)?.let { findDirectChildContainer(it, tabCount) }
        if (directChild != null) return directChild
        return javaClass.allFields()
            .mapNotNull { field ->
                runCatching { field.get(this) as? ViewGroup }.getOrNull()
            }
            .firstOrNull { container -> container.childCount >= tabCount }
    }

    private fun findDirectChildContainer(parent: ViewGroup, tabCount: Int): ViewGroup? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup && child.childCount >= tabCount) {
                return child
            }
        }
        return null
    }

    private fun saveCachedBottomEntries() {
        if (cachedBottomEntriesLoaded) return
        runCatching {
            val file = env.hostContext.filesDir.resolve(HOME_TAB_CACHE_FILE)
            if (!file.isFile) return
            val bottom = JSONObject(file.readText()).optJSONObject("data")?.optJSONArray("bottom") ?: return
            val entries = ArrayList<BottomBarEntry>(bottom.length())
            for (index in 0 until bottom.length()) {
                val item = bottom.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val uri = item.optString("uri").trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    entries += BottomBarEntry(id, name, uri)
                }
            }
            if (entries.size >= MIN_BOTTOM_BAR_ITEMS) {
                saveKnownEntries(entries, preserveMissing = false)
                cachedBottomEntriesLoaded = true
            }
        }.onFailure {
            if (!cacheReaderFailureLogged) {
                cacheReaderFailureLogged = true
                log("Bottom bar cache reader failed", it)
            }
        }
    }

    private fun Any.extractBottomEntry(): BottomBarEntry? {
        val fields = javaClass.allFields().toList()
        val strings = fields
            .mapNotNull { field ->
                runCatching { field.get(this) as? String }.getOrNull()?.trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()

        val guessedUri = strings.firstOrNull(::looksLikeUri)
        val guessedName = strings.firstOrNull(::looksLikeDisplayName)
        val guessedId = strings.firstOrNull { it != guessedUri && it != guessedName && looksLikeAsciiId(it) }
            ?: strings.firstOrNull { it != guessedUri && it != guessedName && looksLikeBottomBarId(it) }
            ?: strings.firstOrNull { it != guessedUri && it != guessedName }

        val intId = fields.firstNotNullOfOrNull { field ->
            if (field.name.equals("id", ignoreCase = true) || field.name.equals("mId", ignoreCase = true)) {
                runCatching { field.get(this) }.getOrNull()?.toString()?.takeIf { it.isNotBlank() && it != "0" && it != "-1" }
            } else null
        }

        if (guessedId == null && guessedName == null && guessedUri == null && intId == null) return null
        val resolvedId = guessedId ?: intId ?: guessedName ?: guessedUri ?: return null
        val resolvedName = guessedName ?: guessedId ?: intId ?: return null
        return BottomBarEntry(resolvedId, resolvedName, guessedUri.orEmpty())
    }

    private fun Any.extractSourceBottomEntries(): List<BottomBarEntry> =
        javaClass.allFields()
            .mapNotNull { field ->
                runCatching { field.get(this) as? List<*> }.getOrNull()
            }
            .mapNotNull { list ->
                list.mapNotNull { item -> item?.extractBottomEntryDeep() }
                    .takeIf { entries ->
                        entries.size == list.size &&
                            entries.size >= MIN_BOTTOM_BAR_ITEMS &&
                            entries.all { it.uri.isNotBlank() }
                    }
            }
            .maxByOrNull { it.size }
            .orEmpty()

    private fun Any.extractBottomEntryDeep(): BottomBarEntry? =
        extractBottomEntry()
            ?: javaClass.allFields()
                .mapNotNull { field ->
                    runCatching { field.get(this) }.getOrNull()
                }
                .firstNotNullOfOrNull { value ->
                    value.takeUnless { it === this || it is Collection<*> || it.javaClass.isArray }
                        ?.extractBottomEntry()
                }

    private fun Any.findTabHost(tabHostClass: Class<*>?): Any? {
        if (tabHostClass == null) return null
        if (tabHostClass.isInstance(this)) return this
        return javaClass.allFields()
            .firstNotNullOfOrNull { field ->
                runCatching { field.get(this) }
                    .getOrNull()
                    ?.takeIf(tabHostClass::isInstance)
            }
    }

    private fun looksLikeUri(value: String): Boolean =
        "://" in value || value.startsWith("bilibili://", ignoreCase = true) ||
            value.startsWith("activity://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun looksLikeDisplayName(value: String): Boolean {
        if (looksLikeUri(value)) return false
        if (value.length < 2) return false
        return value.any { it.isLetter() || it.code in 0x4E00..0x9FFF }
    }

    private fun looksLikeBottomBarId(value: String): Boolean {
        if (looksLikeUri(value)) return false
        if (value.length !in 1..48) return false
        if (value.any(Char::isWhitespace)) return false
        return value.any { it.isLetterOrDigit() || it == '_' || it == '-' }
    }

    private fun looksLikeAsciiId(value: String): Boolean {
        if (!looksLikeBottomBarId(value)) return false
        return value.all { it.code in 0x21..0x7E }
    }

    private fun saveKnownEntries(entries: Collection<BottomBarEntry>, preserveMissing: Boolean) {
        val items = entries.mapIndexedTo(linkedSetOf()) { index, entry ->
            encodeBottomItem(
                order = index,
                id = entry.id,
                name = entry.name,
                uri = entry.uri,
            )
        }
        saveKnownItems(items, preserveMissing)
    }

    private fun shouldPreserveMissing(observedIds: Collection<String>): Boolean {
        val observed = observedIds.toSet()
        return ModuleSettings.getHiddenBottomBarItems(prefs).any { it !in observed }
    }

    private fun saveKnownItems(items: Set<String>, preserveMissing: Boolean) {
        if (items.isEmpty()) return
        val oldItems = ModuleSettings.getKnownBottomBarItems(prefs)
        val updatedItems = if (preserveMissing) mergeKnownItems(oldItems, items) else normalizeKnownItems(items)
        if (oldItems == updatedItems) return
        ModuleSettings.cacheKnownBottomBarItems(updatedItems)
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS, updatedItems.toMutableSet())
            .apply()
    }

    private fun mergeKnownItems(oldItems: Set<String>, observedItems: Set<String>): Set<String> {
        val merged = linkedMapOf<String, KnownBottomBarItem>()
        oldItems.mapNotNull(::decodeBottomItem).forEach { item ->
            merged.putIfAbsent(item.id, item)
        }
        observedItems.mapNotNull(::decodeBottomItem).forEach { item ->
            val oldItem = merged[item.id]
            merged[item.id] = item.copy(order = oldItem?.order ?: item.order)
        }
        return encodeKnownItems(merged.values)
    }

    private fun normalizeKnownItems(items: Set<String>): Set<String> =
        encodeKnownItems(items.mapNotNull(::decodeBottomItem))

    private fun encodeKnownItems(items: Collection<KnownBottomBarItem>): Set<String> =
        items.asSequence()
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy(KnownBottomBarItem::id)
            .sortedWith(compareBy<KnownBottomBarItem> { it.order }.thenBy { it.name }.thenBy { it.id })
            .mapIndexed { index, item ->
                encodeBottomItem(
                    order = index,
                    id = item.id,
                    name = item.name,
                    uri = item.uri,
                )
            }
            .toMutableSet()

    private fun decodeBottomItem(raw: String): KnownBottomBarItem? {
        val parts = raw.split(ITEM_SEPARATOR, limit = 4)
        if (parts.size == 4) {
            val order = parts[0].toIntOrNull() ?: return null
            return KnownBottomBarItem(order, parts[1], parts[2], parts[3])
        }
        if (parts.size == 3) {
            return KnownBottomBarItem(Int.MAX_VALUE, parts[0], parts[1], parts[2])
        }
        return null
    }

    private fun encodeBottomItem(order: Int, id: String, name: String, uri: String): String =
        listOf(order.toString(), id, name, uri)
            .joinToString(ITEM_SEPARATOR) { it.sanitizeItemPart() }

    private fun String.sanitizeItemPart(): String =
        replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')

    private data class BottomBarEntry(
        val id: String,
        val name: String,
        val uri: String,
    )

    private data class KnownBottomBarItem(
        val order: Int,
        val id: String,
        val name: String,
        val uri: String,
    )

    private companion object {
        private const val ITEM_SEPARATOR = "\t"
        private const val MIN_BOTTOM_BAR_ITEMS = 3
        private const val HOME_TAB_CACHE_FILE = "home_tab_v2.data"
        private const val HOME_TAB_CACHE_READ_DELAY_MS = 2_000L
    }
}
