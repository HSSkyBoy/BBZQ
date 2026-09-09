package io.github.biliz.feats.hook

import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.symbol.RestoredHomeRecommendTabSymbols
import java.lang.reflect.Field

class HomeRecommendTabHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var removeAllSkippedLogged = false

    override fun startHook() {
        if (env.processName != env.packageName) return
        ModuleSettings.refreshKnownHomeRecommendTabsCache(prefs)

        val symbols = env.symbols?.homeRecommendTabs?.restore(classLoader)
        if (symbols == null) {
            log("startHook: HomeRecommendTabs missing symbols")
            return
        }

        env.hookBefore(symbols.buildTabsMethod) { param ->
            runCatching {
                processTabsArgument(param.args, symbols)
            }.onFailure {
                log(
                    "HomeRecommendTabs failed at ${symbols.buildTabsMethod.declaringClass.name}.${symbols.buildTabsMethod.name}",
                    it,
                )
            }
        }
        log("startHook: HomeRecommendTabs method=${symbols.buildTabsMethod.declaringClass.name}.${symbols.buildTabsMethod.name}")
    }

    private fun processTabsArgument(
        args: MutableList<Any?>,
        symbols: RestoredHomeRecommendTabSymbols,
    ) {
        val original = args.firstOrNull() as? List<*> ?: return
        if (original.isEmpty()) return

        val entriesByIndex = original.mapIndexedNotNull { index, item ->
            item?.extractTabEntry(index, symbols)
        }.associateBy { it.index }
        if (entriesByIndex.isEmpty()) return

        saveKnownTabs(entriesByIndex.values.sortedBy { it.order })

        val enabled = ModuleSettings.isCustomHomeRecommendTabFilterEnabled(prefs)
        val hiddenTabs = ModuleSettings.getHiddenHomeRecommendTabs(prefs)
        if (!enabled || hiddenTabs.isEmpty()) return

        val filtered = ArrayList<Any?>(original.size)
        var removed = 0
        original.forEachIndexed { index, item ->
            val entry = entriesByIndex[index]
            if (entry != null && entry.key in hiddenTabs) {
                removed += 1
            } else {
                filtered += item
            }
        }

        if (removed == 0) return
        if (filtered.isEmpty()) {
            if (!removeAllSkippedLogged) {
                removeAllSkippedLogged = true
                log("HomeRecommendTabs skipped filtering because all tabs would be removed")
            }
            return
        }

        args[0] = filtered
        log("HomeRecommendTabs removed $removed tab(s)")
    }

    private fun Any.extractTabEntry(
        index: Int,
        symbols: RestoredHomeRecommendTabSymbols,
    ): HomeRecommendTabEntry? {
        val id = readString(symbols.idField)
        val title = readString(symbols.titleField)
        val uri = readString(symbols.uriField)
        val reporterId = readString(symbols.reporterIdField)
        val key = listOf(id, reporterId, uri, title).firstOrNull { it.isNotBlank() } ?: return null
        val name = title.ifBlank {
            listOf(id, reporterId, uri).firstOrNull { it.isNotBlank() } ?: key
        }
        return HomeRecommendTabEntry(
            order = index,
            key = key,
            name = name,
            uri = uri,
            reporterId = reporterId,
            index = index,
        )
    }

    private fun Any.readString(field: Field?): String =
        field?.let {
            runCatching { it.get(this) as? String }
                .getOrNull()
                ?.trim()
                .orEmpty()
        }.orEmpty()

    private fun saveKnownTabs(entries: Collection<HomeRecommendTabEntry>) {
        if (entries.isEmpty()) return
        val encoded = entries
            .distinctBy { it.key }
            .map { entry ->
                encodeTab(
                    order = entry.order,
                    key = entry.key,
                    name = entry.name,
                    uri = entry.uri,
                    reporterId = entry.reporterId,
                )
            }
            .toMutableSet()
        val oldItems = ModuleSettings.getKnownHomeRecommendTabs(prefs)
        if (oldItems == encoded) return
        ModuleSettings.cacheKnownHomeRecommendTabs(encoded)
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS, encoded)
            .apply()
    }

    private fun encodeTab(
        order: Int,
        key: String,
        name: String,
        uri: String,
        reporterId: String,
    ): String =
        listOf(order.toString(), key, name, uri, reporterId)
            .joinToString(ITEM_SEPARATOR) { it.sanitizeItemPart() }

    private fun String.sanitizeItemPart(): String =
        replace('\t', ' ')
            .replace('\n', ' ')
            .replace('\r', ' ')

    private data class HomeRecommendTabEntry(
        val order: Int,
        val key: String,
        val name: String,
        val uri: String,
        val reporterId: String,
        val index: Int,
    )

    private companion object {
        private const val ITEM_SEPARATOR = "\t"
    }
}
