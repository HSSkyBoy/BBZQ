package io.github.bbzq.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import org.json.JSONObject

internal fun SettingsContentFactory.shareRows(): List<View> {
    return listOf(
        createSwitchRow(
            context.getString(R.string.share_purify_title),
            context.getString(R.string.share_purify_summary),
            ModuleSettings.KEY_PURIFY_SHARE_ENABLED,
            false,
        ),
        createSwitchRow(
            context.getString(R.string.share_mini_program_title),
            context.getString(R.string.share_mini_program_summary),
            ModuleSettings.KEY_MINI_PROGRAM_ENABLED,
            false,
        ),
    )
}

internal fun SettingsContentFactory.copyRows(): List<View> {
    return listOf(
        createSwitchRow(
            context.getString(R.string.copy_disable_title),
            context.getString(R.string.copy_disable_summary),
            ModuleSettings.KEY_DISABLE_LONG_PRESS_COPY_ENABLED,
            false,
        ) {
            disableLongPressCopySwitch = it
        },
        createSwitchRow(
            context.getString(R.string.copy_enhance_title),
            context.getString(R.string.copy_enhance_summary),
            ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED,
            false,
        ) {
            enhanceLongPressCopySwitch = it
        },
    )
}

internal fun SettingsContentFactory.startupRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.startup_skip_splash_title),
        context.getString(R.string.startup_skip_splash_summary),
        ModuleSettings.KEY_SKIP_SPLASH_AD_ENABLED,
        true,
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.startup_splash_auto_night_title),
        context.getString(R.string.startup_splash_auto_night_summary),
        ModuleSettings.KEY_SPLASH_AUTO_NIGHT_ENABLED,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.startup_block_teenagers_title),
        context.getString(R.string.startup_block_teenagers_summary),
        ModuleSettings.KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.block_update_title),
        context.getString(R.string.block_update_summary),
        ModuleSettings.KEY_BLOCK_UPDATE_ENABLED,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.risk_control_spoof_title),
        context.getString(R.string.risk_control_spoof_summary),
        ModuleSettings.KEY_RISK_CONTROL_SPOOF_ENABLED,
        false,
    )
    return rows
}

internal fun SettingsContentFactory.downloadRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
            context.getString(R.string.custom_download_thread_title),
            context.getString(R.string.custom_download_thread_summary),
            ModuleSettings.KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED,
            false,
        ) {
            downloadThreadSwitch = it
        }
    rows += createClickableInfoRow(
        context.getString(R.string.custom_download_concurrency_title),
        context.getString(
            R.string.custom_download_concurrency_summary,
            ModuleSettings.getCustomDownloadConcurrency(prefs),
        ),
    ) {
        showCustomDownloadConcurrencyDialog()
    }.also {
        downloadConcurrencyRow = it
        downloadConcurrencySummary = (it as ViewGroup).getChildAt(1) as TextView
    }
    return rows
}

internal fun SettingsContentFactory.appearanceRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.bottom_bar_title),
        context.getString(R.string.bottom_bar_summary),
        ModuleSettings.KEY_CUSTOM_BOTTOM_BAR_ENABLED,
        false,
    ) {
        bottomBarSwitch = it
    }
    val items = bottomBarItems()
    if (items.isEmpty()) {
        rows += createInfoRow(
            context.getString(R.string.bottom_bar_item_title),
            context.getString(R.string.bottom_bar_unavailable_summary),
        )
    } else {
        rows += createInfoRow(
            context.getString(R.string.bottom_bar_item_title),
            context.getString(R.string.bottom_bar_info_summary),
        )
        rows += createBottomBarItemGroup(items)
    }
    rows += createSwitchRow(
        context.getString(R.string.custom_theme_title),
        context.getString(R.string.custom_theme_summary),
        ModuleSettings.KEY_CUSTOM_THEME_ENABLED,
        false,
    ) {
        customThemeSwitch = it
    }
    if (ModuleSettings.isMonetSupported) {
        rows += createSwitchRow(
            context.getString(R.string.custom_theme_monet_title),
            context.getString(R.string.custom_theme_monet_summary),
            ModuleSettings.KEY_CUSTOM_THEME_MONET,
            false,
        ) {
            customThemeMonetSwitch = it
        }
    }
    rows += createCustomThemeColorRow()
    rows += createSwitchRow(
        context.getString(R.string.custom_skin_title),
        context.getString(R.string.custom_skin_summary),
        ModuleSettings.KEY_CUSTOM_SKIN_ENABLED,
        false,
    ) {
        customSkinSwitch = it
    }
    rows += createCustomSkinConfigRow()
    rows += createSwitchRow(
        context.getString(R.string.custom_font_scale_title),
        context.getString(R.string.custom_font_scale_summary),
        ModuleSettings.KEY_CUSTOM_FONT_SCALE_ENABLED,
        false,
        badges = restartBadges(),
    ) {
        customFontScaleSwitch = it
    }
    rows += createCustomFontScaleRow()
    rows += createSwitchRow(
        context.getString(R.string.dialog_blur_background_title),
        context.getString(R.string.dialog_blur_background_summary),
        ModuleSettings.KEY_DIALOG_BLUR_BACKGROUND_ENABLED,
        true,
    )
    rows += createSwitchRow(
        context.getString(R.string.home_top_bar_promotion_title),
        context.getString(R.string.home_top_bar_promotion_summary),
        ModuleSettings.KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED,
        false,
    )
    return rows
}

