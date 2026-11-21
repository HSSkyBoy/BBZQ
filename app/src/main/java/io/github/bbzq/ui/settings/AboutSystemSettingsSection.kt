package io.github.bbzq.ui.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.BuildConfig
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.RootUtils
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.SettingsActivity
import io.github.bbzq.UpdateChecker
import io.github.bbzq.prefs.AccessKeyRepository
import io.github.bbzq.ui.MarkdownFormatter
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.PROJECT_REPOSITORY_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.RELEASE_PAGE_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.SUPPORTED_HOST_PACKAGES
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.TELEGRAM_CHANNEL_URL
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.UNKNOWN_RUNTIME_VALUE
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.VERSION_TAP_WINDOW_MS
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun SettingsContentFactory.aboutRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.about_hide_desktop_icon_title),
        context.getString(R.string.about_hide_desktop_icon_summary),
        ModuleSettings.KEY_HIDE_DESKTOP_ICON,
        false,
    )
    rows += createClickableInfoRow(
        context.getString(R.string.about_version_title),
        RuntimeEnvironmentInfo.versionSummary(context, prefs),
    ) {
        handleVersionRowClick()
    }
    rows += createClickableInfoRow(
        context.getString(R.string.about_project_repository_title),
        "HSSkyBoy/BBZQ",
    ) {
        openUrl(PROJECT_REPOSITORY_URL)
    }
    rows += createClickableInfoRow(
        context.getString(R.string.about_telegram_channel_title),
        "t.me/bbx_show",
    ) {
        openUrl(TELEGRAM_CHANNEL_URL)
    }
    return rows
}

internal fun SettingsContentFactory.updateRows(): List<View> = listOf(
    createUpdateCheckRow(),
    createSwitchRow(
        context.getString(R.string.about_accept_prerelease_title),
        context.getString(R.string.about_accept_prerelease_summary),
        ModuleSettings.KEY_ACCEPT_PRERELEASE_UPDATE,
        false,
    ),
)

internal fun SettingsContentFactory.configBackupRows(): List<View> = listOf(
    createClickableInfoRow(
        context.getString(R.string.action_restart_bilibili_title),
        context.getString(R.string.action_restart_bilibili_summary),
    ) {
        (context as? Activity)?.let { activity ->
            RootUtils.showRestartBilibiliDialog(activity, prefs)
        }
    },
    createClickableInfoRow(
        context.getString(R.string.about_export_config_title),
        context.getString(R.string.about_export_config_summary),
    ) { onExportClick() },
    createClickableInfoRow(
        context.getString(R.string.about_import_config_title),
        context.getString(R.string.about_import_config_summary),
    ) { onImportClick() },
    createSymbolCacheRefreshRow(),
)

private fun SettingsContentFactory.hiddenFeaturesEntryRows(): List<View> {
    return listOf(
        createClickableInfoRow(
            context.getString(R.string.hidden_features_entry_title),
            "",
        ) {
            openPage(SettingsActivity.PAGE_HIDDEN_FEATURES)
        },
    )
}

internal fun SettingsContentFactory.hiddenFeaturesRows(): List<View> {
    val rows = mutableListOf<View>()
    val skipVisible = ModuleSettings.isSkipVideoAdSettingsVisible(prefs)
    val accessKeyVisible = ModuleSettings.isAccessKeySettingsVisible(prefs)
    val tryFreeQualityVisible = ModuleSettings.isTryFreeQualitySettingsVisible(prefs)
    if (skipVisible) {
        rows += createClickableInfoRow(
            context.getString(R.string.about_skip_video_ad_switch_title),
            context.getString(R.string.about_skip_video_ad_switch_summary),
        ) {
            openPage(SettingsActivity.PAGE_SKIP_VIDEO_AD_SWITCH)
        }
        rows += createClickableInfoRow(
            context.getString(R.string.about_skip_video_ad_category_title),
            context.getString(R.string.about_skip_video_ad_category_summary),
        ) {
            openPage(SettingsActivity.PAGE_SKIP_VIDEO_AD_CATEGORY)
        }
    }
    if (accessKeyVisible) {
        rows += createClickableInfoRow(
            context.getString(R.string.about_access_key_title),
            context.getString(R.string.about_access_key_summary),
        ) {
            handleAccessKeyClick()
        }
    }
    rows += createSwitchRow(
        context.getString(R.string.auto_claim_vip_exp_title),
        context.getString(R.string.auto_claim_vip_exp_summary),
        ModuleSettings.KEY_AUTO_CLAIM_VIP_EXP_ENABLED,
        false,
    )
    if (tryFreeQualityVisible) {
        rows += createSwitchRow(
            context.getString(R.string.unlock_video_features_title),
            context.getString(R.string.unlock_video_features_summary),
            ModuleSettings.KEY_UNLOCK_VIDEO_FEATURES_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.unlock_video_features_ui_title),
            context.getString(R.string.unlock_video_features_ui_summary),
            ModuleSettings.KEY_UNLOCK_VIDEO_FEATURES_UI_ENABLED,
            true,
        )
        rows += createSwitchRow(
            context.getString(R.string.unlock_highest_bitrate_title),
            context.getString(R.string.unlock_highest_bitrate_summary),
            ModuleSettings.KEY_UNLOCK_HIGHEST_BITRATE_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.avoid_hdr_dolby_title),
            context.getString(R.string.avoid_hdr_dolby_summary),
            ModuleSettings.KEY_AVOID_HDR_DOLBY_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.fake_wifi_title),
            context.getString(R.string.fake_wifi_summary),
            ModuleSettings.KEY_FAKE_WIFI_ENABLED,
            true,
        )
        rows += createHalfScreenQualityRow()
        rows += createFullScreenQualityRow()
        rows += createSwitchRow(
            context.getString(R.string.video_download_title),
            context.getString(R.string.video_download_summary),
            ModuleSettings.KEY_VIDEO_DOWNLOAD_ENABLED,
            false,
        )
    }
    return rows
}

