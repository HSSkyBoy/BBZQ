package io.github.bbzq.ui.settings

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.TITLE_KEYWORD_SUMMARY_MAX_ITEMS

internal fun SettingsContentFactory.homeRecommendRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.home_recommend_preload_title),
        context.getString(R.string.home_recommend_preload_summary),
        ModuleSettings.KEY_HOME_RECOMMEND_PRELOAD_ENABLED,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.home_recommend_custom_filter_title),
        context.getString(R.string.home_recommend_custom_filter_summary),
        ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED,
        false,
    ) {
        homeRecommendItemSwitch = it
    }

    rows += createHomeRecommendTitleKeywordRow()
    rows += createInfoRow(
        context.getString(R.string.home_recommend_custom_filter_item_title),
        context.getString(R.string.home_recommend_custom_filter_info_summary),
    )
    rows += createHomeRecommendItemGroup(homeRecommendItems())
    rows += createSwitchRow(
        context.getString(R.string.home_recommend_tab_filter_title),
        context.getString(R.string.home_recommend_tab_filter_summary),
        ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED,
        false,
    ) {
        homeRecommendTabSwitch = it
    }
    val tabs = homeRecommendTabs()
    if (tabs.isEmpty()) {
        rows += createInfoRow(
            context.getString(R.string.home_recommend_tab_item_title),
            context.getString(R.string.home_recommend_tab_unavailable_summary),
        )
    } else {
        rows += createInfoRow(
            context.getString(R.string.home_recommend_tab_item_title),
            context.getString(R.string.home_recommend_tab_info_summary),
        )
        rows += createHomeRecommendTabGroup(tabs)
    }
    rows += createSwitchRow(
        context.getString(R.string.home_recommend_hide_all_title),
        context.getString(R.string.home_recommend_hide_all_summary),
        ModuleSettings.KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED,
        false,
    ) {
        hideAllHomeComponentsSwitch = it
    }
    rows += createSwitchRow(
        context.getString(R.string.home_recommend_custom_hide_title),
        context.getString(R.string.home_recommend_custom_hide_summary),
        ModuleSettings.KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED,
        false,
    ) {
        customHomeComponentHideSwitch = it
    }

    val components = homeComponentItems()
    if (components.isEmpty()) {
        rows += createInfoRow(
            context.getString(R.string.home_component_title),
            context.getString(R.string.home_component_unavailable_summary),
        )
    } else {
        rows += createInfoRow(
            context.getString(R.string.home_component_title),
            context.getString(R.string.home_component_info_summary),
        )
        rows += createHomeComponentGroup(components)
    }
    return rows
}

internal fun SettingsContentFactory.dynamicRows(): List<View> {
    return listOf(
        createSwitchRow(
            context.getString(R.string.dynamic_preferred_video_tab_title),
            context.getString(R.string.dynamic_preferred_video_tab_summary),
            ModuleSettings.KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.dynamic_remove_city_tab_title),
            context.getString(R.string.dynamic_remove_city_tab_summary),
            ModuleSettings.KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.dynamic_remove_school_tab_title),
            context.getString(R.string.dynamic_remove_school_tab_summary),
            ModuleSettings.KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.dynamic_keyword_filter_title),
            context.getString(R.string.dynamic_keyword_filter_summary),
            ModuleSettings.KEY_DYNAMIC_KEYWORD_FILTER_ENABLED,
            false,
        ) {
            dynamicKeywordFilterSwitch = it
        },
        createDynamicKeywordRow(),
    )
}

