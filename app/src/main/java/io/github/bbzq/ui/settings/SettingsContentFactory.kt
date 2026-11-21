package io.github.bbzq.ui.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.SettingsActivity
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.prefs.ModuleRemotePreferences
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.PROJECT_REPOSITORY_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.RELEASE_PAGE_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.SUPPORTED_HOST_PACKAGES
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.TELEGRAM_CHANNEL_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.TITLE_KEYWORD_SUMMARY_MAX_ITEMS
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.UNKNOWN_RUNTIME_VALUE
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.VERSION_TAP_WINDOW_MS
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.hiddenSearchPages
import okhttp3.Call
import org.json.JSONObject

class SettingsContentFactory(
    internal val context: Context,
    internal val prefs: SharedPreferences,
    internal val page: String,
    internal val hostEntry: Boolean = true,
    internal val openPage: (String) -> Unit,
    internal val onExportClick: () -> Unit,
    internal val onImportClick: () -> Unit,
    internal val onCustomSkinImportClick: () -> Unit,
    internal val onOpenEnabledSettings: () -> Unit = {},
) {
    internal val pageBackgroundColor: Int by lazy { context.getColor(R.color.page_background) }

    internal val titleTextColor: Int by lazy { context.getColor(R.color.title_text) }

    internal val summaryTextColor: Int by lazy { context.getColor(R.color.summary_text) }

    internal val sectionLabelColor: Int by lazy { context.getColor(R.color.section_label) }

    internal val cardBackgroundColor: Int by lazy { context.getColor(R.color.card_background) }

    internal val dividerColor: Int by lazy { context.getColor(R.color.divider) }

    internal val disableConfirmColor: Int by lazy { context.getColor(R.color.disable_confirm) }

    internal val cancelActionColor: Int by lazy { context.getColor(R.color.cancel_action) }

    internal val tagCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val bottomBarItemCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val homeRecommendItemCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val homeRecommendTabCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val homeComponentCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val componentPoolCheckBoxes = mutableMapOf<String, CheckBox>()

    internal lateinit var blockAllComponentPoolsSwitch: Switch

    internal lateinit var customComponentPoolBlockSwitch: Switch

    internal val videoDetailRelateTypeCheckBoxes = mutableMapOf<String, CheckBox>()

    internal val sponsorBlockCategoryButtons = mutableMapOf<String, Button>()

    internal lateinit var videoDetailRelateFilterSwitch: Switch

    internal lateinit var videoDetailRelateTitleKeywordRow: View

    internal lateinit var videoDetailRelateTitleKeywordSummaryView: TextView

    internal lateinit var disableLongPressCopySwitch: Switch

    internal lateinit var enhanceLongPressCopySwitch: Switch

    internal lateinit var downloadThreadSwitch: Switch

    internal lateinit var downloadConcurrencyRow: View

    internal lateinit var downloadConcurrencySummary: TextView

    internal lateinit var bottomBarSwitch: Switch

    internal lateinit var customThemeSwitch: Switch

    internal lateinit var customThemeMonetSwitch: Switch

    internal lateinit var customSkinSwitch: Switch

    internal lateinit var customFontScaleSwitch: Switch

    internal lateinit var customFontScaleRow: View

    internal lateinit var customFontScaleSeekBar: SeekBar

    internal lateinit var customFontScaleSummary: TextView

    internal lateinit var customThemeColorRow: View

    internal lateinit var customThemeColorSummary: TextView

    internal lateinit var customThemeColorSwatch: View

    internal lateinit var customSkinConfigRow: View

    internal lateinit var customSkinConfigSummary: TextView

    internal lateinit var commentKeywordFilterSwitch: Switch

    internal lateinit var commentKeywordRow: View

    internal lateinit var commentKeywordSummary: TextView

    internal lateinit var commentMinLevelSwitch: Switch

    internal lateinit var commentMinLevelRow: View

    internal lateinit var commentMinLevelSummary: TextView

    internal lateinit var homeRecommendItemSwitch: Switch

    internal lateinit var homeRecommendTitleKeywordRow: View

    internal lateinit var homeRecommendTabSwitch: Switch

    internal lateinit var homeRecommendTitleKeywordSummaryView: TextView

    internal lateinit var dynamicKeywordFilterSwitch: Switch

    internal lateinit var dynamicKeywordRow: View

    internal lateinit var dynamicKeywordSummaryView: TextView

    internal val seaServerRows = mutableMapOf<ResolverRegion, View>()

    internal val seaServerSummaries = mutableMapOf<ResolverRegion, TextView>()

    internal lateinit var seaRelaxSwitch: Switch

    internal lateinit var seaCdnAutoSwitch: Switch

    internal val seaCdnSummaries = mutableMapOf<ResolverRegion, TextView>()

    internal val seaCdnRows = mutableMapOf<ResolverRegion, View>()

    internal lateinit var seaDefaultRegionRow: View

    internal lateinit var seaDefaultRegionSummary: TextView

    internal lateinit var seaSubtitleSwitch: Switch

    internal lateinit var seaResolverModeRow: View

    internal lateinit var seaResolverModeSummaryView: TextView

    internal lateinit var seaSearchSwitch: Switch

    internal lateinit var seaPlaySwitch: Switch

    internal lateinit var seaDetailSwitch: Switch

    internal lateinit var seaAccessKeySwitch: Switch

    internal lateinit var hideAllHomeComponentsSwitch: Switch

    internal lateinit var customHomeComponentHideSwitch: Switch

    internal lateinit var storyVideoAdSwitch: Switch

    internal lateinit var storyVideoImmersiveFullscreenSwitch: Switch

    internal lateinit var storyVideoComponentAlphaSummary: TextView

    internal lateinit var storyVideoComponentAlphaSeekBar: SeekBar

    internal lateinit var customLongPressSpeedSwitch: Switch

    internal lateinit var customLongPressSpeedRow: View

    internal lateinit var customLongPressSpeedSummary: TextView

    internal lateinit var customLongPressSpeedSeekBar: SeekBar

    internal lateinit var skipVideoAdAutoLikeSwitch: Switch

    internal lateinit var blockedCountView: TextView

    internal lateinit var customMineComponentHideSwitch: Switch

    internal lateinit var mineComponentPickerRow: View

    internal lateinit var mineComponentPickerSummary: TextView

    internal lateinit var symbolScanStatusSummary: TextView

    internal lateinit var customCdnWifiSummary: TextView

    internal lateinit var customCdnCellularSummary: TextView

    internal lateinit var halfScreenQualitySummary: TextView

    internal lateinit var fullScreenQualitySummary: TextView

    /** 「检查更新」行的摘要文本视图，用于回显检查状态；界面销毁时置空避免泄漏。 */
    internal var updateCheckSummaryView: TextView? = null

    /** 是否正在检查更新，用于去重并发点击。 */
    internal var updateChecking = false

    /** 当前在途的检查更新请求，界面销毁时取消。 */
    internal var updateCheckCall: Call? = null

    internal var versionTapCount = 0

    internal var firstVersionTapAt = 0L

    internal var refreshing = false

    internal val searchTargets = mutableListOf<SettingsSearchTarget>()

    internal var currentSearchSection: String = ""

    internal var indexSearchTargets = true

    internal var indexPage: String = page

    internal var globalIndexed = false

    fun collectSearchTargets(): List<SettingsSearchTarget> {
        if (page == SettingsActivity.PAGE_ROOT) indexAllPages()
        return searchTargets.map { target ->
            val provider = target.isEnabledProvider
            if (provider != null) {
                target.copy(item = target.item.copy(enabled = provider()))
            } else {
                target
            }
        }
    }

    fun findSearchTarget(key: String): SettingsSearchTarget? =
        searchTargets.firstOrNull { it.item.key == key }

    // 首页只展示分类入口，设置项的搜索索引通过离屏构建各分类页的内容得到。
    internal fun indexAllPages() {
        if (globalIndexed) return
        globalIndexed = true
        indexSearchTargets = true
        SettingsCategory.entries.forEach { category ->
            indexPage = category.page
            categorySections(category).forEach { spec ->
                currentSearchSection = spec.label
                spec.build()
            }
        }
        if (hasHiddenFeatures()) {
            indexPage = SettingsActivity.PAGE_SEA_BANGUMI_UNLOCK
            seaSections().forEach { spec ->
                currentSearchSection = spec.label
                spec.build()
            }
        }
        indexPage = page
        registerRemoteSearchTargets()
    }

    internal fun LinearLayout.addSections(specs: List<SectionSpec>) {
        specs.forEach { addSettingsSection(it.label, it.build) }
    }

    internal fun categorySections(category: SettingsCategory): List<SectionSpec> = when (category) {
        SettingsCategory.APPEARANCE -> listOf(
            SectionSpec(context.getString(R.string.section_ui_customize)) { appearanceRows() },
        )
        SettingsCategory.SHARE -> listOf(
            SectionSpec(context.getString(R.string.section_share_link)) { shareRows() },
            SectionSpec(context.getString(R.string.section_copy_enhance)) { copyRows() },
        )
        SettingsCategory.STARTUP -> listOf(
            SectionSpec(context.getString(R.string.section_startup_purify)) { startupRows() },
        )
        SettingsCategory.NETWORK -> listOf(
            SectionSpec(context.getString(R.string.section_download_features)) { downloadRows() },
            SectionSpec(context.getString(R.string.section_video_cdn)) { customCdnRows() },
            SectionSpec(context.getString(R.string.section_component_pool)) { componentPoolRows() },
        )
        SettingsCategory.HOME -> listOf(
            SectionSpec(context.getString(R.string.section_home_recommend_purify)) { homeRecommendRows() },
        )
        SettingsCategory.SEARCH -> listOf(
            SectionSpec(context.getString(R.string.section_search)) { searchRows() },
        )
        SettingsCategory.PLAYBACK -> listOf(
            SectionSpec(context.getString(R.string.section_playback_promo)) { playbackPromoRows() },
            SectionSpec(context.getString(R.string.section_playback_player)) { playbackPlayerRows() },
            SectionSpec(context.getString(R.string.section_playback_speed)) { playbackSpeedRows() },
            SectionSpec(context.getString(R.string.section_playback_recommend)) { playbackRecommendRows() },
            SectionSpec(context.getString(R.string.section_playback_interact)) { playbackInteractRows() },
        )
        SettingsCategory.STORY -> listOf(
            SectionSpec(context.getString(R.string.section_story_purify)) { storyRows() },
        )
        SettingsCategory.LIVE -> listOf(
            SectionSpec(context.getString(R.string.section_live)) { liveRows() },
        )
        SettingsCategory.COMMENT -> listOf(
            SectionSpec(context.getString(R.string.section_comment_purify)) { commentRows() },
        )
        SettingsCategory.DYNAMIC -> listOf(
            SectionSpec(context.getString(R.string.section_dynamic_page)) { dynamicRows() },
        )
        SettingsCategory.MINE -> listOf(
            SectionSpec(context.getString(R.string.section_mine_customize)) { mineProfileRows() },
        )
    }

    fun createScrollView(): ScrollView {
        searchTargets.clear()
        globalIndexed = false
        indexPage = page
        currentSearchSection = ""
        indexSearchTargets = page !in hiddenSearchPages
        val pageRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackgroundColor)
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }

        when (page) {
            SettingsActivity.PAGE_SKIP_VIDEO_AD_SWITCH -> {
                pageRoot.addSettingsSection(context.getString(R.string.section_skip_video_ad)) {
                    skipVideoAdOverviewRows()
                }
                pageRoot.addSettingsSection(context.getString(R.string.section_thanks)) {
                    skipVideoAdCreditRows()
                }
            }

            SettingsActivity.PAGE_SKIP_VIDEO_AD_CATEGORY -> {
                pageRoot.addSettingsSection(context.getString(R.string.section_category_filter)) {
                    skipVideoAdCategoryRows()
                }
                pageRoot.addSettingsSection(context.getString(R.string.section_thanks)) {
                    skipVideoAdCreditRows()
                }
            }

            SettingsActivity.PAGE_HIDDEN_FEATURES -> {
                pageRoot.addSettingsSection(context.getString(R.string.about_hidden_features_title)) {
                    hiddenFeaturesRows()
                }
            }

            SettingsActivity.PAGE_UPDATE -> {
                pageRoot.addSettingsSection(context.getString(R.string.about_update_title)) {
                    updateRows()
                }
            }

            SettingsActivity.PAGE_CONFIG_BACKUP -> {
                pageRoot.addSettingsSection(context.getString(R.string.about_config_backup_title)) {
                    configBackupRows()
                }
            }

            SettingsActivity.PAGE_SEA_BANGUMI_UNLOCK -> pageRoot.addSections(seaSections())

            SettingsActivity.PAGE_ABOUT -> {
                pageRoot.addSettingsSection(context.getString(R.string.section_about)) { aboutRows() }
            }

            SettingsActivity.PAGE_ROOT -> {
                // 首页的入口行只负责跳转，不进入搜索索引。
                indexSearchTargets = false
                pageRoot.addView(createStatusCard())
                pageRoot.addSettingsSection(context.getString(R.string.home_section_general)) {
                    categoryEntryRows(general = true)
                }
                pageRoot.addSettingsSection(context.getString(R.string.home_section_pages)) {
                    categoryEntryRows(general = false)
                }
                if (hasHiddenFeatures()) {
                    pageRoot.addSettingsSection(context.getString(R.string.home_section_advanced)) {
                        advancedEntryRows()
                    }
                }
                pageRoot.addSettingsSection(context.getString(R.string.home_section_more)) { moduleEntryRows() }
                indexSearchTargets = true
            }

            else -> {
                val category = SettingsCategory.forPage(page)
                if (category != null) pageRoot.addSections(categorySections(category))
            }
        }
        registerRemoteSearchTargets()

        return ScrollView(context).apply {
            setBackgroundColor(pageBackgroundColor)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                pageRoot,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }.also { refresh() }
    }

    /** 界面销毁时调用：取消在途请求并断开视图引用，避免回调持有已销毁的 Activity。 */
    fun destroy() {
        updateCheckCall?.cancel()
        updateCheckCall = null
        updateCheckSummaryView = null
    }

    internal fun startSymbolCacheRefresh() {
        val appContext = context.applicationContext ?: context
        Toast.makeText(appContext, R.string.symbol_cache_refresh_running_toast, Toast.LENGTH_SHORT).show()
        ModuleRemotePreferences.requestSymbolCacheRefresh { message ->
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            if (::symbolScanStatusSummary.isInitialized) {
                symbolScanStatusSummary.text = symbolScanSummary()
            }
        }
        launchHostAppForSymbolRefresh(appContext)
    }

    internal fun LinearLayout.addSettingsSection(section: String, buildRows: () -> List<View>) {
        currentSearchSection = section
        val rows = buildRows()
        if (rows.isEmpty()) return
        addView(createSectionLabel(section))
        addView(createSectionCard(rows))
    }

    internal fun restoreSkipVideoAdAutoLikeEnabled() {
        prefs.edit().putBoolean(ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, true).apply()
        ModuleSettings.refreshSkipVideoAdCache(prefs)
        if (::skipVideoAdAutoLikeSwitch.isInitialized) {
            refreshing = true
            skipVideoAdAutoLikeSwitch.isChecked = true
            refreshing = false
        }
    }

    internal fun refresh() {
        refreshing = true

        val storyEnabled = ModuleSettings.isPurifyStoryVideoAdEnabled(prefs)
        val selectedTags = ModuleSettings.getPurifyStoryVideoAdTags(prefs)
        val copyBaseEnabled = ModuleSettings.isDisableLongPressCopyEnabled(prefs)
        val copyEnhanceEnabled = copyBaseEnabled && ModuleSettings.isEnhanceLongPressCopyEnabled(prefs)
        val bottomBarEnabled = ModuleSettings.isCustomBottomBarEnabled(prefs)
        val hiddenBottomBarItems = ModuleSettings.getHiddenBottomBarItems(prefs)
        val homeRecommendFilterEnabled = ModuleSettings.isCustomHomeRecommendFilterEnabled(prefs)
        val hiddenHomeRecommendItems = ModuleSettings.getHiddenHomeRecommendItems(prefs)
        val homeRecommendTabFilterEnabled = ModuleSettings.isCustomHomeRecommendTabFilterEnabled(prefs)
        val hiddenHomeRecommendTabs = ModuleSettings.getHiddenHomeRecommendTabs(prefs)
        val hideAllHomeComponentsEnabled = ModuleSettings.isHideAllHomeComponentsEnabled(prefs)
        val customHomeComponentHideEnabled = ModuleSettings.isCustomHomeComponentHideEnabled(prefs)
        val hiddenHomeComponents = ModuleSettings.getHiddenHomeComponents(prefs)
        val customMineComponentHideEnabled = ModuleSettings.isCustomMineComponentHideEnabled(prefs)
        val hiddenMineComponents = ModuleSettings.getHiddenMineComponents(prefs)
        val sponsorBlockEnabled = ModuleSettings.isSkipVideoAdEnabled(prefs)
        val skipVideoAdAutoLikeEnabled = ModuleSettings.isSkipVideoAdAutoLikeEnabled(prefs)

        if (!copyBaseEnabled && prefs.getBoolean(ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, false)) {
            prefs.edit().putBoolean(ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, false).apply()
        }

        if (::disableLongPressCopySwitch.isInitialized) {
            disableLongPressCopySwitch.isChecked = copyBaseEnabled
        }
        if (::enhanceLongPressCopySwitch.isInitialized) {
            enhanceLongPressCopySwitch.isEnabled = copyBaseEnabled
            enhanceLongPressCopySwitch.isChecked = copyEnhanceEnabled
        }
        val downloadEnabled = ModuleSettings.isCustomDownloadThreadEnabled(prefs)
        if (::downloadThreadSwitch.isInitialized) {
            downloadThreadSwitch.isChecked = downloadEnabled
        }
        if (::downloadConcurrencyRow.isInitialized) {
            downloadConcurrencyRow.isEnabled = downloadEnabled
            downloadConcurrencyRow.alpha = if (downloadEnabled) 1f else 0.45f
        }
        if (::downloadConcurrencySummary.isInitialized) {
            downloadConcurrencySummary.text =
                context.getString(
                    R.string.custom_download_concurrency_summary,
                    ModuleSettings.getCustomDownloadConcurrency(prefs),
                )
        }
        if (::bottomBarSwitch.isInitialized) {
            bottomBarSwitch.isChecked = bottomBarEnabled
        }
        if (::customThemeSwitch.isInitialized) {
            customThemeSwitch.isChecked = ModuleSettings.isCustomThemeEnabled(prefs)
        }
        if (::customSkinSwitch.isInitialized) {
            customSkinSwitch.isChecked = ModuleSettings.isCustomSkinEnabled(prefs)
        }
        if (::customThemeMonetSwitch.isInitialized) {
            customThemeMonetSwitch.isChecked = ModuleSettings.isCustomThemeMonetEnabled(prefs)
            customThemeMonetSwitch.isEnabled = ModuleSettings.isCustomThemeEnabled(prefs)
        }
        val monet = ModuleSettings.isCustomThemeMonetEnabled(prefs)
        if (::customThemeColorRow.isInitialized) {
            val enabled = ModuleSettings.isCustomThemeEnabled(prefs) && !monet
            customThemeColorRow.isEnabled = enabled
            customThemeColorRow.alpha = if (enabled) 1f else 0.45f
        }
        if (::customThemeColorSummary.isInitialized) {
            customThemeColorSummary.text = if (monet) {
                context.getString(R.string.custom_theme_color_follow_system)
            } else {
                context.getString(
                    R.string.custom_theme_color_summary,
                    "#%06X".format(ModuleSettings.getCustomThemeColor(prefs) and 0xFFFFFF),
                )
            }
        }
        if (::customThemeColorSwatch.isInitialized) {
            customThemeColorSwatch.setBackgroundColor(ModuleSettings.getEffectiveCustomThemeColor(context, prefs))
        }
        if (::customSkinConfigRow.isInitialized) {
            val enabled = ModuleSettings.isCustomSkinEnabled(prefs)
            customSkinConfigRow.isEnabled = enabled
            customSkinConfigRow.alpha = if (enabled) 1f else 0.45f
        }
        if (::customSkinConfigSummary.isInitialized) {
            val config = ModuleSettings.getCustomSkinJson(prefs)
            customSkinConfigSummary.text = if (config.isBlank()) {
                context.getString(R.string.custom_skin_config_empty)
            } else {
                runCatching {
                    val root = JSONObject(config)
                    val skin = root.optJSONObject("user_equip") ?: root
                    context.getString(
                        R.string.custom_skin_config_loaded,
                        skin.optString("name", skin.optLong("id").toString()),
                    )
                }.getOrElse { context.getString(R.string.custom_skin_config_loaded, "JSON") }
            }
        }
        val customFontScaleEnabled = ModuleSettings.isCustomFontScaleEnabled(prefs)
        if (::customFontScaleSwitch.isInitialized) {
            customFontScaleSwitch.isChecked = customFontScaleEnabled
        }
        if (::customFontScaleSeekBar.isInitialized) {
            val percent = ModuleSettings.getCustomFontScalePercent(prefs)
            customFontScaleSeekBar.progress = ((percent - ModuleSettings.MIN_CUSTOM_FONT_SCALE_PERCENT) / 5).coerceAtLeast(0)
            customFontScaleSummary.text = customFontScaleSummary(percent)
        }
        if (::customFontScaleRow.isInitialized) {
            customFontScaleRow.isEnabled = customFontScaleEnabled
            customFontScaleRow.alpha = if (customFontScaleEnabled) 1f else 0.45f
            if (::customFontScaleSeekBar.isInitialized) {
                customFontScaleSeekBar.isEnabled = customFontScaleEnabled
            }
        }
        if (::customCdnWifiSummary.isInitialized) {
            val hosts = ModuleSettings.getCdnPriorityList(prefs, ModuleSettings.KEY_CDN_WIFI_PRIORITY)
            val isEnabled = prefs.getBoolean(ModuleSettings.KEY_CDN_WIFI_ENABLED, false)
            customCdnWifiSummary.text = if (!isEnabled || hosts.isEmpty()) {
                context.getString(R.string.cdn_wifi_priority_summary_empty)
            } else {
                val names = hosts.map { h -> ModuleSettings.cdnEndpoints.firstOrNull { it.host == h }?.name?.substringBefore("（") ?: h }
                context.getString(R.string.cdn_wifi_priority_summary_set, names.joinToString(" → "))
            }
        }
        if (::customCdnCellularSummary.isInitialized) {
            val hosts = ModuleSettings.getCdnPriorityList(prefs, ModuleSettings.KEY_CDN_CELLULAR_PRIORITY)
            val isEnabled = prefs.getBoolean(ModuleSettings.KEY_CDN_CELLULAR_ENABLED, false)
            customCdnCellularSummary.text = if (!isEnabled || hosts.isEmpty()) {
                context.getString(R.string.cdn_cellular_priority_summary_empty)
            } else {
                val names = hosts.map { h -> ModuleSettings.cdnEndpoints.firstOrNull { it.host == h }?.name?.substringBefore("（") ?: h }
                context.getString(R.string.cdn_cellular_priority_summary_set, names.joinToString(" → "))
            }
        }
        if (::halfScreenQualitySummary.isInitialized) {
            val qn = ModuleSettings.getHalfScreenQuality(prefs)
            val label = ModuleSettings.halfScreenQualityOptions.firstOrNull { it.qn == qn }?.label ?: qn.toString()
            halfScreenQualitySummary.text = context.getString(R.string.half_screen_quality_summary, label)
        }
        if (::fullScreenQualitySummary.isInitialized) {
            val qn = ModuleSettings.getFullScreenQuality(prefs)
            val label = ModuleSettings.fullScreenQualityOptions.firstOrNull { it.qn == qn }?.label ?: qn.toString()
            fullScreenQualitySummary.text = context.getString(R.string.full_screen_quality_summary, label)
        }
        bottomBarItemCheckBoxes.forEach { (id, checkBox) ->
            checkBox.isEnabled = bottomBarEnabled
            val item = bottomBarItems().firstOrNull { it.id == id }
            val allKnown = ModuleSettings.getKnownBottomBarItems(prefs).mapNotNull(::parseBottomBarItem)
            val isHidden = if (item != null) {
                isBottomBarItemHidden(item, hiddenBottomBarItems, allKnown)
            } else {
                id in hiddenBottomBarItems
            }
            checkBox.isChecked = !isHidden
        }
        if (::homeRecommendItemSwitch.isInitialized) {
            homeRecommendItemSwitch.isChecked = homeRecommendFilterEnabled
        }
        if (::homeRecommendTitleKeywordSummaryView.isInitialized) {
            homeRecommendTitleKeywordSummaryView.text = homeRecommendTitleKeywordSummary()
        }
        if (::homeRecommendTitleKeywordRow.isInitialized) {
            homeRecommendTitleKeywordRow.isEnabled = homeRecommendFilterEnabled
            homeRecommendTitleKeywordRow.alpha = if (homeRecommendFilterEnabled) 1f else 0.45f
        }
        val seaUnlockOn = prefs.getBoolean(ModuleSettings.KEY_SEA_BANGUMI_UNLOCK_ENABLED, false)
        seaServerSummaries.forEach { (region, view) ->
            view.text = ModuleSettings.normalizeResolverBaseUrl(prefs.getString(region.prefKey, null))
                ?: context.getString(R.string.sea_resolver_server_unset_summary)
        }
        seaServerRows.values.forEach { row ->
            row.isEnabled = seaUnlockOn
            row.alpha = if (seaUnlockOn) 1f else 0.45f
        }
        seaCdnSummaries.forEach { (region, view) ->
            view.text = ModuleSettings.getResolverCdnHost(prefs, region)
                ?: context.getString(R.string.sea_cdn_follow_summary)
        }
        seaCdnRows.values.forEach { row ->
            row.isEnabled = seaUnlockOn
            row.alpha = if (seaUnlockOn) 1f else 0.45f
        }
        if (::seaDefaultRegionSummary.isInitialized) {
            seaDefaultRegionSummary.text = ModuleSettings.getResolverDefaultRegion(prefs)?.let { seaRegionLabel(it) }
                ?: context.getString(R.string.sea_default_region_unset_summary)
        }
        if (::seaDefaultRegionRow.isInitialized) {
            seaDefaultRegionRow.isEnabled = seaUnlockOn
            seaDefaultRegionRow.alpha = if (seaUnlockOn) 1f else 0.45f
        }
        if (::seaResolverModeSummaryView.isInitialized) {
            seaResolverModeSummaryView.text =
                context.getString(R.string.sea_resolver_mode_summary, ModuleSettings.getSeaResolverMode(prefs))
        }
        if (::seaResolverModeRow.isInitialized) {
            seaResolverModeRow.isEnabled = seaUnlockOn
            seaResolverModeRow.alpha = if (seaUnlockOn) 1f else 0.45f
        }
        fun gate(switchView: Switch, enabled: Boolean) {
            switchView.isEnabled = enabled
            (switchView.parent as? View)?.alpha = if (enabled) 1f else 0.45f
        }
        if (::seaSearchSwitch.isInitialized) gate(seaSearchSwitch, seaUnlockOn)
        if (::seaPlaySwitch.isInitialized) gate(seaPlaySwitch, seaUnlockOn)
        if (::seaRelaxSwitch.isInitialized) gate(seaRelaxSwitch, seaUnlockOn)
        if (::seaCdnAutoSwitch.isInitialized) gate(seaCdnAutoSwitch, seaUnlockOn)
        if (::seaSubtitleSwitch.isInitialized) gate(seaSubtitleSwitch, seaUnlockOn)
        if (::seaDetailSwitch.isInitialized) gate(seaDetailSwitch, seaUnlockOn)
        if (::seaAccessKeySwitch.isInitialized) gate(seaAccessKeySwitch, seaUnlockOn)
        val commentKeywordFilterEnabled = ModuleSettings.isCommentKeywordFilterEnabled(prefs)
        if (::commentKeywordFilterSwitch.isInitialized) {
            commentKeywordFilterSwitch.isChecked = commentKeywordFilterEnabled
        }
        if (::commentKeywordSummary.isInitialized) {
            commentKeywordSummary.text = commentKeywordSummaryText()
        }
        if (::commentKeywordRow.isInitialized) {
            commentKeywordRow.isEnabled = commentKeywordFilterEnabled
            commentKeywordRow.alpha = if (commentKeywordFilterEnabled) 1f else 0.45f
        }
        val dynamicKeywordFilterEnabled = ModuleSettings.isDynamicKeywordFilterEnabled(prefs)
        if (::dynamicKeywordFilterSwitch.isInitialized) {
            dynamicKeywordFilterSwitch.isChecked = dynamicKeywordFilterEnabled
        }
        if (::dynamicKeywordSummaryView.isInitialized) {
            dynamicKeywordSummaryView.text = dynamicKeywordSummary()
        }
        if (::dynamicKeywordRow.isInitialized) {
            dynamicKeywordRow.isEnabled = dynamicKeywordFilterEnabled
            dynamicKeywordRow.alpha = if (dynamicKeywordFilterEnabled) 1f else 0.45f
        }
        val commentMinLevelEnabled = ModuleSettings.isCommentMinLevelEnabled(prefs)
        if (::commentMinLevelSwitch.isInitialized) {
            commentMinLevelSwitch.isChecked = commentMinLevelEnabled
        }
        if (::commentMinLevelSummary.isInitialized) {
            commentMinLevelSummary.text = commentMinLevelSummaryText()
        }
        if (::commentMinLevelRow.isInitialized) {
            commentMinLevelRow.isEnabled = commentMinLevelEnabled
            commentMinLevelRow.alpha = if (commentMinLevelEnabled) 1f else 0.45f
        }
        homeRecommendItemCheckBoxes.forEach { (key, checkBox) ->
            checkBox.isEnabled = homeRecommendFilterEnabled
            checkBox.isChecked = key in hiddenHomeRecommendItems
        }
        if (::homeRecommendTabSwitch.isInitialized) {
            homeRecommendTabSwitch.isChecked = homeRecommendTabFilterEnabled
        }
        homeRecommendTabCheckBoxes.forEach { (key, checkBox) ->
            checkBox.isEnabled = homeRecommendTabFilterEnabled
            checkBox.isChecked = key in hiddenHomeRecommendTabs
        }
        val videoDetailRelateFilterEnabled = ModuleSettings.isCustomVideoDetailRelateFilterEnabled(prefs)
        val hiddenVideoDetailRelateTypes = ModuleSettings.getHiddenVideoDetailRelateTypes(prefs)
        if (::videoDetailRelateFilterSwitch.isInitialized) {
            videoDetailRelateFilterSwitch.isChecked = videoDetailRelateFilterEnabled
        }
        if (::videoDetailRelateTitleKeywordSummaryView.isInitialized) {
            videoDetailRelateTitleKeywordSummaryView.text = videoDetailRelateTitleKeywordSummary()
        }
        if (::videoDetailRelateTitleKeywordRow.isInitialized) {
            videoDetailRelateTitleKeywordRow.isEnabled = videoDetailRelateFilterEnabled
            videoDetailRelateTitleKeywordRow.alpha = if (videoDetailRelateFilterEnabled) 1f else 0.45f
        }
        videoDetailRelateTypeCheckBoxes.forEach { (type, checkBox) ->
            checkBox.isEnabled = videoDetailRelateFilterEnabled
            checkBox.isChecked = type in hiddenVideoDetailRelateTypes
        }
        if (::hideAllHomeComponentsSwitch.isInitialized) {
            hideAllHomeComponentsSwitch.isChecked = hideAllHomeComponentsEnabled
        }
        if (::customHomeComponentHideSwitch.isInitialized) {
            customHomeComponentHideSwitch.isChecked = customHomeComponentHideEnabled
            customHomeComponentHideSwitch.isEnabled = !hideAllHomeComponentsEnabled
        }
        val homeComponentPickerEnabled = customHomeComponentHideEnabled && !hideAllHomeComponentsEnabled
        homeComponentCheckBoxes.forEach { (className, checkBox) ->
            checkBox.isEnabled = homeComponentPickerEnabled
            checkBox.isChecked = className !in hiddenHomeComponents
        }
        val blockAllComponentPools = ModuleSettings.isBlockAllComponentPoolsEnabled(prefs)
        val customComponentPoolBlock = ModuleSettings.isCustomComponentPoolBlockEnabled(prefs)
        val blockedComponentPools = prefs.getStringSet(ModuleSettings.KEY_BLOCKED_COMPONENT_POOLS, emptySet())
            ?.toSet() ?: emptySet()
        if (::blockAllComponentPoolsSwitch.isInitialized) {
            blockAllComponentPoolsSwitch.isChecked = blockAllComponentPools
        }
        if (::customComponentPoolBlockSwitch.isInitialized) {
            customComponentPoolBlockSwitch.isChecked = customComponentPoolBlock
            customComponentPoolBlockSwitch.isEnabled = !blockAllComponentPools
        }
        val componentPoolPickerEnabled = customComponentPoolBlock && !blockAllComponentPools
        componentPoolCheckBoxes.forEach { (name, checkBox) ->
            checkBox.isEnabled = componentPoolPickerEnabled
            checkBox.isChecked = blockAllComponentPools || name in blockedComponentPools
        }
        if (::customMineComponentHideSwitch.isInitialized) {
            customMineComponentHideSwitch.isChecked = customMineComponentHideEnabled
        }
        if (::mineComponentPickerRow.isInitialized) {
            mineComponentPickerRow.isEnabled = customMineComponentHideEnabled
            mineComponentPickerRow.alpha = if (customMineComponentHideEnabled) 1f else 0.45f
        }
        if (::mineComponentPickerSummary.isInitialized) {
            val components = mineComponentItems()
            val hiddenCount = components.count { it.name in hiddenMineComponents }
            mineComponentPickerSummary.text = context.getString(
                R.string.mine_component_picker_summary,
                hiddenCount,
                components.size,
            )
        }

        if (::storyVideoAdSwitch.isInitialized) {
            storyVideoAdSwitch.isChecked = storyEnabled
        }
        if (::storyVideoImmersiveFullscreenSwitch.isInitialized) {
            storyVideoImmersiveFullscreenSwitch.isChecked =
                ModuleSettings.isStoryVideoImmersiveFullscreenEnabled(prefs)
        }
        if (::storyVideoComponentAlphaSeekBar.isInitialized) {
            val alphaPercent = ModuleSettings.getStoryVideoComponentAlphaPercent(prefs)
            storyVideoComponentAlphaSeekBar.progress = alphaPercent
            storyVideoComponentAlphaSummary.text = storyVideoComponentAlphaSummary(alphaPercent)
        }
        val customLongPressSpeedEnabled = prefs.getBoolean(ModuleSettings.KEY_PLAYER_CUSTOM_LONG_PRESS_SPEED_ENABLED, false)
        if (::customLongPressSpeedSwitch.isInitialized) {
            customLongPressSpeedSwitch.isChecked = customLongPressSpeedEnabled
        }
        if (::customLongPressSpeedSeekBar.isInitialized) {
            val tenths = ModuleSettings.getPlayerCustomLongPressSpeedTenths(prefs)
            customLongPressSpeedSeekBar.progress = tenths - ModuleSettings.MIN_CUSTOM_LONG_PRESS_SPEED_TENTHS
            customLongPressSpeedSummary.text = customLongPressSpeedSummary(tenths)
        }
        if (::customLongPressSpeedRow.isInitialized) {
            val rowEnabled = customLongPressSpeedEnabled && ModuleSettings.isPlayerTripleSpeedEnabled(prefs)
            customLongPressSpeedRow.isEnabled = rowEnabled
            customLongPressSpeedRow.alpha = if (rowEnabled) 1f else 0.45f
            if (::customLongPressSpeedSeekBar.isInitialized) {
                customLongPressSpeedSeekBar.isEnabled = rowEnabled
            }
        }
        if (::skipVideoAdAutoLikeSwitch.isInitialized) {
            skipVideoAdAutoLikeSwitch.isChecked = skipVideoAdAutoLikeEnabled
        }
        if (::symbolScanStatusSummary.isInitialized) {
            symbolScanStatusSummary.text = symbolScanSummary()
        }
        tagCheckBoxes.forEach { (key, checkBox) ->
            checkBox.isEnabled = storyEnabled
            checkBox.isChecked = key in selectedTags
        }
        sponsorBlockCategoryButtons.forEach { (key, button) ->
            val category = ModuleSettings.skipVideoAdCategories.firstOrNull { it.key == key } ?: return@forEach
            val mode = ModuleSettings.getSkipVideoAdMode(prefs, key)
            button.isEnabled = sponsorBlockEnabled
            button.text = mode.label
            button.alpha = if (sponsorBlockEnabled) 1f else 0.45f
            button.contentDescription = "${category.label}：${mode.label}"
        }

        if (::blockedCountView.isInitialized) {
            blockedCountView.text =
                context.getString(
                    R.string.story_filter_blocked_count_summary,
                    prefs.getInt(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT, 0),
                )
        }
        refreshing = false
    }

    internal companion object {
        internal const val PROJECT_REPOSITORY_URL = "https://github.com/HSSkyBoy/BBZQ"
        internal const val TELEGRAM_CHANNEL_URL = "https://t.me/bbx_show"
        internal const val VERSION_TAP_WINDOW_MS = 1500L
        internal const val TITLE_KEYWORD_SUMMARY_MAX_ITEMS = 4
        internal const val UNKNOWN_RUNTIME_VALUE = "unknown"
        internal val hiddenSearchPages = setOf(
            SettingsActivity.PAGE_HIDDEN_FEATURES,
            SettingsActivity.PAGE_SKIP_VIDEO_AD_SWITCH,
            SettingsActivity.PAGE_SKIP_VIDEO_AD_CATEGORY,
        )
        internal val SUPPORTED_HOST_PACKAGES = listOf(
            "tv.danmaku.bili",
            "com.bilibili.app.blue",
            "top.nkbe.npatch",
        )
        /** 检查更新弹窗「前往下载」的兜底地址，当 Release 未给出链接时使用。 */
        internal const val RELEASE_PAGE_URL =
            "https://github.com/HSSkyBoy/BBZQ/releases/latest"
    }
}
