package io.github.bbzq

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {
    private val prefs by lazy {
        val base = getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE)
        ReadableModulePreferences(this, base)
    }

    private var pendingImportArchive: ByteArray? = null
    private var currentFactory: SettingsContentFactory? = null

    // In-activity page stack for zero-delay navigation
    private val pageStack = ArrayDeque<String>()
    private lateinit var toolbarTitleView: TextView
    private lateinit var contentContainer: FrameLayout

    // Track current bottom inset so new pages get it applied immediately
    private var currentBottomInset: Int = 0
    private var toolbarBaseTop: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        RuntimeEnvironmentInfo.applyRuntimeSnapshotFromIntent(intent, prefs)
        LinkerGuard.triggerConflict(this)

        val initialPage = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_ROOT
        pageStack.addLast(initialPage)

        val toolbar = createToolbar(initialPage)
        contentContainer = FrameLayout(this)

        val factory = buildFactory(initialPage)
        currentFactory = factory
        val scrollView = factory.createScrollView()
        contentContainer.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.page_background))
            addView(toolbar)
            addView(
                contentContainer,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        val root = FrameLayout(this).apply {
            addView(
                contentRoot,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            createAccountWatermark()?.let { watermark ->
                addView(
                    watermark,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        }

        setContentView(root)
        applyWindowInsets(contentRoot, toolbar, scrollView)
    }

    override fun onDestroy() {
        currentFactory = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RuntimeEnvironmentInfo.applyRuntimeSnapshotFromIntent(intent, prefs)
        LinkerGuard.triggerConflict(this)
        val targetPage = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_ROOT
        if (targetPage == PAGE_ROOT) {
            pageStack.clear()
            pageStack.addLast(PAGE_ROOT)
            switchContent(PAGE_ROOT)
        } else {
            navigateTo(targetPage)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_EXPORT_CONFIG -> data?.data?.let(::doExport)
            REQUEST_IMPORT_CONFIG -> data?.data?.let(::loadImportArchive)
            REQUEST_IMPORT_CUSTOM_SKIN -> data?.data?.let(::loadCustomSkinFile)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (pageStack.size > 1) {
            navigateBack()
        } else {
            finish()
        }
    }

    // ── In-activity Navigation ────────────────────────────────────────────────

    private fun navigateTo(page: String) {
        if (pageStack.lastOrNull() == page) return
        pageStack.addLast(page)
        switchContent(page)
    }

    private fun navigateBack() {
        if (pageStack.size <= 1) { finish(); return }
        pageStack.removeLast()
        switchContent(pageStack.last())
    }

    private fun switchContent(page: String) {
        currentFactory = null
        contentContainer.removeAllViews()
        toolbarTitleView.text = toolbarTitle(page)

        val factory = buildFactory(page)
        currentFactory = factory
        val scrollView = factory.createScrollView()
        // Apply tracked bottom inset to the new scroll view
        if (currentBottomInset > 0) {
            scrollView.setPadding(
                scrollView.paddingLeft,
                scrollView.paddingTop,
                scrollView.paddingRight,
                scrollView.paddingBottom + currentBottomInset,
            )
        }
        contentContainer.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun buildFactory(page: String): SettingsContentFactory =
        SettingsContentFactory(
            context = this,
            prefs = prefs,
            page = page,
            openPage = { targetPage -> navigateTo(targetPage) },
            onExportClick = { launchExportConfig() },
            onImportClick = { launchImportConfig() },
            onCustomSkinImportClick = { launchCustomSkinImport() },
        )

    // ── File Picking / Import / Export ────────────────────────────────────────

    private fun launchExportConfig() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, buildExportFileName())
        }
        runCatching {
            startActivityForResult(intent, REQUEST_EXPORT_CONFIG)
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.config_export_failed, throwable.message ?: "無法開啟檔案選擇器"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun launchImportConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream",
                ),
            )
        }
        runCatching {
            startActivityForResult(intent, REQUEST_IMPORT_CONFIG)
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.config_import_failed, throwable.message ?: "無法開啟檔案選擇器"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun launchCustomSkinImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "application/zip", "application/x-zip-compressed", "application/octet-stream"),
            )
        }
        startActivityForResult(intent, REQUEST_IMPORT_CUSTOM_SKIN)
    }

    private fun loadCustomSkinFile(uri: Uri) {
        val result = runCatching {
            contentResolver.openInputStream(uri)?.use { CustomSkinConfigPorter.read(it.readBytes()) }
                ?: CustomSkinConfigPorter.Result.Failure("无法读取文件")
        }.getOrElse { CustomSkinConfigPorter.Result.Failure(it.message ?: "无法读取文件") }
        when (result) {
            is CustomSkinConfigPorter.Result.Success -> {
                prefs.edit()
                    .putString(ModuleSettings.KEY_CUSTOM_SKIN_JSON, result.json)
                    .putBoolean(ModuleSettings.KEY_CUSTOM_SKIN_ENABLED, true)
                    .apply()
                Toast.makeText(this, R.string.custom_skin_config_imported, Toast.LENGTH_SHORT).show()
                recreate()
            }
            is CustomSkinConfigPorter.Result.Failure -> Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
        }
    }

    private fun doExport(uri: Uri) {
        val packageInfo = ConfigPorter.exportToZip(this, prefs)
        runCatching {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(packageInfo.bytes)
                output.flush()
            } ?: throw IOException("無法開啟匯出檔案")
        }.onSuccess {
            Toast.makeText(
                this,
                getString(
                    R.string.config_export_success,
                    packageInfo.switchCount,
                    packageInfo.manualCount,
                ),
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.config_export_failed, throwable.message ?: "未知錯誤"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun loadImportArchive(uri: Uri) {
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: throw IOException("無法讀取匯入檔案")
        }.onSuccess { bytes ->
            pendingImportArchive = bytes
            showImportConfirmDialog()
        }.onFailure { throwable ->
            Toast.makeText(
                this,
                getString(R.string.config_import_failed, throwable.message ?: "未知錯誤"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun showImportConfirmDialog() {
        val archive = pendingImportArchive ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.config_import_confirm_title)
            .setMessage(R.string.config_import_confirm_message)
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                pendingImportArchive = null
            }
            .setPositiveButton(R.string.skip_mode_confirm) { _, _ ->
                performImport(archive)
            }
            .setOnCancelListener {
                pendingImportArchive = null
            }
            .show()
    }

    private fun performImport(archive: ByteArray) {
        pendingImportArchive = null
        when (val result = ConfigPorter.importFromZip(archive, prefs)) {
            is ConfigPorter.ImportResult.Success -> {
                Toast.makeText(
                    this,
                    getString(
                        R.string.config_import_success,
                        result.switchCount,
                        result.manualCount,
                        result.skippedCount,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
                recreate()
            }

            is ConfigPorter.ImportResult.Failure -> {
                Toast.makeText(
                    this,
                    getString(R.string.config_import_failed, result.reason),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun buildExportFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "bbzq_config_$timestamp.zip"
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private fun createToolbar(page: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(getColor(R.color.toolbar_background))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(2).toFloat()

            addView(TextView(this@SettingsActivity).apply {
                text = toolbarTitle(page)
                textSize = 20f
                setTextColor(getColor(R.color.title_text))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                toolbarTitleView = this
            })

            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.toolbar_save_and_restart)
                textSize = 15f
                setTextColor(getColor(R.color.accent_pink))
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener {
                    RootUtils.showRestartBilibiliDialog(this@SettingsActivity, prefs) {
                        finish()
                    }
                }
            })

            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.settings_done)
                textSize = 15f
                setTextColor(getColor(R.color.accent_pink))
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { finish() }
            })
        }
    }

    private fun toolbarTitle(page: String): String = when (page) {
        PAGE_SKIP_VIDEO_AD_SWITCH -> getString(R.string.about_skip_video_ad_switch_title)
        PAGE_SKIP_VIDEO_AD_CATEGORY -> getString(R.string.about_skip_video_ad_category_title)
        PAGE_HIDDEN_FEATURES -> getString(R.string.about_hidden_features_title)
        PAGE_UPDATE -> getString(R.string.about_update_title)
        PAGE_CONFIG_BACKUP -> getString(R.string.about_config_backup_title)
        else -> getString(R.string.settings_title)
    }

    // ── Watermark ─────────────────────────────────────────────────────────────

    private fun createAccountWatermark(): AccountWatermarkView? {
        val uid = prefs.getString(ModuleSettings.KEY_HOST_ACCOUNT_UID, "").orEmpty()
        val userName = prefs.getString(ModuleSettings.KEY_HOST_ACCOUNT_NAME, "").orEmpty()
        val text = listOfNotNull(
            userName.takeIf { it.isNotBlank() },
            uid.takeIf { it.isNotBlank() }?.let { "UID $it" },
        ).joinToString(" · ")
        return text.takeIf { it.isNotBlank() }
            ?.let { AccountWatermarkView(this, it) }
            ?: runCatching {
                val snapshot = io.github.bbzq.feats.HostAccountResolver.resolve(this, classLoader)
                if (!snapshot.loggedIn) return@runCatching null
                val fallbackText = listOfNotNull(
                    snapshot.userName.takeIf { it.isNotBlank() },
                    snapshot.uid.takeIf { it.isNotBlank() }?.let { "UID $it" },
                ).joinToString(" · ")
                fallbackText.takeIf { it.isNotBlank() }?.let { AccountWatermarkView(this, it) }
            }.getOrNull()
    }

    // ── Window Insets ─────────────────────────────────────────────────────────

    private fun applyWindowInsets(
        root: LinearLayout,
        toolbar: LinearLayout,
        initialContent: ScrollView,
    ) {
        val toolbarLeft = toolbar.paddingLeft
        toolbarBaseTop = toolbar.paddingTop
        val toolbarRight = toolbar.paddingRight
        val toolbarBottom = toolbar.paddingBottom
        val contentLeft = initialContent.paddingLeft
        val contentTop = initialContent.paddingTop
        val contentRight = initialContent.paddingRight
        val contentBottom = initialContent.paddingBottom

        root.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets =
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            toolbar.setPadding(
                toolbarLeft,
                toolbarBaseTop + safeInsets.top,
                toolbarRight,
                toolbarBottom,
            )
            // Track bottom inset so future page switches apply it to new ScrollViews
            currentBottomInset = safeInsets.bottom
            // Apply to whichever ScrollView is currently shown
            val activeScroll = contentContainer.getChildAt(0) as? ScrollView
            activeScroll?.setPadding(
                contentLeft,
                contentTop,
                contentRight,
                contentBottom + safeInsets.bottom,
            )
            insets
        }
        root.requestApplyInsets()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PAGE = "settings_page"
        const val PAGE_ROOT = "root"
        const val PAGE_SKIP_VIDEO_AD_SWITCH = "skip_video_ad_switch"
        const val PAGE_SKIP_VIDEO_AD_CATEGORY = "skip_video_ad_category"
        const val PAGE_HIDDEN_FEATURES = "hidden_features"
        const val PAGE_UPDATE = "update"
        const val PAGE_CONFIG_BACKUP = "config_backup"
        private const val REQUEST_EXPORT_CONFIG = 0x5001
        private const val REQUEST_IMPORT_CONFIG = 0x5002
        private const val REQUEST_IMPORT_CUSTOM_SKIN = 0x5003
    }
}