internal fun SettingsContentFactory.skipVideoAdCreditRows(): List<View> {
    return listOf(
        createClickableInfoRow(
            context.getString(R.string.credits_title),
            context.getString(R.string.credits_summary),
        ) {
            openUrl("https://github.com/hanydd/BilibiliSponsorBlock")
        },
        createClickableInfoRow(
            context.getString(R.string.api_docs_title),
            context.getString(R.string.api_docs_summary),
        ) {
            openUrl("https://github.com/hanydd/BilibiliSponsorBlock/wiki/API")
        },
    )
}

private fun SettingsContentFactory.handleVersionRowClick() {
    val now = SystemClock.elapsedRealtime()
    val skipVisible = ModuleSettings.isSkipVideoAdSettingsVisible(prefs)
    val accessKeyVisible = ModuleSettings.isAccessKeySettingsVisible(prefs)
    val tryFreeQualityVisible = ModuleSettings.isTryFreeQualitySettingsVisible(prefs)

    if (now - firstVersionTapAt > VERSION_TAP_WINDOW_MS) {
        versionTapCount = 0
        firstVersionTapAt = now
    }

    versionTapCount += 1

    if (!(skipVisible && accessKeyVisible && tryFreeQualityVisible) && versionTapCount >= 4) {
        prefs.edit().apply {
            if (!skipVisible) putBoolean(ModuleSettings.KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE, true)
            if (!accessKeyVisible) putBoolean(ModuleSettings.KEY_ACCESS_KEY_SETTINGS_VISIBLE, true)
            if (!tryFreeQualityVisible) putBoolean(ModuleSettings.KEY_TRY_FREE_QUALITY_SETTINGS_VISIBLE, true)
        }.apply()
        Toast.makeText(context, context.getString(R.string.version_hidden_entry_toast), Toast.LENGTH_SHORT).show()
        versionTapCount = 0
        firstVersionTapAt = 0L
        openPage(SettingsActivity.PAGE_ROOT)
        return
    }

    if (skipVisible || accessKeyVisible || tryFreeQualityVisible) {
        showRuntimeEnvironmentDialog()
    }
}

internal fun SettingsContentFactory.hasHiddenFeatures(): Boolean =
    ModuleSettings.isSkipVideoAdSettingsVisible(prefs) ||
        ModuleSettings.isAccessKeySettingsVisible(prefs) ||
        ModuleSettings.isTryFreeQualitySettingsVisible(prefs)