internal fun SettingsContentFactory.mineProfileRows(): List<View> {
    return listOf(
        createSwitchRow(
            context.getString(R.string.mine_remove_vip_title),
            context.getString(R.string.mine_remove_vip_summary),
            ModuleSettings.KEY_MINE_REMOVE_VIP,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.mine_keep_vip_space_title),
            context.getString(R.string.mine_keep_vip_space_summary),
            ModuleSettings.KEY_MINE_KEEP_VIP_SPACE,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.mine_component_hide_title),
            context.getString(R.string.mine_component_hide_summary),
            ModuleSettings.KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED,
            false,
        ) {
            customMineComponentHideSwitch = it
        },
    ).toMutableList().also { rows ->
        val components = mineComponentItems()
        if (components.isEmpty()) {
            rows += createInfoRow(
                context.getString(R.string.mine_component_title),
                context.getString(R.string.mine_component_unavailable_summary),
            )
        } else {
            rows += createInfoRow(
                context.getString(R.string.mine_component_title),
                context.getString(R.string.mine_component_info_summary),
            )
            rows += createMineComponentPickerRow(components)
        }
        rows += createSwitchRow(
            context.getString(R.string.full_number_format_title),
            context.getString(R.string.full_number_format_summary),
            ModuleSettings.KEY_FULL_NUMBER_FORMAT_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.space_fix_title),
            context.getString(R.string.space_fix_summary),
            ModuleSettings.KEY_SPACE_FIX_ENABLED,
            false,
        )
    }
}

private fun SettingsContentFactory.createHomeRecommendTitleKeywordRow(): View {
    homeRecommendTitleKeywordSummaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showHomeRecommendTitleKeywordDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.home_recommend_title_keyword_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(homeRecommendTitleKeywordSummaryView)
    }.also {
        homeRecommendTitleKeywordRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.home_recommend_title_keyword_title),
            context.getString(R.string.home_recommend_title_keyword_title),
            "action:${context.getString(R.string.home_recommend_title_keyword_title)}",
        )
    }
}

private fun SettingsContentFactory.showHomeRecommendTitleKeywordDialog() {
    val input = EditText(context).apply {
        setText(ModuleSettings.getHomeRecommendTitleKeywordsText(prefs))
        minLines = 4
        maxLines = 8
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setSelectAllOnFocus(false)
        setHint(R.string.home_recommend_title_keyword_hint)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), 0)
        addView(input)
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.home_recommend_title_keyword_dialog_title)
        .setView(content)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            prefs.edit()
                .putString(ModuleSettings.KEY_HOME_RECOMMEND_TITLE_KEYWORDS, input.text?.toString()?.trim().orEmpty())
                .apply()
            refresh()
        }
        .show()
}

private fun SettingsContentFactory.createDynamicKeywordRow(): View {
    dynamicKeywordSummaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showDynamicKeywordDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.dynamic_keyword_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(dynamicKeywordSummaryView)
    }.also {
        dynamicKeywordRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.dynamic_keyword_title),
            context.getString(R.string.dynamic_keyword_title),
            "action:${context.getString(R.string.dynamic_keyword_title)}",
        )
    }
}

private fun SettingsContentFactory.showDynamicKeywordDialog() {
    val input = EditText(context).apply {
        setText(ModuleSettings.getDynamicKeywordsText(prefs))
        minLines = 4
        maxLines = 8
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(false)
        setSelectAllOnFocus(false)
        setHint(R.string.dynamic_keyword_hint)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), 0)
        addView(input)
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.dynamic_keyword_dialog_title)
        .setView(content)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            prefs.edit()
                .putString(ModuleSettings.KEY_DYNAMIC_KEYWORDS, input.text?.toString()?.trim().orEmpty())
                .apply()
            refresh()
        }
        .show()
}

internal fun SettingsContentFactory.dynamicKeywordSummary(): String {
    val keywords = ModuleSettings.parseDynamicKeywords(ModuleSettings.getDynamicKeywordsText(prefs))
    if (keywords.isEmpty()) {
        return context.getString(R.string.dynamic_keyword_empty_summary)
    }
    return context.getString(
        R.string.dynamic_keyword_summary,
        keywords.size,
        keywords.take(TITLE_KEYWORD_SUMMARY_MAX_ITEMS).joinToString(context.getString(R.string.list_separator)),
    )
}

internal fun SettingsContentFactory.createTagGroup(): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        ModuleSettings.storyVideoAdTags.forEach { tag ->
            addView(CheckBox(context).apply {
                text = tag.label
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveSelectedTags()
                }
                tagCheckBoxes[tag.key] = this
            })
        }
    }
}

