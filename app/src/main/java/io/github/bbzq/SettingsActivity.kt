package io.github.bbzq

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.FrameLayout
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
    private var contentFactory: SettingsContentFactory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        RuntimeEnvironmentInfo.applyRuntimeSnapshotFromIntent(intent, prefs)
        LinkerGuard.triggerConflict(this)

        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_ROOT
        val toolbar = createToolbar(page)
        val factory = SettingsContentFactory(
            context = this,
            prefs = prefs,
            page = page,
            openPage = { targetPage ->
                ModuleSettingsNavigator.open(
                    context = this,
                    runtimeValues = intent.getBundleExtra(RuntimeEnvironmentInfo.EXTRA_RUNTIME_VALUES),
                    page = targetPage,
                )
            },
            onExportClick = { launchExportConfig() },
            onImportClick = { launchImportConfig() },
            onCustomSkinImportClick = { launchCustomSkinImport() },
        )
        contentFactory = factory
        val content = factory.createScrollView()
        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F7F8"))
            addView(toolbar)
            addView(
                content,
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
        applyWindowInsets(contentRoot, toolbar, content)
    }

    override fun onDestroy() {
        contentFactory?.destroy()
        contentFactory = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RuntimeEnvironmentInfo.applyRuntimeSnapshotFromIntent(intent, prefs)
        LinkerGuard.triggerConflict(this)
        recreate()
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
        finish()
    }

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

    private fun createToolbar(page: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(2).toFloat()

            addView(TextView(this@SettingsActivity).apply {
                text = toolbarTitle(page)
                textSize = 20f
                setTextColor(Color.parseColor("#18191C"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(TextView(this@SettingsActivity).apply {
                text = getString(R.string.settings_done)
                textSize = 15f
                setTextColor(Color.parseColor("#FB7299"))
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

    private fun applyWindowInsets(
        root: LinearLayout,
        toolbar: LinearLayout,
        content: ScrollView,
    ) {
        val toolbarLeft = toolbar.paddingLeft
        val toolbarTop = toolbar.paddingTop
        val toolbarRight = toolbar.paddingRight
        val toolbarBottom = toolbar.paddingBottom
        val contentLeft = content.paddingLeft
        val contentTop = content.paddingTop
        val contentRight = content.paddingRight
        val contentBottom = content.paddingBottom

        root.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets =
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            toolbar.setPadding(
                toolbarLeft,
                toolbarTop + safeInsets.top,
                toolbarRight,
                toolbarBottom,
            )
            content.setPadding(
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