private fun SettingsContentFactory.createUpdateCheckRow(): View {
    val summaryView = TextView(context).apply {
        text = context.getString(R.string.about_check_update_summary)
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    updateCheckSummaryView = summaryView
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { handleUpdateCheckClick() }
        addView(TextView(context).apply {
            text = context.getString(R.string.about_check_update_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(summaryView)
        registerSearchTarget(
            this,
            context.getString(R.string.about_check_update_title),
            context.getString(R.string.about_check_update_summary),
            "action:${context.getString(R.string.about_check_update_title)}",
        )
    }
}

private fun SettingsContentFactory.handleUpdateCheckClick() {
    if (updateChecking) return
    updateChecking = true
    updateCheckSummaryView?.text = context.getString(R.string.check_update_checking_summary)
    updateCheckCall = UpdateChecker.check(
        BuildConfig.RELEASE_NAME,
        BuildConfig.VERSION_CODE,
        ModuleSettings.isAcceptPrereleaseUpdateEnabled(prefs),
    ) { result ->
        updateCheckCall = null
        updateChecking = false
        if (isActivityFinishing()) return@check
        onUpdateCheckResult(result)
    }
}

private fun SettingsContentFactory.isActivityFinishing(): Boolean {
    val activity = context as? Activity ?: return false
    return activity.isFinishing || activity.isDestroyed
}

private fun SettingsContentFactory.onUpdateCheckResult(result: UpdateChecker.Result) {
    when (result.status) {
        UpdateChecker.Status.UP_TO_DATE -> {
            updateCheckSummaryView?.text = context.getString(
                R.string.check_update_up_to_date_summary,
                result.latestVersion ?: BuildConfig.RELEASE_NAME,
            )
        }

        UpdateChecker.Status.UPDATE_AVAILABLE -> {
            val latest = result.latestVersion.orEmpty()
            updateCheckSummaryView?.text = context.getString(
                R.string.check_update_available_summary,
                latest,
            )
            showUpdateAvailableDialog(result)
        }

        UpdateChecker.Status.FAILED -> {
            updateCheckSummaryView?.text = context.getString(R.string.check_update_failed_summary)
        }
    }
}

private fun SettingsContentFactory.showUpdateAvailableDialog(result: UpdateChecker.Result) {
    val latestVersionText = formatVersionWithCode(result.latestVersion.orEmpty(), result.latestVersionCode)
    val currentVersionText = formatVersionWithCode(
        result.currentVersion ?: BuildConfig.RELEASE_NAME,
        BuildConfig.VERSION_CODE,
    )
    val header = buildString {
        append(
            context.getString(
                R.string.check_update_dialog_message,
                latestVersionText,
                currentVersionText,
            ),
        )
        if (result.apkSizeBytes > 0) {
            append('\n')
            append(context.getString(R.string.check_update_apk_size, formatSize(result.apkSizeBytes)))
        }
    }
    val notesRaw = result.releaseNotes?.takeIf { it.isNotBlank() }
    val notesSpanned = notesRaw?.let { MarkdownFormatter.toSpanned(it) }
        ?: SpannableString(context.getString(R.string.check_update_notes_empty))

    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), 0)
        addView(TextView(context).apply {
            text = header
            textSize = 14f
            setTextColor(titleTextColor)
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.check_update_notes_label)
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(12), 0, dp(4))
        })
        addView(TextView(context).apply {
            text = notesSpanned
            textSize = 13f
            setTextColor(titleTextColor)
            movementMethod = LinkMovementMethod.getInstance()
        })
    }
    val scroll = ScrollView(context).apply { addView(content) }

    AlertDialog.Builder(context)
        .setTitle(R.string.check_update_dialog_title)
        .setView(scroll)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.check_update_dialog_confirm) { _, _ ->
            openUrl(result.releaseUrl ?: RELEASE_PAGE_URL)
        }
        .show()
}

/** 拼接「版本号（versionCode）」，code 无效时只显示版本号。 */
private fun SettingsContentFactory.formatVersionWithCode(version: String, code: Int): String =
    if (code > 0) {
        context.getString(R.string.check_update_version_with_code, version, code)
    } else {
        version
    }

/** 将字节数格式化为可读大小（KB/MB）。 */
private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        String.format(Locale.getDefault(), "%.1f KB", kb)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
    }
}

private fun SettingsContentFactory.handleAccessKeyClick() {
    val key = AccessKeyRepository.read(prefs)
    if (key == null) {
        AlertDialog.Builder(context)
            .setTitle(R.string.access_key_error_title)
            .setMessage(R.string.access_key_error_message)
            .setPositiveButton(R.string.runtime_environment_ok, null)
            .setNeutralButton(R.string.access_key_manual_button) { _, _ ->
                showManualAccessKeyDialog(null)
            }
            .show()
        return
    }

    val content = TextView(context).apply {
        text = key
        textSize = 16f
        typeface = Typeface.MONOSPACE
        setTextColor(titleTextColor)
        setTextIsSelectable(true)
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(24), dp(18), dp(24))
    }

    AlertDialog.Builder(context)
        .setTitle(R.string.access_key_title)
        .setView(content)
        .setPositiveButton(R.string.access_key_copy_button) { _, _ ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AccessKey", key)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, context.getString(R.string.access_key_copied_toast), Toast.LENGTH_SHORT).show()
        }
        .setNeutralButton(R.string.access_key_manual_button) { _, _ ->
            showManualAccessKeyDialog(key)
        }
        .setNegativeButton(R.string.runtime_environment_ok, null)
        .show()
}