internal fun SettingsContentFactory.createBottomBarItemGroup(items: List<BottomBarItem>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        items.forEach { item ->
            addView(CheckBox(context).apply {
                text = item.name
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveHiddenBottomBarItems()
                }
                bottomBarItemCheckBoxes[item.id] = this
            })
        }
    }
}

private fun SettingsContentFactory.createHomeRecommendItemGroup(items: List<HomeRecommendItem>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        items.forEach { item ->
            addView(CheckBox(context).apply {
                text = "${item.title}\n${item.summary}"
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveHiddenHomeRecommendItems()
                }
                homeRecommendItemCheckBoxes[item.key] = this
            })
        }
    }
}

private fun SettingsContentFactory.createHomeRecommendTabGroup(items: List<HomeRecommendTabItem>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        items.forEach { item ->
            addView(CheckBox(context).apply {
                text = item.displayText()
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveHiddenHomeRecommendTabs()
                }
                homeRecommendTabCheckBoxes[item.key] = this
            })
        }
    }
}

internal fun SettingsContentFactory.componentPoolRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createInfoRow(
        context.getString(R.string.component_pool_title),
        context.getString(R.string.component_pool_info_summary),
    )
    rows += createSwitchRow(
        context.getString(R.string.component_pool_block_all_title),
        context.getString(R.string.component_pool_block_all_summary),
        ModuleSettings.KEY_BLOCK_ALL_COMPONENT_POOLS_ENABLED,
        false,
    ) {
        blockAllComponentPoolsSwitch = it
    }
    rows += createSwitchRow(
        context.getString(R.string.component_pool_custom_title),
        context.getString(R.string.component_pool_custom_summary),
        ModuleSettings.KEY_CUSTOM_COMPONENT_POOL_BLOCK_ENABLED,
        false,
    ) {
        customComponentPoolBlockSwitch = it
    }
    val pools = componentPoolItems()
    rows += if (pools.isEmpty()) {
        createInfoRow(
            context.getString(R.string.component_pool_list_title),
            context.getString(R.string.component_pool_unavailable_summary),
        )
    } else {
        createComponentPoolGroup(pools)
    }
    return rows
}

private fun SettingsContentFactory.createComponentPoolGroup(items: List<ComponentPoolItem>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        items.forEach { item ->
            addView(CheckBox(context).apply {
                text = context.getString(
                    R.string.component_pool_item_format,
                    item.name,
                    item.moduleCount,
                )
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveBlockedComponentPools()
                }
                componentPoolCheckBoxes[item.name] = this
            })
        }
    }
}

private fun SettingsContentFactory.componentPoolItems(): List<ComponentPoolItem> =
    ModuleSettings.getKnownComponentPools(prefs)
        .mapNotNull(ModuleSettings::decodeComponentPool)
        .map { (name, count) -> ComponentPoolItem(name, count) }
        .distinctBy(ComponentPoolItem::name)
        .sortedBy(ComponentPoolItem::name)

private fun SettingsContentFactory.blockedComponentPoolNames(): Set<String> =
    componentPoolCheckBoxes.filterValues { it.isChecked }.keys.toSet()

private fun SettingsContentFactory.saveBlockedComponentPools() {
    prefs.edit()
        .putStringSet(ModuleSettings.KEY_BLOCKED_COMPONENT_POOLS, blockedComponentPoolNames().toMutableSet())
        .apply()
}

internal data class ComponentPoolItem(val name: String, val moduleCount: Int)

private fun SettingsContentFactory.createHomeComponentGroup(items: List<HomeComponentItem>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        items.forEach { item ->
            addView(CheckBox(context).apply {
                text = "${item.name}\n${item.className}"
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveHiddenHomeComponents()
                }
                homeComponentCheckBoxes[item.className] = this
            })
        }
    }
}

