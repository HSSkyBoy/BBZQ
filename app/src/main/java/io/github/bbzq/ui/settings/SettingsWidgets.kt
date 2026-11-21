package io.github.bbzq.ui.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.RootUtils
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.SettingsActivity
import io.github.bbzq.ui.DesktopIconHelper
import io.github.bbzq.ui.LocaleHelper
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.UNKNOWN_RUNTIME_VALUE

internal class SectionSpec(val label: String, val build: () -> List<View>)

internal fun SettingsContentFactory.registerSearchTarget(
    view: View?,
    title: String,
    detail: String,
    key: String,
    pageOverride: String = indexPage,
    sectionOverride: String = currentSearchSection,
) {
    if (!indexSearchTargets) return
    val trimmedTitle = title.trim()
    if (trimmedTitle.isEmpty()) return
    if (searchTargets.any { it.item.key == key && it.page == pageOverride }) return
    searchTargets += SettingsSearchTarget(
        item = SettingsSearchItem(
            key = key,
            title = trimmedTitle,
            detail = detail.trim(),
            section = sectionOverride,
        ),
        view = view,
        page = pageOverride,
    )
}

internal fun SettingsContentFactory.registerRemoteSearchTargets() {
    fun remote(pageId: String, section: String, title: String, detail: String, key: String) {
        if (page == pageId) return
        registerSearchTarget(
            view = null,
            title = title,
            detail = detail,
            key = key,
            pageOverride = pageId,
            sectionOverride = section,
        )
    }

    remote(
        SettingsActivity.PAGE_UPDATE,
        context.getString(R.string.about_update_title),
        context.getString(R.string.about_check_update_title),
        context.getString(R.string.about_check_update_summary),
        "action:${context.getString(R.string.about_check_update_title)}",
    )
    remote(
        SettingsActivity.PAGE_UPDATE,
        context.getString(R.string.about_update_title),
        context.getString(R.string.about_accept_prerelease_title),
        context.getString(R.string.about_accept_prerelease_summary),
        ModuleSettings.KEY_ACCEPT_PRERELEASE_UPDATE,
    )
    remote(
        SettingsActivity.PAGE_CONFIG_BACKUP,
        context.getString(R.string.about_config_backup_title),
        context.getString(R.string.about_export_config_title),
        context.getString(R.string.about_export_config_summary),
        "action:${context.getString(R.string.about_export_config_title)}",
    )
    remote(
        SettingsActivity.PAGE_CONFIG_BACKUP,
        context.getString(R.string.about_config_backup_title),
        context.getString(R.string.about_import_config_title),
        context.getString(R.string.about_import_config_summary),
        "action:${context.getString(R.string.about_import_config_title)}",
    )
    remote(
        SettingsActivity.PAGE_CONFIG_BACKUP,
        context.getString(R.string.about_config_backup_title),
        context.getString(R.string.symbol_cache_refresh_title),
        context.getString(R.string.symbol_cache_refresh_title),
        "action:${context.getString(R.string.symbol_cache_refresh_title)}",
    )
}

internal fun SettingsContentFactory.createSectionLabel(text: String): TextView {
    return TextView(context).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(sectionLabelColor)
        setPadding(dp(4), dp(14), dp(4), dp(8))
    }
}

internal fun SettingsContentFactory.createSectionCard(rows: List<View>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(cardBackgroundColor)
        }
        clipToOutline = true
        rows.forEachIndexed { index, row ->
            addView(row)
            if (index != rows.lastIndex) {
                addView(createDivider())
            }
        }
    }
}

private fun SettingsContentFactory.createDivider(): View {
    return View(context).apply {
        setBackgroundColor(dividerColor)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(1),
        ).apply {
            marginStart = dp(16)
        }
    }
}

