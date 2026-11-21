package io.github.bbzq

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.ui.LocaleHelper
import io.github.bbzq.ui.MarkdownFormatter
import java.util.Locale
import okhttp3.Call

class IntroActivity : Activity() {
    private val prefs by lazy {
        getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE)
    }

    private var updateCheckStatusView: TextView? = null
    private var updateChecking = false
    private var updateCheckCall: Call? = null

    override fun attachBaseContext(newBase: Context) {
        val basePrefs = newBase.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = ModuleSettings.getAppLanguage(basePrefs)
        super.attachBaseContext(LocaleHelper.wrapContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.page_background))
            setPadding(dp(14), dp(18), dp(14), dp(28))
            addView(createHeroCard())
            addView(createGuideCard())
            addView(createSectionLabel(getString(R.string.intro_section_links)))
            addView(
                createSectionCard(
                    listOf(
                        createNavRow(
                            title = getString(R.string.about_update_title),
                            summary = getString(R.string.about_update_summary),
                            iconRes = R.drawable.ic_more_update,
                            tileBgRes = R.color.tile_general_background,
                            tileIconRes = R.color.tile_general_icon,
                        ) { openSettingsPage(SettingsActivity.PAGE_UPDATE) },
                        createNavRow(
                            title = getString(R.string.intro_open_settings),
                            summary = getString(R.string.intro_open_settings_summary),
                            iconRes = R.drawable.ic_more_about,
                            tileBgRes = R.color.tile_general_background,
                            tileIconRes = R.color.tile_general_icon,
                        ) { openSettingsPage(SettingsActivity.PAGE_ABOUT) },
                        createNavRow(
                            title = getString(R.string.about_project_repository_title),
                            summary = getString(R.string.intro_repo_summary),
                            iconRes = R.drawable.ic_category_share,
                            tileBgRes = R.color.tile_page_background,
                            tileIconRes = R.color.tile_page_icon,
                        ) { openUrl(UpdateChecker.PROJECT_REPOSITORY_URL) },
                        createNavRow(
                            title = getString(R.string.about_telegram_channel_title),
                            summary = "t.me/bbx_show",
                            iconRes = R.drawable.ic_category_comment,
                            tileBgRes = R.color.tile_general_background,
                            tileIconRes = R.color.tile_general_icon,
                        ) { openUrl(TELEGRAM_CHANNEL_URL) },
                    ),
                ),
            )
            addView(createSectionLabel(getString(R.string.intro_section_extras)))
            addView(
                createSectionCard(
                    listOf(
                        createNavRow(
                            title = getString(R.string.intro_womic_title),
                            summary = getString(R.string.intro_womic_summary),
                            iconRes = R.drawable.ic_category_live,
                            tileBgRes = R.color.tile_advanced_background,
                            tileIconRes = R.color.tile_advanced_icon,
                        ) { startActivity(Intent(this@IntroActivity, WoMicIntroActivity::class.java)) },
                        createNavRow(
                            title = getString(R.string.intro_readera_title),
                            summary = getString(R.string.intro_readera_summary),
                            iconRes = R.drawable.ic_category_dynamic,
                            tileBgRes = R.color.tile_advanced_background,
                            tileIconRes = R.color.tile_advanced_icon,
                        ) { startActivity(Intent(this@IntroActivity, ReadEraIntroActivity::class.java)) },
                    ),
                ),
            )
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.page_background))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(scrollView)
        applyWindowInsets(scrollView, content)
    }

    override fun onDestroy() {
        updateCheckCall?.cancel()
        updateCheckCall = null
        updateCheckStatusView = null
        super.onDestroy()
    }

    private fun createHeroCard(): View {
        val accentColor = getColor(R.color.accent_primary)
        val versionPill = TextView(this).apply {
            text = "v${BuildConfig.RELEASE_NAME} (${BuildConfig.VERSION_CODE})"
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accentColor)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(withAlpha(accentColor, 0x22))
            }
        }
        val statusLine = TextView(this).apply {
            textSize = 12f
            setTextColor(getColor(R.color.summary_text))
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        updateCheckStatusView = statusLine

        val openBiliButton = createPillButton(
            label = getString(R.string.intro_open_bilibili),
            bgColor = accentColor,
            fgColor = getColor(R.color.on_accent_primary),
        ) { launchBilibili() }

        val checkUpdateButton = createPillButton(
            label = getString(R.string.intro_check_update),
            bgColor = getColor(R.color.secondary_button_background),
            fgColor = getColor(R.color.title_text),
        ) { startUpdateCheck() }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(getColor(R.color.card_background))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }

            addView(LinearLayout(this@IntroActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    TextView(this@IntroActivity).apply {
                        text = getString(R.string.app_name)
                        textSize = 24f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(getColor(R.color.title_text))
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(versionPill)
            })
            addView(TextView(this@IntroActivity).apply {
                text = getString(R.string.intro_hero_subtitle)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.accent_pink))
                setPadding(0, dp(6), 0, 0)
            })
            addView(TextView(this@IntroActivity).apply {
                text = getString(R.string.intro_desc_body)
                textSize = 13f
                setTextColor(getColor(R.color.summary_text))
                setPadding(0, dp(6), 0, 0)
            })
            addView(statusLine)
            addView(
                LinearLayout(this@IntroActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(openBiliButton, LinearLayout.LayoutParams(0, dp(44), 1f))
                    addView(
                        checkUpdateButton,
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                            marginStart = dp(10)
                        },
                    )
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(16) },
            )
        }
    }

    private fun createGuideCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(getColor(R.color.card_background))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            addView(TextView(this@IntroActivity).apply {
                text = getString(R.string.intro_launcher_title)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.title_text))
            })
            addView(TextView(this@IntroActivity).apply {
                text = getString(R.string.intro_guide_hint)
                textSize = 13f
                setTextColor(getColor(R.color.summary_text))
                setPadding(0, dp(6), 0, 0)
            })
            addView(
                TextView(this@IntroActivity).apply {
                    text = getString(R.string.intro_repo_notice)
                    textSize = 12f
                    setTextColor(getColor(R.color.tile_general_icon))
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(getColor(R.color.tile_general_background))
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(10) },
            )
        }
    }

    private fun createSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(getColor(R.color.section_label))
            setPadding(dp(4), dp(16), dp(4), dp(8))
        }
    }

    private fun createSectionCard(rows: List<View>): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(getColor(R.color.card_background))
            }
            clipToOutline = true
            rows.forEachIndexed { index, row ->
                addView(row)
                if (index != rows.lastIndex) {
                    addView(
                        View(this@IntroActivity).apply {
                            setBackgroundColor(getColor(R.color.divider))
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dp(1),
                            ).apply { marginStart = dp(16) }
                        },
                    )
                }
            }
        }
    }

    private fun createNavRow(
        title: String,
        summary: String,
        iconRes: Int,
        tileBgRes: Int,
        tileIconRes: Int,
        onClick: () -> Unit,
    ): View {
        val summaryColor = getColor(R.color.summary_text)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            val mask = GradientDrawable().apply { setColor(Color.WHITE) }
            background = RippleDrawable(ColorStateList.valueOf(withAlpha(summaryColor, 0x28)), null, mask)
            setOnClickListener { onClick() }

            addView(ImageView(this@IntroActivity).apply {
                setImageResource(iconRes)
                setColorFilter(getColor(tileIconRes))
                setPadding(dp(9), dp(9), dp(9), dp(9))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(getColor(tileBgRes))
                }
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(14) }
            })
            addView(
                LinearLayout(this@IntroActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, dp(10), 0)
                    addView(TextView(this@IntroActivity).apply {
                        text = title
                        textSize = 15f
                        setTextColor(getColor(R.color.title_text))
                    })
                    addView(TextView(this@IntroActivity).apply {
                        text = summary
                        textSize = 12f
                        setTextColor(summaryColor)
                        setPadding(0, dp(3), 0, 0)
                    })
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(ImageView(this@IntroActivity).apply {
                setImageResource(R.drawable.ic_chevron_right)
                setColorFilter(summaryColor)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            })
        }
    }

    private fun createPillButton(label: String, bgColor: Int, fgColor: Int, onClick: () -> Unit): View {
        val radius = dp(22).toFloat()
        val content = GradientDrawable().apply {
            cornerRadius = radius
            setColor(bgColor)
        }
        val mask = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        return TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            setTextColor(fgColor)
            setTypeface(typeface, Typeface.BOLD)
            isClickable = true
            isFocusable = true
            background = RippleDrawable(ColorStateList.valueOf(withAlpha(fgColor, 0x33)), content, mask)
            setOnClickListener { onClick() }
        }
    }

    private fun launchBilibili() {
        val recorded = prefs.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, null)
            ?.takeIf { it.isNotBlank() && it != "unknown" }
        val candidates = buildList {
            if (recorded != null) add(recorded)
            SUPPORTED_HOST_PACKAGES.forEach { if (it !in this) add(it) }
        }
        val launchIntent = candidates.firstNotNullOfOrNull { pkg ->
            packageManager.getLaunchIntentForPackage(pkg)
        }
        if (launchIntent == null) {
            Toast.makeText(this, R.string.intro_open_bilibili_failed, Toast.LENGTH_SHORT).show()
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(launchIntent) }
            .onFailure {
                Toast.makeText(this, R.string.intro_open_bilibili_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openSettingsPage(page: String) {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_PAGE, page)
        }
        startActivity(intent)
    }

    private fun startUpdateCheck() {
        if (updateChecking) return
        updateChecking = true
        updateCheckStatusView?.apply {
            visibility = View.VISIBLE
            text = getString(R.string.check_update_checking_summary)
        }
        updateCheckCall = UpdateChecker.check(
            BuildConfig.RELEASE_NAME,
            BuildConfig.VERSION_CODE,
            ModuleSettings.isAcceptPrereleaseUpdateEnabled(prefs),
        ) { result ->
            updateCheckCall = null
            updateChecking = false
            if (isFinishing || isDestroyed) return@check
            when (result.status) {
                UpdateChecker.Status.UP_TO_DATE -> {
                    val message = getString(
                        R.string.check_update_up_to_date_summary,
                        result.latestVersion ?: BuildConfig.RELEASE_NAME,
                    )
                    updateCheckStatusView?.apply {
                        visibility = View.VISIBLE
                        text = message
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }

                UpdateChecker.Status.UPDATE_AVAILABLE -> {
                    val latest = result.latestVersion.orEmpty()
                    updateCheckStatusView?.apply {
                        visibility = View.VISIBLE
                        text = getString(R.string.check_update_available_summary, latest)
                    }
                    showUpdateAvailableDialog(result)
                }

                UpdateChecker.Status.FAILED -> {
                    val message = getString(R.string.check_update_failed_summary)
                    updateCheckStatusView?.apply {
                        visibility = View.VISIBLE
                        text = message
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateAvailableDialog(result: UpdateChecker.Result) {
        val latestVersionText = formatVersionWithCode(result.latestVersion.orEmpty(), result.latestVersionCode)
        val currentVersionText = formatVersionWithCode(
            result.currentVersion ?: BuildConfig.RELEASE_NAME,
            BuildConfig.VERSION_CODE,
        )
        val header = buildString {
            append(getString(R.string.check_update_dialog_message, latestVersionText, currentVersionText))
            if (result.apkSizeBytes > 0) {
                append('\n')
                append(getString(R.string.check_update_apk_size, formatSize(result.apkSizeBytes)))
            }
        }
        val notesRaw = result.releaseNotes?.takeIf { it.isNotBlank() }
        val notesSpanned = notesRaw?.let { MarkdownFormatter.toSpanned(it) }
            ?: SpannableString(getString(R.string.check_update_notes_empty))

        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(TextView(this@IntroActivity).apply {
                text = header
                textSize = 14f
                setTextColor(getColor(R.color.title_text))
            })
            addView(TextView(this@IntroActivity).apply {
                text = getString(R.string.check_update_notes_label)
                textSize = 12f
                setTextColor(getColor(R.color.summary_text))
                setPadding(0, dp(12), 0, dp(4))
            })
            addView(TextView(this@IntroActivity).apply {
                text = notesSpanned
                textSize = 13f
                setTextColor(getColor(R.color.title_text))
                movementMethod = LinkMovementMethod.getInstance()
            })
        }
        val scroll = ScrollView(this).apply { addView(dialogContent) }

        AlertDialog.Builder(this)
            .setTitle(R.string.check_update_dialog_title)
            .setView(scroll)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.check_update_dialog_confirm) { _, _ ->
                openUrl(result.releaseUrl ?: UpdateChecker.RELEASE_PAGE_URL)
            }
            .show()
    }

    private fun formatVersionWithCode(version: String, code: Int): String =
        if (code > 0) getString(R.string.check_update_version_with_code, version, code) else version

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb < 1024) {
            String.format(Locale.getDefault(), "%.1f KB", kb)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, getString(R.string.open_url_failed_toast), Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyWindowInsets(scrollView: ScrollView, content: LinearLayout) {
        val baseLeft = content.paddingLeft
        val baseTop = content.paddingTop
        val baseRight = content.paddingRight
        val baseBottom = content.paddingBottom
        scrollView.setOnApplyWindowInsetsListener { _, insets ->
            val safeTop: Int
            val safeBottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                safeTop = safe.top
                safeBottom = safe.bottom
            } else {
                val cutout = insets.displayCutout
                @Suppress("DEPRECATION")
                safeTop = maxOf(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0)
                @Suppress("DEPRECATION")
                safeBottom = maxOf(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0)
            }
            content.setPadding(baseLeft, baseTop + safeTop, baseRight, baseBottom + safeBottom)
            insets
        }
        scrollView.requestApplyInsets()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TELEGRAM_CHANNEL_URL = "https://t.me/bbx_show"
        private val SUPPORTED_HOST_PACKAGES = listOf(
            "tv.danmaku.bili",
            "com.bilibili.app.blue",
            "com.bilibili.app.in",
            "top.nkbe.npatch",
        )
    }
}