private fun SettingsContentFactory.createMineComponentPickerRow(items: List<MineComponentItem>): View {
    mineComponentPickerSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showMineComponentPickerDialog(items) }
        addView(TextView(context).apply {
            text = context.getString(R.string.mine_component_picker_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(mineComponentPickerSummary)
    }.also {
        mineComponentPickerRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.mine_component_picker_title),
            context.getString(R.string.mine_component_picker_title),
            "action:${context.getString(R.string.mine_component_picker_title)}",
        )
    }
}

private fun SettingsContentFactory.showMineComponentPickerDialog(items: List<MineComponentItem>) {
    val retained = BooleanArray(items.size) { index ->
        items[index].name !in ModuleSettings.getHiddenMineComponents(prefs)
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.mine_component_title)
        .setMultiChoiceItems(items.map(MineComponentItem::name).toTypedArray(), retained) { _, which, isChecked ->
            retained[which] = isChecked
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            val hidden = items.indices
                .filterNot { retained[it] }
                .mapTo(linkedSetOf()) { items[it].name }
            prefs.edit().putStringSet(ModuleSettings.KEY_HIDDEN_MINE_COMPONENTS, hidden).apply()
            refresh()
        }
        .show()
}

private fun SettingsContentFactory.saveSelectedTags() {
    prefs.edit()
        .putStringSet(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_TAGS, selectedTagKeys().toMutableSet())
        .apply()
}

private fun SettingsContentFactory.saveHiddenBottomBarItems() {
    prefs.edit()
        .putStringSet(ModuleSettings.KEY_HIDDEN_BOTTOM_BAR_ITEMS, collectHiddenBottomBarKeys())
        .apply()
}

private fun SettingsContentFactory.saveHiddenHomeRecommendItems() {
    prefs.edit()
        .putStringSet(
            ModuleSettings.KEY_HIDDEN_HOME_RECOMMEND_ITEMS,
            hiddenHomeRecommendItemKeys().toMutableSet(),
        )
        .also { ModuleSettings.clearLegacyHomeRecommendFilterSwitches(it) }
        .apply()
}

private fun SettingsContentFactory.saveHiddenHomeRecommendTabs() {
    prefs.edit()
        .putStringSet(ModuleSettings.KEY_HIDDEN_HOME_RECOMMEND_TABS, hiddenHomeRecommendTabKeys().toMutableSet())
        .apply()
}

private fun SettingsContentFactory.saveHiddenHomeComponents() {
    prefs.edit()
        .putStringSet(ModuleSettings.KEY_HIDDEN_HOME_COMPONENTS, hiddenHomeComponentClassNames().toMutableSet())
        .apply()
}

private fun SettingsContentFactory.selectedTagKeys(): Set<String> =
    tagCheckBoxes.filterValues { it.isChecked }.keys.toSet()

private fun SettingsContentFactory.hiddenBottomBarItemIds(): Set<String> =
    bottomBarItemCheckBoxes.filterValues { !it.isChecked }.keys.toSet()

private fun SettingsContentFactory.collectHiddenBottomBarKeys(): Set<String> {
    val allKnown = ModuleSettings.getKnownBottomBarItems(prefs).mapNotNull(::parseBottomBarItem)
    val hiddenSet = mutableSetOf<String>()
    val oldHidden = ModuleSettings.getHiddenBottomBarItems(prefs)
    val uiItems = bottomBarItems()
    val handledOldKeys = mutableSetOf<String>()
    uiItems.forEach { item ->
        val isChecked = bottomBarItemCheckBoxes[item.id]?.isChecked ?: true
        val matchingKnown = allKnown.filter { k ->
            (item.id.isNotBlank() && item.id.equals(k.id, ignoreCase = true)) ||
                (item.name.isNotBlank() && item.name.equals(k.name, ignoreCase = true)) ||
                (item.uri.isNotBlank() && item.uri.equals(k.uri, ignoreCase = true))
        }
        val itemKeys = buildSet {
            if (item.id.isNotBlank()) add(item.id)
            if (item.name.isNotBlank()) add(item.name)
            if (item.uri.isNotBlank()) add(item.uri)
            matchingKnown.forEach { k ->
                if (k.id.isNotBlank()) add(k.id)
                if (k.name.isNotBlank()) add(k.name)
                if (k.uri.isNotBlank()) add(k.uri)
            }
        }
        handledOldKeys.addAll(itemKeys)
        if (!isChecked) {
            hiddenSet.addAll(itemKeys)
        }
    }
    oldHidden.forEach { oldKey ->
        if (oldKey !in handledOldKeys) {
            hiddenSet.add(oldKey)
        }
    }
    return hiddenSet
}

