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
    private val mineSymbols: RestoredMineProfileSymbols? by lazy(LazyThreadSafetyMode.NONE) {
        env.symbols?.mineProfile?.restore(classLoader)
    }

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

        hookMineComponentData()
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

    private fun purifyMineData(mineData: Any) {
        val components = linkedSetOf<String>()
        val hidden = if (ModuleSettings.isCustomMineComponentHideEnabled(prefs)) {
            ModuleSettings.getHiddenMineComponents(prefs)
        } else {
            emptySet()
        }

        // AccountMineV2 structure: sectionListV2 -> itemList -> title
        val sectionListV2Field = runCatching { mineData.javaClass.getDeclaredField("sectionListV2").apply { isAccessible = true } }.getOrNull()
        if (sectionListV2Field != null) {
            val sections = runCatching { sectionListV2Field.get(mineData) as? MutableList<*> }.getOrNull()
            sections?.forEach { section ->
                if (section == null) return@forEach
                val itemListField = runCatching { section.javaClass.getDeclaredField("itemList").apply { isAccessible = true } }.getOrNull()
                if (itemListField != null) {
                    val itemList = runCatching { itemListField.get(section) as? MutableList<*> }.getOrNull()
                    itemList?.removeAll { item ->
                        if (item == null) return@removeAll false
                        val titleField = runCatching { item.javaClass.getDeclaredField("title").apply { isAccessible = true } }.getOrNull()
                        if (titleField != null) {
                            val title = runCatching { titleField.get(item) as? String }.getOrNull()?.trim()
                            if (!title.isNullOrBlank()) {
                                components.add(title)
                                return@removeAll title in hidden
                            }
                        }
                        false
                    }
                }
            }
        }

        // AccountMine legacy structure: padSectionList, recommendSectionList, moreSectionList
        val legacyLists = listOf("padSectionList", "recommendSectionList", "moreSectionList")
        legacyLists.forEach { fieldName ->
            val listField = runCatching { mineData.javaClass.getDeclaredField(fieldName).apply { isAccessible = true } }.getOrNull()
            if (listField != null) {
                val list = runCatching { listField.get(mineData) as? MutableList<*> }.getOrNull()
                list?.removeAll { item ->
                    if (item == null) return@removeAll false
                    val titleField = runCatching { item.javaClass.getDeclaredField("title").apply { isAccessible = true } }.getOrNull()
                    if (titleField != null) {
                        val title = runCatching { titleField.get(item) as? String }.getOrNull()?.trim()
                        if (!title.isNullOrBlank()) {
                            components.add(title)
                            return@removeAll title in hidden
                        }
                    }
                    false
                }
            }
        }

        if (components.isNotEmpty()) {
            saveKnownComponents(components)
        }
    }

    private fun saveKnownComponents(names: Set<String>) {
        val known = ModuleSettings.getKnownMineComponents(prefs).toMutableSet()
        if (!known.addAll(names)) return
        ModuleSettings.cacheKnownMineComponents(known)
        prefs.edit().putStringSet(ModuleSettings.KEY_KNOWN_MINE_COMPONENTS, known).apply()
    }
}
