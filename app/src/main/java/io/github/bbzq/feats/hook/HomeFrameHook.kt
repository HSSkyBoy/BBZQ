package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.symbol.RestoredHomeFrameSymbols
import io.github.libxposed.api.XposedInterface

class HomeFrameHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val installedConstructors = mutableSetOf<String>()
    private val depth = ThreadLocal.withInitial { 0 }

    override fun startHook() {
        if (env.processName != env.packageName || isInstalled) return
        val symbols = env.symbols?.homeFrame?.restore(classLoader) ?: return
        symbols.constructors.forEach { constructor ->
            val key = constructor.toGenericString()
            if (key in installedConstructors) return@forEach
            runCatching {
                xposed.hook(constructor)
                    .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
                    .intercept { chain ->
                        val previous = depth.get() ?: 0
                        depth.set(previous + 1)
                        try {
                            val result = chain.proceed(chain.getArgs().toTypedArray())
                            if (previous == 0) {
                                runCatching { process(chain.getThisObject(), symbols) }
                                    .onFailure { failure -> runCatching { log("HomeFrame model processor failed", failure) } }
                            }
                            result
                        } finally {
                            if (previous == 0) depth.remove() else depth.set(previous)
                        }
                    }
                installedConstructors += key
            }.onFailure { log("HomeFrame constructor hook failed", it) }
        }
        isInstalled = installedConstructors.size == symbols.constructors.size
        log("startHook: HomeFrame, constructors=${installedConstructors.size}/${symbols.constructors.size}")
    }

    private fun process(model: Any?, symbols: RestoredHomeFrameSymbols) {
        if (model == null) return
        symbols.lists.forEach { (name, field) ->
            val source = field.get(model) as? List<*> ?: return@forEach
            if (source is FilteredHomeFrameList || source.isEmpty()) return@forEach
            val entries = source.mapIndexedNotNull { index, item ->
                if (item == null || symbols.itemFields.getValue("uri").declaringClass.isInstance(item).not()) {
                    return@mapIndexedNotNull null
                }
                fun string(key: String): String = (symbols.itemFields[key]?.get(item) as? String).orEmpty().trim()
                Entry(index, string("id"), string("name"), string("uri"), string("tab_id"),
                    symbols.itemFields.getValue("default_selected").getInt(item) != 0)
            }
            if (entries.isEmpty()) return@forEach
            val known = when (name) {
                "tab" -> ModuleSettings.getKnownHomeRecommendTabs(prefs)
                "bottom" -> ModuleSettings.getKnownBottomBarItems(prefs)
                else -> emptySet()
            }.map { it.split('\t') }.filter { it.size >= 4 }
            fun key(entry: Entry): String = known.firstOrNull {
                (entry.name.isNotBlank() && it[2] == entry.name) || (entry.uri.isNotBlank() && it[3] == entry.uri)
            }?.get(1)?.takeIf { it.isNotBlank() } ?: entry.id.ifBlank { entry.tabId.ifBlank { entry.uri } }
            if (name != "top") saveKnown(name, entries, ::key)
            val hidden = when (name) {
                "tab" -> if (ModuleSettings.isCustomHomeRecommendTabFilterEnabled(prefs)) {
                    ModuleSettings.getHiddenHomeRecommendTabs(prefs)
                } else emptySet()
                "bottom" -> if (ModuleSettings.isCustomBottomBarEnabled(prefs)) {
                    ModuleSettings.getHiddenBottomBarItems(prefs)
                } else emptySet()
                else -> emptySet()
            }
            val removed = entries.filter { entry ->
                if (name == "top") {
                    ModuleSettings.isHideHomeTopBarPromotionEnabled(prefs) && isHomeGameMenuAction(entry.uri)
                } else {
                    !entry.selected && listOf(key(entry), entry.id, entry.name, entry.uri, entry.tabId)
                        .any { it.isNotBlank() && it in hidden }
                }
            }.mapTo(HashSet()) { it.index }
            if (removed.isEmpty() || (name != "top" && removed.size == source.size)) return@forEach
            val filtered = source.filterIndexed { index, _ -> index !in removed }
            field.set(model, FilteredHomeFrameList(filtered))
            log("HomeFrame $name removed ${removed.size} item(s)")
        }
    }

    private fun saveKnown(name: String, entries: List<Entry>, key: (Entry) -> String) {
        val encoded = entries.mapNotNullTo(linkedSetOf()) { entry ->
            val id = key(entry).takeIf { it.isNotBlank() } ?: return@mapNotNullTo null
            val parts = listOf(entry.index.toString(), id, entry.name.ifBlank { id }, entry.uri)
            (if (name == "tab") parts + entry.tabId else parts).joinToString("\t") {
                it.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
            }
        }
        if (encoded.isEmpty()) return
        val preference = if (name == "tab") {
            if (encoded == ModuleSettings.getKnownHomeRecommendTabs(prefs)) return
            ModuleSettings.cacheKnownHomeRecommendTabs(encoded)
            ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS
        } else {
            if (encoded == ModuleSettings.getKnownBottomBarItems(prefs)) return
            ModuleSettings.cacheKnownBottomBarItems(encoded)
            ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS
        }
        prefs.edit().putStringSet(preference, encoded).apply()
    }

    private data class Entry(
        val index: Int,
        val id: String,
        val name: String,
        val uri: String,
        val tabId: String,
        val selected: Boolean,
    )

    private class FilteredHomeFrameList(items: Collection<Any?>) : ArrayList<Any?>(items)
}
