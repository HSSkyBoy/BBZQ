package io.github.bbzq.feats.hook

import android.view.View
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.symbol.RestoredMineProfileSymbols
import kotlin.LazyThreadSafetyMode
import java.util.Collections
import java.util.WeakHashMap

class MineProfileHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val mineSymbols: RestoredMineProfileSymbols? by lazy(LazyThreadSafetyMode.NONE) {
        env.symbols?.mineProfile?.restore(classLoader)
    }
    private val seenMenuTitles: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val masterMenuLists: MutableMap<Any, MutableList<Any?>> = Collections.synchronizedMap(WeakHashMap())
    private var composeVipInstalled = false

    override fun startHook() {
        if (env.processName != env.packageName) return

        val symbols = mineSymbols
        if (symbols != null) {
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
        } else {
            log("MineProfileHook: Mine profile symbols missing for VIP hook")
        }

        if (!composeVipInstalled) {
            composeVipInstalled = env.hookComposeMineVip()
            if (!composeVipInstalled) hookVipEntranceState()
        }
        hookMineComponentData()
        hookMenuGroupItems()
    }

    /**
     * Both the View page (onViewCreated) and the Compose pages (MineVipModule in mine/v2 and v3)
     * only build the VIP card when the "vip" entrance state is non-zero.
     */
    private fun hookVipEntranceState() {
        val method = classLoader.findClassOrNull(TEENAGERS_MODE_CLASS)
            ?.declaredMethods
            ?.firstOrNull {
                it.name == "getEntranceState" &&
                    it.parameterTypes.contentEquals(arrayOf(String::class.java)) &&
                    it.returnType == Int::class.javaPrimitiveType
            }
            ?: return log("MineProfileHook: TeenagersMode.getEntranceState not found, VIP entrance hook skipped")
        env.hookBefore(method) { param ->
            if (param.args.firstOrNull() != VIP_ENTRANCE_KEY) return@hookBefore
            if (!ModuleSettings.isMineRemoveVipEnabled(prefs)) return@hookBefore
            // The View page keeps the card's space through the onResume hook instead.
            if (ModuleSettings.isMineKeepVipSpaceEnabled(prefs) && isCalledFromViewPage()) return@hookBefore
            param.result = 0
        }
    }

    private fun isCalledFromViewPage(): Boolean =
        Thread.currentThread().stackTrace.any { it.className == VIEW_PAGE_FRAGMENT_CLASS }

    /**
     * mine/v2 and v3 decode AccountMineV2 with kotlinx, so filter the menu groups at their getter.
     * The host mutates the returned list in place (e.g. appends the local "设置" entry to the last group).
     * We keep a non-destructive master copy so unhiding restores items correctly, and ensure "设置" is never hidden.
     */
    private fun hookMenuGroupItems() {
        val getItemList = classLoader.findClassOrNull(MENU_GROUP_CLASS)
            ?.declaredMethods
            ?.firstOrNull { it.name == "getItemList" && it.parameterCount == 0 && List::class.java.isAssignableFrom(it.returnType) }
            ?: return log("MineProfileHook: MenuGroupV2.getItemList not found, menu item hook skipped")
        val getTitle = classLoader.findClassOrNull(MENU_ITEM_CLASS)
            ?.declaredMethods
            ?.firstOrNull { it.name == "getTitle" && it.parameterCount == 0 && it.returnType == String::class.java }
            ?: return log("MineProfileHook: MenuGroupV2.Item.getTitle not found, menu item hook skipped")
        val getUri = classLoader.findClassOrNull(MENU_ITEM_CLASS)
            ?.declaredMethods
            ?.firstOrNull { it.name == "getUri" && it.parameterCount == 0 && it.returnType == String::class.java }
        val setLocalShow = classLoader.findClassOrNull(MENU_ITEM_CLASS)
            ?.declaredMethods
            ?.firstOrNull { it.name == "setLocalShow" && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) }

        env.hookAfter(getItemList) { param ->
            val group = param.thisObject ?: return@hookAfter
            @Suppress("UNCHECKED_CAST")
            val items = param.result as? MutableList<Any?> ?: return@hookAfter

            val master = masterMenuLists.getOrPut(group) { ArrayList(items) }
            for (item in items) {
                if (item != null && !master.contains(item)) {
                    master.add(item)
                }
            }

            val hidden = if (ModuleSettings.isCustomMineComponentHideEnabled(prefs)) {
                ModuleSettings.getHiddenMineComponents(prefs)
            } else {
                emptySet()
            }
            val titles = linkedSetOf<String>()
            val kept = ArrayList<Any?>()

            for (item in master) {
                if (item == null) {
                    kept.add(item)
                    continue
                }
                val title = runCatching { getTitle.invoke(item) as? String }.getOrNull()?.trim()
                val uri = runCatching { getUri?.invoke(item) as? String }.getOrNull()?.trim()
                val isSettings = isMineSettingsItem(title, uri)

                if (!title.isNullOrBlank() && !isSettings) {
                    titles.add(title)
                }

                val isHidden = !isSettings && !title.isNullOrBlank() && title in hidden
                runCatching { setLocalShow?.invoke(item, !isHidden) }

                if (!isHidden) {
                    kept.add(item)
                }
            }

            if (titles.isNotEmpty() && seenMenuTitles.addAll(titles)) {
                saveKnownComponents(titles)
            }

            runCatching {
                items.clear()
                items.addAll(kept)
            }
            param.result = items
        }
    }

    private fun hookMineComponentData() {
        val jsonClass = sequenceOf("com.alibaba.fastjson.JSON", "com.alibaba.fastjson2.JSON")
            .mapNotNull { name -> runCatching { classLoader.loadClass(name) }.getOrNull() }
            .firstOrNull()
        if (jsonClass == null) {
            log("MineProfileHook: FastJson class not found, component hook skipped")
            return
        }

        jsonClass.methods.filter {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.name == "parseObject" &&
                    it.parameterCount >= 2 &&
                    it.parameterTypes[0] == String::class.java
        }.forEach { method ->
            env.hookAfter(method) { param ->
                val result = param.result ?: return@hookAfter
                processParsedResult(result)
            }
        }
    }

    private fun processParsedResult(result: Any) {
        var data = result
        if (data.javaClass.name.endsWith(".GeneralResponse")) {
            val dataField = data.javaClass.methods.firstOrNull { it.name == "getData" }
                ?.let { runCatching { it.invoke(data) }.getOrNull() }
                ?: runCatching { 
                    data.javaClass.getDeclaredField("data").apply { isAccessible = true }.get(data) 
                }.getOrNull()
            
            if (dataField != null) {
                data = dataField
            } else {
                return
            }
        }

        val className = data.javaClass.name
        if (className == "tv.danmaku.bili.ui.main2.api.AccountMine" || className == "tv.danmaku.bili.ui.main2.api.AccountMineV2") {
            purifyMineData(data)
        }
    }

    private fun purifyMineData(mineData: Any) = runCatching {
        val components = linkedSetOf<String>()
        val hidden = if (ModuleSettings.isCustomMineComponentHideEnabled(prefs)) {
            ModuleSettings.getHiddenMineComponents(prefs)
        } else {
            emptySet()
        }

        // AccountMineV2 structure: sectionListV2 -> itemList -> title
        val sectionListV2Field = runCatching { mineData.javaClass.getDeclaredField("sectionListV2").apply { isAccessible = true } }.getOrNull()
        if (sectionListV2Field != null) {
            val sections = runCatching { sectionListV2Field.get(mineData) as? List<*> }.getOrNull()
            sections?.forEach { section ->
                if (section == null) return@forEach
                val itemListField = runCatching { section.javaClass.getDeclaredField("itemList").apply { isAccessible = true } }.getOrNull()
                if (itemListField != null) {
                    val rawList = runCatching { itemListField.get(section) as? List<*> }.getOrNull()
                    if (rawList != null) {
                        val toKeep = ArrayList<Any?>()
                        var modified = false
                        rawList.forEach { item ->
                            if (item == null) {
                                toKeep.add(item)
                                return@forEach
                            }
                            val titleField = runCatching { item.javaClass.getDeclaredField("title").apply { isAccessible = true } }.getOrNull()
                            val title = titleField?.let { runCatching { it.get(item) as? String }.getOrNull() }?.trim()
                            val isSettings = isMineSettingsItem(title, null)
                            if (!title.isNullOrBlank() && !isSettings) {
                                components.add(title)
                                if (title in hidden) {
                                    modified = true
                                    return@forEach
                                }
                            }
                            toKeep.add(item)
                        }
                        if (modified) {
                            val success = runCatching {
                                (rawList as? MutableList<Any?>)?.clear()
                                (rawList as? MutableList<Any?>)?.addAll(toKeep)
                                true
                            }.getOrDefault(false)
                            if (!success) {
                                runCatching { itemListField.set(section, toKeep) }
                            }
                        }
                    }
                }
            }
        }

        // AccountMine legacy structure: padSectionList, recommendSectionList, moreSectionList
        val legacyLists = listOf("padSectionList", "recommendSectionList", "moreSectionList")
        legacyLists.forEach { fieldName ->
            val listField = runCatching { mineData.javaClass.getDeclaredField(fieldName).apply { isAccessible = true } }.getOrNull()
            if (listField != null) {
                val rawList = runCatching { listField.get(mineData) as? List<*> }.getOrNull()
                if (rawList != null) {
                    val toKeep = ArrayList<Any?>()
                    var modified = false
                    rawList.forEach { item ->
                        if (item == null) {
                            toKeep.add(item)
                            return@forEach
                        }
                        val titleField = runCatching { item.javaClass.getDeclaredField("title").apply { isAccessible = true } }.getOrNull()
                        val title = titleField?.let { runCatching { it.get(item) as? String }.getOrNull() }?.trim()
                        val isSettings = isMineSettingsItem(title, null)
                        if (!title.isNullOrBlank() && !isSettings) {
                            components.add(title)
                            if (title in hidden) {
                                modified = true
                                return@forEach
                            }
                        }
                        toKeep.add(item)
                    }
                    if (modified) {
                        val success = runCatching {
                            (rawList as? MutableList<Any?>)?.clear()
                            (rawList as? MutableList<Any?>)?.addAll(toKeep)
                            true
                        }.getOrDefault(false)
                        if (!success) {
                            runCatching { listField.set(mineData, toKeep) }
                        }
                    }
                }
            }
        }

        if (components.isNotEmpty()) {
            saveKnownComponents(components)
        }
    }.onFailure { throwable ->
        log("purifyMineData error", throwable)
    }

    private fun isMineSettingsItem(title: String?, uri: String?): Boolean {
        if (ModuleSettings.isMineSettingsTitle(title)) return true
        if (uri != null && (uri.contains("preference") || uri.contains("setting"))) return true
        return false
    }

    private fun saveKnownComponents(names: Set<String>) {
        val filtered = names.filter { !isMineSettingsItem(it, null) }
        val known = ModuleSettings.getKnownMineComponents(prefs).toMutableSet()
        if (!known.addAll(filtered)) return
        ModuleSettings.cacheKnownMineComponents(known)
        prefs.edit().putStringSet(ModuleSettings.KEY_KNOWN_MINE_COMPONENTS, known).apply()
    }

    private companion object {
        private const val TEENAGERS_MODE_CLASS = "com.bilibili.teenagersmode.TeenagersMode"
        private const val VIEW_PAGE_FRAGMENT_CLASS = "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment"
        private const val MENU_GROUP_CLASS = "com.bilibili.lib.homepage.mine.MenuGroupV2"
        private const val MENU_ITEM_CLASS = "com.bilibili.lib.homepage.mine.MenuGroupV2\$Item"
        private const val VIP_ENTRANCE_KEY = "vip"
    }
}