internal fun SettingsContentFactory.searchRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.home_search_default_word_title),
        context.getString(R.string.home_search_default_word_summary),
        ModuleSettings.KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.search_hot_clean_title),
        context.getString(R.string.search_hot_clean_summary),
        ModuleSettings.KEY_SEARCH_HOT_CLEAN_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.search_suggest_clean_title),
        context.getString(R.string.search_suggest_clean_summary),
        ModuleSettings.KEY_SEARCH_SUGGEST_CLEAN_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.search_result_ad_block_title),
        context.getString(R.string.search_result_ad_block_summary),
        ModuleSettings.KEY_SEARCH_RESULT_AD_BLOCK_ENABLED,
        false,
        badges = unfinishedBadges(),
    ),
)

private fun SettingsContentFactory.showCustomDownloadConcurrencyDialog() {
    val picker = NumberPicker(context).apply {
        minValue = 1
        maxValue = 12
        wrapSelectorWheel = false
        value = ModuleSettings.getCustomDownloadConcurrency(prefs)
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.custom_download_concurrency_dialog_title)
        .setView(picker)
        .setNegativeButton(R.string.skip_mode_cancel, null)
        .setPositiveButton(R.string.skip_mode_confirm) { _, _ ->
            prefs.edit()
                .putInt(ModuleSettings.KEY_CUSTOM_DOWNLOAD_CONCURRENCY, picker.value)
                .apply()
            refresh()
        }
        .show()
}

private fun SettingsContentFactory.createCustomThemeColorRow(): View {
    customThemeColorSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    customThemeColorSwatch = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginStart = dp(8)
        }
        background = GradientDrawable().apply { cornerRadius = dp(6).toFloat() }
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showCustomThemeColorDialog() }
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.custom_theme_color_title)
                    textSize = 15f
                    setTextColor(titleTextColor)
                })
                addView(customThemeColorSummary)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(customThemeColorSwatch)
    }.also {
        customThemeColorRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.custom_theme_color_title),
            context.getString(R.string.custom_theme_color_title),
            "action:${context.getString(R.string.custom_theme_color_title)}",
        )
    }
}

private fun SettingsContentFactory.showCustomThemeColorDialog() {
    val input = EditText(context).apply {
        setSingleLine(true)
        setSelectAllOnFocus(true)
        setText("%06X".format(ModuleSettings.getCustomThemeColor(prefs) and 0xFFFFFF))
        hint = "RRGGBB"
        inputType = InputType.TYPE_CLASS_TEXT
    }
    AlertDialog.Builder(context)
        .setTitle(R.string.custom_theme_color_dialog_title)
        .setMessage(R.string.custom_theme_color_dialog_message)
        .setView(input)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            val raw = input.text?.toString()?.trim()?.removePrefix("#").orEmpty()
            val color = raw.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }
                ?.let { Color.parseColor("#$it") }
            if (color == null) {
                Toast.makeText(context, R.string.custom_theme_color_invalid, Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            prefs.edit().putInt(ModuleSettings.KEY_CUSTOM_THEME_COLOR, color).apply()
            refresh()
        }
        .show()
}