internal fun SettingsContentFactory.createInfoRow(title: String, summary: String): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(titleTextColor)
        })
        if (summary.isNotBlank()) {
            addView(TextView(context).apply {
                text = summary
                textSize = 12f
                setTextColor(summaryTextColor)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }
}

internal fun SettingsContentFactory.createClickableInfoRow(title: String, summary: String, onClick: () -> Unit): View {
    return createInfoRow(title, summary).apply {
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        registerSearchTarget(this, title, summary, "action:$title")
    }
}

internal fun SettingsContentFactory.createSwitchRow(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean,
    badges: List<RowBadge> = emptyList(),
    onSwitchReady: ((Switch) -> Unit)? = null,
): View {
    val switchView = Switch(context).apply {
        isChecked = prefs.getBoolean(key, defaultValue)
        setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            if (!refreshing) {
                handleSwitchChanged(key, isChecked)
            }
        }
    }
    onSwitchReady?.invoke(switchView)

    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(createTextColumn(title, summary, badges), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(switchView)
        registerSearchTarget(this, title, summary, key)
    }
}

private fun SettingsContentFactory.handleSwitchChanged(key: String, isChecked: Boolean) {
    val editor = prefs.edit().putBoolean(key, isChecked)
    if (key == ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED && isChecked) {
        editor.putBoolean(ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, true)
    }
    if (key == ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED) {
        ModuleSettings.clearLegacyHomeRecommendFilterSwitches(editor)
    }
    editor.apply()

    if (isSkipVideoAdSwitchKey(key)) {
        ModuleSettings.refreshSkipVideoAdCache(prefs)
    }
    if (key == ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED && !isChecked) {
        showSkipVideoAdAutoLikeDisableDialog()
    }
    if (shouldRefreshAfterSwitchChanged(key)) {
        refresh()
    }
    if (key == ModuleSettings.KEY_HIDE_DESKTOP_ICON) {
        applyDesktopIconSetting(isChecked)
    }
}

private fun SettingsContentFactory.isSkipVideoAdSwitchKey(key: String): Boolean =
    key == ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED ||
        key == ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED

private fun SettingsContentFactory.shouldRefreshAfterSwitchChanged(key: String): Boolean =
    key == ModuleSettings.KEY_PLAYER_TRIPLE_SPEED_ENABLED ||
        key == ModuleSettings.KEY_PLAYER_CUSTOM_LONG_PRESS_SPEED_ENABLED ||
        key == ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED ||
        key == ModuleSettings.KEY_DISABLE_LONG_PRESS_COPY_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_BOTTOM_BAR_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_THEME_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_THEME_MONET ||
        key == ModuleSettings.KEY_CUSTOM_SKIN_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_FONT_SCALE_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED ||
        key == ModuleSettings.KEY_COMMENT_KEYWORD_FILTER_ENABLED ||
        key == ModuleSettings.KEY_COMMENT_MIN_LEVEL_ENABLED ||
        key == ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED ||
        key == ModuleSettings.KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED ||
        key == ModuleSettings.KEY_BLOCK_ALL_COMPONENT_POOLS_ENABLED ||
        key == ModuleSettings.KEY_CUSTOM_COMPONENT_POOL_BLOCK_ENABLED ||
        key == ModuleSettings.KEY_SEA_BANGUMI_UNLOCK_ENABLED ||
        key == ModuleSettings.KEY_SEA_RESOLVER_SEND_ACCESS_KEY

private fun SettingsContentFactory.applyDesktopIconSetting(isChecked: Boolean) {
    DesktopIconHelper.applySetting(context, isChecked)
    if (!isChecked) {
        Toast.makeText(context, "恢复图标需重启手机后生效", Toast.LENGTH_SHORT).show()
    }
}

internal fun SettingsContentFactory.createTextColumn(title: String, summary: String, badges: List<RowBadge> = emptyList()): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, dp(14), 0)
        addView(createTitleLine(title, badges))
        addView(TextView(context).apply {
            text = summary
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        })
    }
}

internal enum class RowBadge(
    val labelRes: Int,
    val textColorRes: Int,
    val backgroundColorRes: Int,
) {
    RESTART(R.string.badge_restart, R.color.badge_restart_text, R.color.badge_restart_background),
    UNFINISHED(R.string.badge_unfinished, R.color.badge_unfinished_text, R.color.badge_unfinished_background),
    EXPERIMENTAL(R.string.badge_experimental, R.color.badge_restart_text, R.color.badge_restart_background),
}