internal fun SettingsContentFactory.isBottomBarItemHidden(
    item: BottomBarItem,
    hiddenKeys: Set<String>,
    knownItems: Collection<BottomBarItem>,
): Boolean {
    if (hiddenKeys.isEmpty()) return false
    if (item.id in hiddenKeys || item.name in hiddenKeys || (item.uri.isNotBlank() && item.uri in hiddenKeys)) {
        return true
    }
    for (known in knownItems) {
        val matches = (item.id.isNotBlank() && item.id.equals(known.id, ignoreCase = true)) ||
            (item.name.isNotBlank() && item.name.equals(known.name, ignoreCase = true)) ||
            (item.uri.isNotBlank() && item.uri.equals(known.uri, ignoreCase = true))
        if (matches) {
            if (known.id in hiddenKeys || known.name in hiddenKeys || (known.uri.isNotBlank() && known.uri in hiddenKeys)) {
                return true
            }
        }
    }
    return false
}

private fun SettingsContentFactory.hiddenHomeRecommendItemKeys(): Set<String> =
    homeRecommendItemCheckBoxes.filterValues { it.isChecked }.keys.toSet()

private fun SettingsContentFactory.hiddenHomeRecommendTabKeys(): Set<String> =
    homeRecommendTabCheckBoxes.filterValues { it.isChecked }.keys.toSet()

private fun SettingsContentFactory.hiddenHomeComponentClassNames(): Set<String> =
    homeComponentCheckBoxes.filterValues { !it.isChecked }.keys.toSet()

internal fun SettingsContentFactory.homeRecommendTitleKeywordSummary(): String {
    val keywords = ModuleSettings.parseHomeRecommendTitleKeywords(
        ModuleSettings.getHomeRecommendTitleKeywordsText(prefs),
    )
    if (keywords.isEmpty()) {
        return context.getString(R.string.home_recommend_title_keyword_empty_summary)
    }
    return context.getString(
        R.string.home_recommend_title_keyword_enabled_summary,
        keywords.size,
        keywords.take(TITLE_KEYWORD_SUMMARY_MAX_ITEMS).joinToString(context.getString(R.string.list_separator)),
    )
}

internal fun SettingsContentFactory.bottomBarItems(): List<BottomBarItem> =
    ModuleSettings.refreshKnownBottomBarItemsCache(prefs)
        .mapNotNull(::parseBottomBarItem)
        .distinctBy { it.name.ifBlank { it.uri.ifBlank { it.id } } }
        .sortedBy(BottomBarItem::order)

private fun SettingsContentFactory.homeRecommendTabs(): List<HomeRecommendTabItem> =
    ModuleSettings.getKnownHomeRecommendTabs(prefs)
        .mapNotNull(::parseHomeRecommendTabItem)
        .distinctBy(HomeRecommendTabItem::key)
        .sortedBy(HomeRecommendTabItem::order)

