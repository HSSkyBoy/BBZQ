package io.github.bbzq.ui.settings

import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServers

internal fun SettingsContentFactory.seaSections(): List<SectionSpec> = listOf(
    SectionSpec(context.getString(R.string.sea_unlock_section_overview)) { seaBangumiUnlockRows() },
    SectionSpec(context.getString(R.string.sea_unlock_section_resolver)) { seaResolverRows() },
    SectionSpec(context.getString(R.string.sea_unlock_section_cdn)) { seaCdnSectionRows() },
    SectionSpec(context.getString(R.string.sea_unlock_section_scope)) { seaScopeRows() },
    SectionSpec(context.getString(R.string.sea_unlock_section_privacy)) { seaPrivacyRows() },
    SectionSpec(context.getString(R.string.sea_unlock_section_notes)) { seaNotesRows() },
)

internal fun SettingsContentFactory.seaUnlockEntrySummary(): String {
    val servers = ResolverServers.configured(prefs)
    return when {
        !prefs.getBoolean(ModuleSettings.KEY_SEA_BANGUMI_UNLOCK_ENABLED, false) ->
            context.getString(R.string.sea_unlock_entry_off_summary)
        servers.isEmpty() -> context.getString(R.string.sea_unlock_entry_no_server_summary)
        else -> context.getString(R.string.sea_unlock_entry_on_summary, servers.size)
    }
}

private fun SettingsContentFactory.seaBangumiUnlockRows(): List<View> = listOf(
    createInfoRow(
        context.getString(R.string.sea_unlock_overview_title),
        context.getString(R.string.sea_unlock_overview_summary),
    ),
    createSwitchRow(
        context.getString(R.string.sea_bangumi_unlock_title),
        context.getString(R.string.sea_bangumi_unlock_summary),
        ModuleSettings.KEY_SEA_BANGUMI_UNLOCK_ENABLED,
        false,
    ),
)

private fun SettingsContentFactory.seaResolverRows(): List<View> =
    ResolverRegion.values().map { createSeaResolverServerRow(it) } + listOf(
        createSeaDefaultRegionRow(),
        createClickableInfoRow(
            context.getString(R.string.sea_resolver_test_title),
            context.getString(R.string.sea_resolver_test_summary),
        ) { testSeaResolver() },
        createSeaResolverModeRow(),
    )

private fun SettingsContentFactory.seaScopeRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.sea_unlock_search_title),
        context.getString(R.string.sea_unlock_search_summary),
        ModuleSettings.KEY_SEA_UNLOCK_SEARCH_ENABLED,
        true,
    ) { seaSearchSwitch = it },
    createSwitchRow(
        context.getString(R.string.sea_unlock_play_title),
        context.getString(R.string.sea_unlock_play_summary),
        ModuleSettings.KEY_SEA_UNLOCK_PLAY_ENABLED,
        true,
    ) { seaPlaySwitch = it },
    createSwitchRow(
        context.getString(R.string.sea_unlock_detail_title),
        context.getString(R.string.sea_unlock_detail_summary),
        ModuleSettings.KEY_SEA_UNLOCK_DETAIL_ENABLED,
        true,
    ) { seaDetailSwitch = it },
    createSwitchRow(
        context.getString(R.string.sea_unlock_relax_title),
        context.getString(R.string.sea_unlock_relax_summary),
        ModuleSettings.KEY_SEA_UNLOCK_RELAX_PLAY_LIMITS,
        true,
    ) { seaRelaxSwitch = it },
    createSwitchRow(
        context.getString(R.string.sea_unlock_subtitle_title),
        context.getString(R.string.sea_unlock_subtitle_summary),
        ModuleSettings.KEY_SEA_UNLOCK_SUBTITLE_ENABLED,
        true,
    ) { seaSubtitleSwitch = it },
)