/** 去广告相关功能在未解锁隐藏功能前标记为“未完成”。 */
internal fun SettingsContentFactory.unfinishedBadges(): List<RowBadge> =
    if (hasHiddenFeatures()) emptyList() else listOf(RowBadge.UNFINISHED)

internal fun SettingsContentFactory.restartBadges(): List<RowBadge> = listOf(RowBadge.RESTART)

private fun SettingsContentFactory.createTitleLine(title: String, badges: List<RowBadge>): View {
    val titleView = TextView(context).apply {
        text = title
        textSize = 15f
        setTextColor(titleTextColor)
    }
    if (badges.isEmpty()) return titleView
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        badges.forEach { addView(createBadgeView(it)) }
    }
}

private fun SettingsContentFactory.createBadgeView(badge: RowBadge): View {
    return TextView(context).apply {
        text = context.getString(badge.labelRes)
        textSize = 10f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(context.getColor(badge.textColorRes))
        setPadding(dp(6), dp(1), dp(6), dp(1))
        background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(context.getColor(badge.backgroundColorRes))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(8) }
    }
}

internal enum class TileTone(val backgroundRes: Int, val iconColorRes: Int) {
    GENERAL(R.color.tile_general_background, R.color.tile_general_icon),
    PAGE(R.color.tile_page_background, R.color.tile_page_icon),
    ADVANCED(R.color.tile_advanced_background, R.color.tile_advanced_icon),
}

private fun SettingsContentFactory.createIconTile(iconRes: Int, tone: TileTone): View {
    return ImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(context.getColor(tone.iconColorRes))
        setPadding(dp(9), dp(9), dp(9), dp(9))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(context.getColor(tone.backgroundRes))
        }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(14) }
    }
}

private fun SettingsContentFactory.createNavigationRow(
    title: String,
    summary: String,
    badges: List<RowBadge> = emptyList(),
    iconRes: Int? = null,
    tone: TileTone = TileTone.PAGE,
    onClick: () -> Unit,
): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(16), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
        if (iconRes != null) addView(createIconTile(iconRes, tone))
        addView(createTextColumn(title, summary, badges), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(summaryTextColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        })
    }
}

internal fun SettingsContentFactory.categoryEntryRows(general: Boolean): List<View> =
    SettingsCategory.entries.filter { it.general == general }.map { category ->
        createNavigationRow(
            context.getString(category.titleRes),
            context.getString(category.summaryRes),
            categoryBadges(category),
            category.iconRes,
            if (general) TileTone.GENERAL else TileTone.PAGE,
        ) { openPage(category.page) }
    }

internal fun SettingsContentFactory.createStatusCard(): View {
    val hostVersion = prefs.getString(ModuleSettings.KEY_RUNTIME_HOST_VERSION_NAME, null)
        ?.takeIf { it.isNotBlank() && it != UNKNOWN_RUNTIME_VALUE }
    val uid = prefs.getString(ModuleSettings.KEY_HOST_ACCOUNT_UID, "").orEmpty()
    val userName = prefs.getString(ModuleSettings.KEY_HOST_ACCOUNT_NAME, "").orEmpty()
    val account = when {
        uid.isBlank() -> null
        userName.isBlank() -> context.getString(R.string.home_status_account, uid)
        else -> context.getString(R.string.home_status_account_named, userName, uid)
    }
    val connected = hostVersion != null
    val statusIcon = ImageView(context).apply {
        setImageResource(if (connected) R.drawable.ic_status_check else R.drawable.ic_status_alert)
        setColorFilter(context.getColor(if (connected) R.color.status_ok_icon else R.color.status_warn_icon))
        setPadding(dp(9), dp(9), dp(9), dp(9))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(if (connected) R.color.status_ok_background else R.color.status_warn_background))
        }
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) }
    }
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = if (connected) {
                context.getString(R.string.home_status_connected, hostVersion)
            } else {
                context.getString(R.string.home_status_disconnected)
            }
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(titleTextColor)
        })
        addView(TextView(context).apply {
            text = account ?: if (connected) "" else context.getString(R.string.home_status_disconnected_hint)
            textSize = 13f
            setTextColor(summaryTextColor)
            setPadding(0, dp(2), 0, 0)
            if (text.isNullOrBlank()) visibility = View.GONE
        })
        addView(TextView(context).apply {
            text = symbolScanSummary()
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(2), 0, 0)
        })
    }
    val primaryButton = createCardButton(
        context.getString(R.string.action_restart_bilibili_title),
        R.color.accent_primary,
        R.color.on_accent_primary,
    ) {
        (context as? Activity)?.let { activity ->
            RootUtils.showRestartBilibiliDialog(activity, prefs)
        }
    }
    val secondaryButton = createCardButton(
        context.getString(R.string.home_status_scan_report),
        R.color.secondary_button_background,
        R.color.title_text,
    ) { handleSymbolCacheRefreshClick() }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(cardBackgroundColor)
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusIcon)
            addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(primaryButton, LinearLayout.LayoutParams(0, dp(44), 1f))
                addView(
                    secondaryButton,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(10) },
                )
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(14) },
        )
    }
}