private fun SettingsContentFactory.createCustomSkinConfigRow(): View {
    customSkinConfigSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showCustomSkinConfigDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.custom_skin_config_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(customSkinConfigSummary)
    }.also {
        customSkinConfigRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.custom_skin_config_title),
            context.getString(R.string.custom_skin_config_title),
            "action:${context.getString(R.string.custom_skin_config_title)}",
        )
    }
}

private fun SettingsContentFactory.createCustomFontScaleRow(): View {
    customFontScaleSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
    }
    customFontScaleSeekBar = SeekBar(context).apply {
        max = (ModuleSettings.MAX_CUSTOM_FONT_SCALE_PERCENT - ModuleSettings.MIN_CUSTOM_FONT_SCALE_PERCENT) / 5
        val currentPercent = ModuleSettings.getCustomFontScalePercent(prefs)
        progress = ((currentPercent - ModuleSettings.MIN_CUSTOM_FONT_SCALE_PERCENT) / 5).coerceAtLeast(0)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = ModuleSettings.MIN_CUSTOM_FONT_SCALE_PERCENT + progress * 5
                if (!refreshing) {
                    customFontScaleSummary.text = customFontScaleSummary(percent)
                }
                if (fromUser) {
                    prefs.edit()
                        .putInt(ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT, percent)
                        .apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(TextView(context).apply {
            text = context.getString(R.string.custom_font_scale_value_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(customFontScaleSummary.apply {
            setPadding(0, dp(4), 0, dp(8))
        })
        addView(customFontScaleSeekBar)
        registerSearchTarget(
            this,
            context.getString(R.string.custom_font_scale_value_title),
            context.getString(R.string.custom_font_scale_value_title),
            ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT,
        )
    }.also {
        customFontScaleRow = it
    }
}

internal fun SettingsContentFactory.customFontScaleSummary(percent: Int): String =
    context.getString(R.string.custom_font_scale_value_summary, percent)

private fun SettingsContentFactory.showCustomSkinConfigDialog() {
    val input = EditText(context).apply {
        minLines = 8
        maxLines = 16
        setText(ModuleSettings.getCustomSkinJson(prefs))
        gravity = Gravity.TOP
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }
    val wrapper = ScrollView(context).apply {
        setPadding(dp(20), 0, dp(20), 0)
        addView(input)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.custom_skin_config_title)
        .setMessage(R.string.custom_skin_config_message)
        .setView(wrapper)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setNeutralButton(R.string.custom_skin_config_import_file, null)
        .setPositiveButton(R.string.dialog_save, null)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            dialog.dismiss()
            onCustomSkinImportClick()
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = input.text?.toString()?.trim().orEmpty()
            val config = runCatching { JSONObject(text) }.getOrNull()
            val skin = config?.optJSONObject("user_equip") ?: config
            if (skin == null || skin.optString("package_url").isBlank()) {
                input.error = context.getString(R.string.custom_skin_config_invalid)
                return@setOnClickListener
            }
            prefs.edit()
                .putString(ModuleSettings.KEY_CUSTOM_SKIN_JSON, text)
                .putBoolean(ModuleSettings.KEY_CUSTOM_SKIN_ENABLED, true)
                .apply()
            refresh()
            dialog.dismiss()
        }
    }
    dialog.show()
}

internal fun SettingsContentFactory.showLanguageSelectDialog() {
    val currentLang = ModuleSettings.getAppLanguage(prefs)
    val languages = listOf(
        ModuleSettings.LANGUAGE_AUTO to context.getString(R.string.settings_language_auto),
        ModuleSettings.LANGUAGE_ZH_CN to context.getString(R.string.settings_language_zh_cn),
        ModuleSettings.LANGUAGE_ZH_TW to context.getString(R.string.settings_language_zh_tw),
        ModuleSettings.LANGUAGE_EN to context.getString(R.string.settings_language_en),
    )
    val currentIndex = languages.indexOfFirst { it.first == currentLang }.coerceAtLeast(0)
    val itemLabels = languages.map { it.second }.toTypedArray()

    AlertDialog.Builder(context)
        .setTitle(R.string.settings_language_title)
        .setSingleChoiceItems(itemLabels, currentIndex) { dialog, which ->
            val selectedLang = languages[which].first
            if (selectedLang != currentLang) {
                ModuleSettings.setAppLanguage(prefs, selectedLang)
                dialog.dismiss()
                (context as? Activity)?.recreate()
            } else {
                dialog.dismiss()
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