private fun SettingsContentFactory.seaPrivacyRows(): List<View> = listOf(
    createInfoRow(
        context.getString(R.string.sea_unlock_privacy_title),
        context.getString(R.string.sea_unlock_privacy_summary),
    ),
    createSwitchRow(
        context.getString(R.string.sea_resolver_send_access_key_title),
        context.getString(R.string.sea_resolver_send_access_key_summary),
        ModuleSettings.KEY_SEA_RESOLVER_SEND_ACCESS_KEY,
        false,
    ) { seaAccessKeySwitch = it },
)

private fun SettingsContentFactory.seaNotesRows(): List<View> = listOf(
    createInfoRow(
        context.getString(R.string.sea_unlock_notes_title),
        context.getString(R.string.sea_unlock_notes_summary),
    ),
)

private fun SettingsContentFactory.createSeaResolverModeRow(): View {
    seaResolverModeSummaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showSeaResolverModeDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.sea_resolver_mode_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(seaResolverModeSummaryView)
    }.also {
        seaResolverModeRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.sea_resolver_mode_title),
            context.getString(R.string.sea_resolver_mode_summary, ModuleSettings.getSeaResolverMode(prefs)),
            "action:${context.getString(R.string.sea_resolver_mode_title)}",
        )
    }
}

private fun SettingsContentFactory.showSeaResolverModeDialog() {
    val modes = ModuleSettings.SEA_RESOLVER_MODES
    val labels = modes.map { mode ->
        when (mode) {
            "TH" -> context.getString(R.string.sea_resolver_mode_th)
            "INTL" -> context.getString(R.string.sea_resolver_mode_intl)
            else -> context.getString(R.string.sea_resolver_mode_sea)
        }
    }.toTypedArray()
    val selected = modes.indexOf(ModuleSettings.getSeaResolverMode(prefs)).coerceAtLeast(0)
    AlertDialog.Builder(context)
        .setTitle(R.string.sea_resolver_mode_title)
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            dialog.dismiss()
            prefs.edit().putString(ModuleSettings.KEY_SEA_RESOLVER_MODE, modes[which]).apply()
            refresh()
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .show()
}

private fun SettingsContentFactory.seaCdnSectionRows(): List<View> =
    listOf(createInfoRow(
        context.getString(R.string.sea_cdn_info_title),
        context.getString(R.string.sea_cdn_info_summary),
    ), createSwitchRow(
        context.getString(R.string.sea_cdn_auto_title),
        context.getString(R.string.sea_cdn_auto_summary),
        ModuleSettings.KEY_RESOLVER_CDN_AUTO,
        false,
    ) { seaCdnAutoSwitch = it }) + ResolverRegion.values().map { createSeaCdnRow(it) }

private fun SettingsContentFactory.createSeaDefaultRegionRow(): View {
    val title = context.getString(R.string.sea_default_region_title)
    seaDefaultRegionSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showSeaDefaultRegionDialog() }
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(seaDefaultRegionSummary)
    }.also {
        seaDefaultRegionRow = it
        registerSearchTarget(it, title, context.getString(R.string.sea_default_region_unset_summary), "action:$title")
    }
}

private fun SettingsContentFactory.showSeaDefaultRegionDialog() {
    val regions = ResolverRegion.values()
    val labels = (listOf(context.getString(R.string.sea_default_region_unset_summary)) + regions.map { seaRegionLabel(it) })
        .toTypedArray()
    val current = ModuleSettings.getResolverDefaultRegion(prefs)
    val selected = if (current == null) 0 else regions.indexOf(current) + 1
    AlertDialog.Builder(context)
        .setTitle(R.string.sea_default_region_title)
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            dialog.dismiss()
            val value = if (which == 0) "" else regions[which - 1].name
            prefs.edit().putString(ModuleSettings.KEY_RESOLVER_DEFAULT_REGION, value).apply()
            refresh()
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .show()
}

private fun SettingsContentFactory.createSeaCdnRow(region: ResolverRegion): View {
    val title = context.getString(R.string.sea_cdn_title_format, seaRegionLabel(region))
    val summaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    seaCdnSummaries[region] = summaryView
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showSeaCdnDialog(region) }
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(summaryView)
    }.also {
        seaCdnRows[region] = it
        registerSearchTarget(it, title, context.getString(R.string.sea_cdn_follow_summary), "action:$title")
    }
}

