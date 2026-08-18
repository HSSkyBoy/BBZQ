package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.symbol.RestoredRelateResponseGetItemsSymbols
import io.github.bbzq.feats.symbol.RestoredVideoDetailRelateFeedSymbols
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class VideoDetailRelateFilterHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var titleKeywordsRaw = ""
    private var titleKeywordsCache = emptyList<String>()
    private val knownTypes = mutableSetOf<String>()
    private val methodCache = mutableMapOf<Class<*>, MutableMap<String, Method?>>()

    override fun startHook() {
        if (env.processName != env.packageName) return
        ModuleSettings.refreshKnownVideoDetailRelateTypesCache(prefs)
        knownTypes.addAll(ModuleSettings.getKnownVideoDetailRelateTypes(prefs))

        val symbols = env.symbols?.videoDetailRelateFeed?.restore(classLoader)
        if (symbols == null) {
            log("startHook: VideoDetailRelateFilter skipped because symbols are unavailable")
            return
        }

        var installed = 0
        symbols.responseGetItems.forEach { response ->
            if (installResponseFilter(response)) installed++
        }
        symbols.detailRelateServiceMethod?.let { method ->
            if (installServiceFilter(method)) installed++
        }

        if (installed == 0) {
            log("startHook: VideoDetailRelateFilter no hook point found")
        } else {
            log("startHook: VideoDetailRelateFilter installed=$installed")
        }
    }

    private fun installResponseFilter(symbols: RestoredRelateResponseGetItemsSymbols): Boolean {
        val getItems = symbols.getItems
        env.hookAfter(getItems) { param ->
            runCatching {
                val original = param.result as? List<*> ?: return@runCatching
                if (original.isEmpty()) return@runCatching

                // 1. 采集所有遇到的卡片类型
                original.forEach { item ->
                    extractType(item)?.let { saveKnownType(it) }
                }

                // 2. 检查是否开启过滤
                val enabled = ModuleSettings.isCustomVideoDetailRelateFilterEnabled(prefs)
                val hiddenTypes = ModuleSettings.getHiddenVideoDetailRelateTypes(prefs)
                val titleKeywords = if (enabled) currentTitleKeywords() else emptyList()

                if (!enabled || (hiddenTypes.isEmpty() && titleKeywords.isEmpty())) return@runCatching

                val filtered = ArrayList<Any?>(original.size)
                var removed = 0
                original.forEach { item ->
                    if (shouldFilterItem(item, hiddenTypes, titleKeywords)) {
                        removed++
                    } else {
                        filtered.add(item)
                    }
                }

                if (removed > 0) {
                    param.result = filtered
                    writeBackFilteredItems(param.thisObject, symbols.itemsField, filtered)
                    log("VideoDetailRelateFilter removed $removed item(s) from ${getItems.declaringClass.name}.${getItems.name}")
                }
            }.onFailure {
                log("VideoDetailRelateFilter response hook failed at ${getItems.declaringClass.name}.${getItems.name}", it)
            }
        }
        return true
    }

    private fun installServiceFilter(method: Method): Boolean {
        env.hookBefore(method) { param ->
            runCatching {
                val item = param.args.firstOrNull() ?: return@runCatching

                // 1. 采集类型
                extractType(item)?.let { saveKnownType(it) }

                // 2. 检查是否过滤
                val enabled = ModuleSettings.isCustomVideoDetailRelateFilterEnabled(prefs)
                val hiddenTypes = ModuleSettings.getHiddenVideoDetailRelateTypes(prefs)
                val titleKeywords = if (enabled) currentTitleKeywords() else emptyList()

                if (enabled && shouldFilterItem(item, hiddenTypes, titleKeywords)) {
                    param.result = null
                    log("VideoDetailRelateFilter blocked component in ${method.declaringClass.name}.${method.name}")
                }
            }.onFailure {
                log("VideoDetailRelateFilter service hook failed at ${method.declaringClass.name}.${method.name}", it)
            }
        }
        return true
    }

    private fun shouldFilterItem(
        item: Any?,
        hiddenTypes: Set<String>,
        titleKeywords: List<String>,
    ): Boolean {
        if (item == null) return false
        val type = extractType(item)
        if (type != null && hiddenTypes.any { it.equals(type, ignoreCase = true) }) {
            return true
        }
        if (titleKeywords.isNotEmpty()) {
            val title = extractTitle(item)
            if (!title.isNullOrBlank() && titleKeywords.any { title.contains(it, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    private fun extractType(item: Any?): String? {
        if (item == null) return null
        // 1. Try getCardCase() (Protobuf RelateCard)
        callNoArg(item, "getCardCase")?.let { cardCase ->
            val name = (cardCase as? Enum<*>)?.name ?: cardCase.toString()
            if (name.isNotBlank() && !name.equals("CARD_NOT_SET", ignoreCase = true)) {
                return normalizeTypeName(name)
            }
        }

        // 2. Try getGoto() / getCardType() (Protobuf Relate)
        callNoArg(item, "getGoto")?.toString()?.takeIf { it.isNotBlank() }?.let {
            return normalizeTypeName(it)
        }
        callNoArg(item, "getCardType")?.toString()?.takeIf { it.isNotBlank() }?.let {
            return normalizeTypeName(it)
        }

        // 3. Try DetailRelateService D0 fields
        val itemClass = item.javaClass
        if (itemClass.name.endsWith("D0") || itemClass.name.contains("relate.D0")) {
            runCatching {
                val fieldA = itemClass.fields.firstOrNull { it.name == "f289440a" || it.name == "type" }
                val typeVal = fieldA?.get(item)
                val typeName = (typeVal as? Enum<*>)?.name ?: typeVal?.toString()
                if (!typeName.isNullOrBlank() && !typeName.equals("CARD_TYPE_UNKNOWN", ignoreCase = true)) {
                    return normalizeTypeName(typeName)
                }
            }
            runCatching {
                val fieldB = itemClass.fields.firstOrNull { it.name == "f289441b" || it.name == "goto" }
                val gotoVal = fieldB?.get(item)?.toString()
                if (!gotoVal.isNullOrBlank()) {
                    return normalizeTypeName(gotoVal)
                }
            }
        }

        return null
    }

    private fun extractTitle(item: Any?): String? {
        if (item == null) return null

        // 1. Direct getTitle()
        callNoArg(item, "getTitle")?.toString()?.takeIf { it.isNotBlank() }?.let {
            return it
        }

        // 2. Sub-card in RelateCard: getAv(), getGame(), getCm(), getResource(), getLive(), getSpecial(), etc.
        val subGetters = arrayOf("getAv", "getGame", "getCm", "getResource", "getLive", "getSpecial", "getBangumi", "getBangumiSeason", "getCourse")
        for (getter in subGetters) {
            val sub = callNoArg(item, getter) ?: continue
            callNoArg(sub, "getTitle")?.toString()?.takeIf { it.isNotBlank() }?.let {
                return it
            }
        }

        // 3. DetailRelateService D0 method d()
        callNoArg(item, "d")?.toString()?.takeIf { it.isNotBlank() }?.let {
            return it
        }

        return null
    }

    private fun normalizeTypeName(raw: String): String {
        var clean = raw.trim().uppercase()
        if (clean.startsWith("CARD_TYPE_")) {
            clean = clean.removePrefix("CARD_TYPE_")
        }
        return clean
    }

    private fun saveKnownType(typeName: String) {
        val normalized = normalizeTypeName(typeName)
        if (normalized.isBlank() || normalized == "UNKNOWN" || normalized == "CARD_NOT_SET") return

        synchronized(knownTypes) {
            if (knownTypes.add(normalized)) {
                val copy = knownTypes.toSet()
                ModuleSettings.cacheKnownVideoDetailRelateTypes(copy)
                prefs.edit()
                    .putStringSet(ModuleSettings.KEY_KNOWN_VIDEO_DETAIL_RELATE_TYPES, copy)
                    .apply()
                log("VideoDetailRelateFilter discovered new card type: $normalized")
            }
        }
    }

    private fun currentTitleKeywords(): List<String> {
        val raw = ModuleSettings.getVideoDetailRelateTitleKeywordsText(prefs)
        if (raw != titleKeywordsRaw) {
            titleKeywordsRaw = raw
            titleKeywordsCache = ModuleSettings.parseVideoDetailRelateTitleKeywords(raw)
        }
        return titleKeywordsCache
    }

    private fun callNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        val method = debugNoArgMethod(target.javaClass, name) ?: return null
        return runCatching { method.invoke(target) }.getOrNull()
    }

    private fun debugNoArgMethod(type: Class<*>, name: String): Method? =
        synchronized(methodCache) {
            val methods = methodCache.getOrPut(type) { mutableMapOf() }
            if (methods.containsKey(name)) {
                methods[name]
            } else {
                val method = type.methods
                    .firstOrNull {
                        it.name == name &&
                            it.parameterCount == 0 &&
                            !Modifier.isStatic(it.modifiers)
                    }
                    ?.apply { isAccessible = true }
                methods[name] = method
                method
            }
        }

    private fun writeBackFilteredItems(target: Any?, field: Field?, items: List<Any?>) {
        if (target == null || field == null) return
        runCatching {
            field.set(target, items)
        }.onFailure { throwable ->
            log("VideoDetailRelateFilter could not update response items field", throwable)
        }
    }
}