private fun SettingsContentFactory.showManualAccessKeyDialog(currentKey: String?) {
    val input = android.widget.EditText(context).apply {
        setText(currentKey.orEmpty())
        hint = context.getString(R.string.access_key_manual_hint)
        setSingleLine()
        setTextColor(titleTextColor)
        setHintTextColor(summaryTextColor)
    }
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(4))
        addView(input)
    }

    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.access_key_manual_title)
        .setView(container)
        .setPositiveButton(R.string.runtime_environment_ok) { _, _ ->
            val trimmed = input.text.toString().trim()
            if (trimmed.isEmpty()) {
                prefs.edit().remove(ModuleSettings.KEY_LAST_ACCESS_KEY).apply()
                Toast.makeText(context, context.getString(R.string.access_key_cleared_toast), Toast.LENGTH_SHORT).show()
            } else if (AccessKeyRepository.looksLikeAccessKey(trimmed)) {
                prefs.edit().putString(ModuleSettings.KEY_LAST_ACCESS_KEY, trimmed).apply()
                Toast.makeText(context, context.getString(R.string.access_key_saved_toast), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.access_key_invalid_toast), Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton(R.string.skip_video_ad_auto_like_disable_dialog_cancel, null)
    if (!currentKey.isNullOrBlank()) {
        builder.setNeutralButton(R.string.access_key_clear_button) { _, _ ->
            prefs.edit().remove(ModuleSettings.KEY_LAST_ACCESS_KEY).apply()
            Toast.makeText(context, context.getString(R.string.access_key_cleared_toast), Toast.LENGTH_SHORT).show()
        }
    }
    builder.show()
}

private fun SettingsContentFactory.createSymbolCacheRefreshRow(): View {
    symbolScanStatusSummary = TextView(context).apply {
        text = symbolScanSummary()
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { handleSymbolCacheRefreshClick() }
        addView(TextView(context).apply {
            text = context.getString(R.string.symbol_cache_refresh_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(symbolScanStatusSummary)
        registerSearchTarget(
            this,
            context.getString(R.string.symbol_cache_refresh_title),
            context.getString(R.string.symbol_cache_refresh_no_status),
            "action:${context.getString(R.string.symbol_cache_refresh_title)}",
        )
    }
}

internal fun SettingsContentFactory.handleSymbolCacheRefreshClick() {
    val report = symbolScanReport()
    AlertDialog.Builder(context)
        .setTitle(R.string.symbol_cache_refresh_dialog_title)
        .setMessage(context.getString(R.string.symbol_cache_refresh_dialog_message, report))
        .setNegativeButton(R.string.skip_mode_cancel, null)
        .setPositiveButton(R.string.symbol_cache_refresh_confirm) { _, _ ->
            startSymbolCacheRefresh()
        }
        .show()
}

internal fun SettingsContentFactory.launchHostAppForSymbolRefresh(appContext: Context) {
    val hostPackage = symbolRefreshHostPackages()
        .firstNotNullOfOrNull { packageName ->
            appContext.packageManager.getLaunchIntentForPackage(packageName)
        }
        ?: run {
            Toast.makeText(appContext, R.string.symbol_cache_refresh_launch_host_failed_toast, Toast.LENGTH_SHORT).show()
            return
        }
    hostPackage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        appContext.startActivity(hostPackage)
    }.onFailure {
        Toast.makeText(appContext, R.string.symbol_cache_refresh_launch_host_failed_toast, Toast.LENGTH_SHORT).show()
    }
}

private fun SettingsContentFactory.symbolRefreshHostPackages(): List<String> {
    val recorded = prefs.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, null)
        ?.takeIf { it.isNotBlank() && it != UNKNOWN_RUNTIME_VALUE }
    return buildList {
        if (recorded != null) add(recorded)
        SUPPORTED_HOST_PACKAGES.forEach { if (it !in this) add(it) }
    }
}

internal fun SettingsContentFactory.symbolScanSummary(): String {
    val summary = prefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY, null)
        ?: context.getString(R.string.symbol_cache_refresh_no_status)
    val updatedAt = prefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT, null)
        ?.toLongOrNull()
        ?.let(::formatTime)
    return if (updatedAt == null) {
        summary
    } else {
        context.getString(R.string.symbol_cache_refresh_summary_with_time, summary, updatedAt)
    }
}

private fun SettingsContentFactory.symbolScanReport(): String =
    prefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, null)
        ?: context.getString(R.string.symbol_cache_refresh_no_report)

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))

private fun SettingsContentFactory.showRuntimeEnvironmentDialog() {
    val content = TextView(context).apply {
        text = RuntimeEnvironmentInfo.runtimeEnvironmentJson(context, prefs)
        textSize = 12f
        typeface = Typeface.MONOSPACE
        setTextColor(titleTextColor)
        setTextIsSelectable(true)
        setPadding(dp(18), dp(14), dp(18), dp(14))
    }
    val scroll = ScrollView(context).apply {
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
    AlertDialog.Builder(context)
        .setTitle("runtimeEnvironment")
        .setView(scroll)
        .setPositiveButton(R.string.runtime_environment_ok, null)
        .show()
}