private fun SettingsContentFactory.homeRecommendItems(): List<HomeRecommendItem> {
    val items = mutableListOf<HomeRecommendItem>()
    if (hasHiddenFeatures()) {
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_AD,
            context.getString(R.string.home_recommend_filter_ad_title),
            context.getString(R.string.home_recommend_filter_ad_summary),
        )
    }
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_PICTURE,
        context.getString(R.string.home_recommend_filter_picture_title),
        context.getString(R.string.home_recommend_filter_picture_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_GAME_PROMO,
        context.getString(R.string.home_recommend_filter_game_promo_title),
        context.getString(R.string.home_recommend_filter_game_promo_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_LIVE,
        context.getString(R.string.home_recommend_filter_live_title),
        context.getString(R.string.home_recommend_filter_live_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_KETANG,
        context.getString(R.string.home_recommend_filter_ketang_title),
        context.getString(R.string.home_recommend_filter_ketang_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_BANGUMI,
        context.getString(R.string.home_recommend_filter_bangumi_title),
        context.getString(R.string.home_recommend_filter_bangumi_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_VERTICAL_AV,
        context.getString(R.string.home_recommend_filter_vertical_av_title),
        context.getString(R.string.home_recommend_filter_vertical_av_summary),
    )
    items += HomeRecommendItem(
        ModuleSettings.HOME_RECOMMEND_FILTER_LARGE_COVER,
        context.getString(R.string.home_recommend_filter_large_cover_title),
        context.getString(R.string.home_recommend_filter_large_cover_summary),
    )
    return items
}

private fun SettingsContentFactory.homeComponentItems(): List<HomeComponentItem> =
    ModuleSettings.getKnownHomeComponents(prefs)
        .mapNotNull(::parseHomeComponentItem)
        .distinctBy(HomeComponentItem::className)
        .sortedWith(compareBy<HomeComponentItem> { it.order }.thenBy { it.name }.thenBy { it.className })

internal fun SettingsContentFactory.mineComponentItems(): List<MineComponentItem> =
    ModuleSettings.getKnownMineComponents(prefs)
        .filter { it.isNotBlank() && !ModuleSettings.isMineSettingsTitle(it) }
        .map(::MineComponentItem)
        .distinctBy(MineComponentItem::name)
        .sortedBy(MineComponentItem::name)

internal fun parseBottomBarItem(raw: String): BottomBarItem? {
    val parts = raw.split('\t', limit = 4)
    if (parts.size == 4) {
        val order = parts[0].toIntOrNull() ?: return null
        if (parts[1].isBlank() || !isBottomBarName(parts[2])) return null
        return BottomBarItem(order, parts[1], parts[2], parts[3])
    }
    if (parts.size == 3) {
        if (parts[0].isBlank() || !isBottomBarName(parts[1])) return null
        return BottomBarItem(Int.MAX_VALUE, parts[0], parts[1], parts[2])
    }
    return null
}

private fun isBottomBarName(value: String): Boolean =
    value.isNotBlank() && !looksLikeBottomBarUri(value)

private fun looksLikeBottomBarUri(value: String): Boolean =
    "://" in value ||
        value.startsWith("bilibili://", ignoreCase = true) ||
        value.startsWith("activity://", ignoreCase = true) ||
        value.startsWith("http://", ignoreCase = true) ||
        value.startsWith("https://", ignoreCase = true)

private fun parseHomeRecommendTabItem(raw: String): HomeRecommendTabItem? {
    val parts = raw.split('\t', limit = 5)
    if (parts.size == 5) {
        val order = parts[0].toIntOrNull() ?: return null
        return HomeRecommendTabItem(order, parts[1], parts[2], parts[3], parts[4])
    }
    if (parts.size == 4) {
        val order = parts[0].toIntOrNull() ?: return null
        return HomeRecommendTabItem(order, parts[1], parts[2], parts[3], "")
    }
    return null
}

private fun parseHomeComponentItem(raw: String): HomeComponentItem? {
    val parts = raw.split('\t', limit = 3)
    if (parts.size != 3) return null
    val order = parts[0].toIntOrNull() ?: return null
    return HomeComponentItem(order, parts[1], parts[2])
}

internal data class BottomBarItem(
    val order: Int,
    val id: String,
    val name: String,
    val uri: String,
)

internal data class HomeRecommendItem(
    val key: String,
    val title: String,
    val summary: String,
)

internal data class HomeRecommendTabItem(
    val order: Int,
    val key: String,
    val name: String,
    val uri: String,
    val reporterId: String,
) {
    fun displayText(): String {
        val details = listOf(uri, reporterId)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        return if (details.isBlank()) name else "$name\n$details"
    }
}

internal data class HomeComponentItem(
    val order: Int,
    val name: String,
    val className: String,
)

internal data class MineComponentItem(val name: String)