private fun SettingsContentFactory.showSeaCdnDialog(region: ResolverRegion) {
    val input = EditText(context).apply {
        setText(ModuleSettings.getResolverCdnHost(prefs, region).orEmpty())
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setSingleLine(true)
        setHint(R.string.sea_cdn_hint)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), 0)
        addView(input)
    }
    AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.sea_cdn_title_format, seaRegionLabel(region)))
        .setView(content)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            val raw = input.text?.toString()?.trim().orEmpty()
            val normalized = ModuleSettings.normalizeCdnHost(raw)
            if (raw.isNotEmpty() && normalized == null) {
                Toast.makeText(context, R.string.sea_cdn_invalid_toast, Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            prefs.edit().putString(region.cdnKey, normalized.orEmpty()).apply()
            refresh()
        }
        .show()
}

internal fun SettingsContentFactory.seaRegionLabel(region: ResolverRegion): String = context.getString(
    when (region) {
        ResolverRegion.HK -> R.string.sea_region_hk
        ResolverRegion.TW -> R.string.sea_region_tw
        ResolverRegion.SEA -> R.string.sea_region_sea
        ResolverRegion.CN -> R.string.sea_region_cn
    },
)

private fun SettingsContentFactory.testSeaResolver() {
    val servers = ResolverServers.configured(prefs)
    if (servers.isEmpty()) {
        Toast.makeText(context, R.string.sea_resolver_server_empty_summary, Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(context, R.string.sea_resolver_test_running, Toast.LENGTH_SHORT).show()
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    Thread {
        val lines = servers.map { server ->
            val pinged = java.util.concurrent.FutureTask { io.github.bbzq.feats.SeaResolverClient(server.baseUrl) { _, _ -> }.ping() }
            Thread(pinged).start()
            server to pinged
        }.joinToString("\n") { (server, pinged) ->
            val elapsed = runCatching { pinged.get() }.getOrNull()
            val outcome = if (elapsed == null) {
                context.getString(R.string.sea_resolver_test_failed)
            } else {
                context.getString(R.string.sea_resolver_test_ok, elapsed)
            }
            "${seaRegionLabel(server.region)}：$outcome"
        }
        mainHandler.post {
            AlertDialog.Builder(context)
                .setTitle(R.string.sea_resolver_test_title)
                .setMessage(lines)
                .setPositiveButton(R.string.dialog_save, null)
                .show()
        }
    }.start()
}

private fun SettingsContentFactory.createSeaResolverServerRow(region: ResolverRegion): View {
    val title = context.getString(R.string.sea_resolver_server_title_format, seaRegionLabel(region))
    val summaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    seaServerSummaries[region] = summaryView
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showSeaResolverServerDialog(region) }
        addView(TextView(context).apply {
            text = title
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(summaryView)
    }.also {
        seaServerRows[region] = it
        registerSearchTarget(
            it,
            title,
            context.getString(R.string.sea_resolver_server_unset_summary),
            "action:$title",
        )
    }
}

private fun SettingsContentFactory.showSeaResolverServerDialog(region: ResolverRegion) {
    val input = EditText(context).apply {
        setText(ModuleSettings.normalizeResolverBaseUrl(prefs.getString(region.prefKey, null)).orEmpty())
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setSingleLine(true)
        setHint(R.string.sea_resolver_server_hint)
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(10), dp(20), 0)
        addView(input)
    }
    AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.sea_resolver_server_title_format, seaRegionLabel(region)))
        .setView(content)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            val raw = input.text?.toString()?.trim().orEmpty()
            val normalized = ModuleSettings.normalizeResolverBaseUrl(raw)
            if (raw.isNotEmpty() && normalized == null) {
                Toast.makeText(context, R.string.sea_resolver_server_invalid_toast, Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            prefs.edit().putString(region.prefKey, normalized.orEmpty()).apply()
            refresh()
        }
        .show()
}