private fun SettingsContentFactory.createCardButton(label: String, backgroundRes: Int, textColorRes: Int, onClick: () -> Unit): View {
    return TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        setTextColor(context.getColor(textColorRes))
        setTypeface(typeface, Typeface.BOLD)
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(context.getColor(backgroundRes))
        }
        setOnClickListener { onClick() }
    }
}

// 入口上的“未完成”只在整页都属于去广告功能时才有意义，其余分类由具体设置项自己标注。
private fun SettingsContentFactory.categoryBadges(category: SettingsCategory): List<RowBadge> = when (category) {
    SettingsCategory.SEARCH,
    SettingsCategory.PLAYBACK,
    SettingsCategory.STORY,
    SettingsCategory.STARTUP,
    -> unfinishedBadges()
    else -> emptyList()
}

internal fun SettingsContentFactory.advancedEntryRows(): List<View> = listOf(
    createNavigationRow(
        context.getString(R.string.sea_bangumi_unlock_title),
        seaUnlockEntrySummary(),
        listOf(RowBadge.EXPERIMENTAL),
        R.drawable.ic_advanced_sea,
        TileTone.ADVANCED,
    ) { openPage(SettingsActivity.PAGE_SEA_BANGUMI_UNLOCK) },
    createNavigationRow(
        context.getString(R.string.about_hidden_features_title),
        context.getString(R.string.hidden_features_entry_summary),
        emptyList(),
        R.drawable.ic_advanced_hidden,
        TileTone.ADVANCED,
    ) { openPage(SettingsActivity.PAGE_HIDDEN_FEATURES) },
)

internal fun SettingsContentFactory.moduleEntryRows(): List<View> = listOf(
    createNavigationRow(
        context.getString(R.string.about_config_backup_title),
        context.getString(R.string.about_config_backup_summary),
        emptyList(),
        R.drawable.ic_more_backup,
        TileTone.GENERAL,
    ) { openPage(SettingsActivity.PAGE_CONFIG_BACKUP) },
    createNavigationRow(
        context.getString(R.string.settings_language_title),
        LocaleHelper.getLanguageDisplayName(context, ModuleSettings.getAppLanguage(prefs)),
        emptyList(),
        R.drawable.ic_more_language,
        TileTone.GENERAL,
    ) { showLanguageSelectDialog() },
    createNavigationRow(
        context.getString(R.string.about_update_title),
        context.getString(R.string.about_update_summary),
        emptyList(),
        R.drawable.ic_more_update,
        TileTone.GENERAL,
    ) { openPage(SettingsActivity.PAGE_UPDATE) },
    createNavigationRow(
        context.getString(R.string.about_page_title),
        RuntimeEnvironmentInfo.versionSummary(context, prefs),
        emptyList(),
        R.drawable.ic_more_about,
        TileTone.GENERAL,
    ) { openPage(SettingsActivity.PAGE_ABOUT) },
)

internal fun SettingsContentFactory.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, context.getString(R.string.open_url_failed_toast), Toast.LENGTH_SHORT).show()
        }
}

internal fun SettingsContentFactory.dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
