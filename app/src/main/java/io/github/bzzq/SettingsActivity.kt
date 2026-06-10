package io.github.bzzq

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    private val prefs by lazy { getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE) }
    private val tagCheckBoxes = mutableMapOf<String, CheckBox>()
    private lateinit var disableLongPressCopySwitch: Switch
    private lateinit var enhanceLongPressCopySwitch: Switch
    private lateinit var storyVideoAdSwitch: Switch
    private lateinit var blockedCountView: TextView
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F6F7F8"))
        }

        mainLayout.addView(createToolbar())

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        content.addView(createSectionTitle(R.string.account_tools_section_title))
        content.addView(
            createClickableItem(
                R.string.copy_access_key_title,
                R.string.copy_access_key_summary,
            ) {
                val token = prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)
                if (token.isNullOrEmpty()) {
                    Toast.makeText(this, R.string.copy_access_key_not_found, Toast.LENGTH_SHORT).show()
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("access_key", token))
                    Toast.makeText(this, R.string.copy_access_key_success, Toast.LENGTH_SHORT).show()
                }
            },
        )

        content.addView(createSectionTitle(R.string.general_features_section_title))
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.skip_splash_ad_title,
                summaryRes = R.string.skip_splash_ad_summary,
                key = ModuleSettings.KEY_SKIP_SPLASH_AD_ENABLED,
                defaultValue = true,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.unlock_video_features_title,
                summaryRes = R.string.unlock_video_features_summary,
                key = ModuleSettings.KEY_UNLOCK_VIDEO_FEATURES_ENABLED,
                defaultValue = true,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.auto_like_video_detail_title,
                summaryRes = R.string.auto_like_video_detail_summary,
                key = ModuleSettings.KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED,
                defaultValue = false,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.fix_live_quality_url_title,
                summaryRes = R.string.fix_live_quality_url_summary,
                key = ModuleSettings.KEY_FIX_LIVE_QUALITY_URL_ENABLED,
                defaultValue = false,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.skip_mini_game_reward_ad_title,
                summaryRes = R.string.skip_mini_game_reward_ad_summary,
                key = ModuleSettings.KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED,
                defaultValue = true,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.block_live_reservation_title,
                summaryRes = R.string.block_live_reservation_summary,
                key = ModuleSettings.KEY_BLOCK_LIVE_RESERVATION_ENABLED,
                defaultValue = false,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.block_live_room_qoe_popup_title,
                summaryRes = R.string.block_live_room_qoe_popup_summary,
                key = ModuleSettings.KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED,
                defaultValue = false,
            ),
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.disable_long_press_copy_title,
                summaryRes = R.string.disable_long_press_copy_summary,
                key = ModuleSettings.KEY_DISABLE_LONG_PRESS_COPY_ENABLED,
                defaultValue = false,
            ).also { layout ->
                disableLongPressCopySwitch = layout.getChildAt(1) as Switch
            },
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.enhance_long_press_copy_title,
                summaryRes = R.string.enhance_long_press_copy_summary,
                key = ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED,
                defaultValue = false,
            ).also { layout ->
                enhanceLongPressCopySwitch = layout.getChildAt(1) as Switch
            },
        )
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.purify_share_title,
                summaryRes = R.string.purify_share_summary,
                key = ModuleSettings.KEY_PURIFY_SHARE_ENABLED,
                defaultValue = false,
            ),
        )
        content.addView(createSectionTitle(R.string.story_filter_section_title))
        content.addView(
            createFeatureSwitch(
                titleRes = R.string.purify_story_video_ad_title,
                summaryRes = R.string.purify_story_video_ad_summary,
                key = ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED,
                defaultValue = false,
            ).also { layout ->
                storyVideoAdSwitch = layout.getChildAt(1) as Switch
            },
        )

        val tagsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        ModuleSettings.storyVideoAdTags.forEach { tag ->
            val checkBox = CheckBox(this).apply {
                text = tag.label
                textSize = 15f
                setTextColor(Color.parseColor("#212121"))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveSelectedTags()
                }
            }
            tagCheckBoxes[tag.key] = checkBox
            tagsLayout.addView(checkBox)
        }
        content.addView(tagsLayout)

        blockedCountView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(12), 0, 0)
        }
        content.addView(blockedCountView)

        mainLayout.addView(
            ScrollView(this).apply {
                addView(
                    content,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        setContentView(mainLayout)
        refresh()
    }

    private fun createToolbar(): View {
        val title = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 20f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(2).toFloat()
            addView(title)
        }
    }

    private fun createSectionTitle(titleRes: Int): TextView {
        return TextView(this).apply {
            text = getString(titleRes)
            textSize = 13f
            setTextColor(Color.parseColor("#FB7299"))
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun createFeatureSwitch(
        titleRes: Int,
        summaryRes: Int,
        key: String,
        defaultValue: Boolean,
    ): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, dp(16), 0)
        }

        textLayout.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 17f
            setTextColor(Color.parseColor("#212121"))
        })
        textLayout.addView(TextView(this).apply {
            text = getString(summaryRes)
            textSize = 13f
            setTextColor(Color.parseColor("#757575"))
            setPadding(0, dp(4), 0, 0)
        })
        textLayout.addView(TextView(this).apply {
            text = getString(
                if (defaultValue) R.string.default_enabled_hint else R.string.default_disabled_hint,
            )
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            setPadding(0, dp(6), 0, 0)
        })

        val switchView = Switch(this).apply {
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, isChecked ->
                if (!refreshing) {
                    prefs.edit().putBoolean(key, isChecked).apply()
                    if (
                        key == ModuleSettings.KEY_DISABLE_LONG_PRESS_COPY_ENABLED ||
                        key == ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED
                    ) {
                        refresh()
                    }
                }
            }
        }

        layout.addView(textLayout)
        layout.addView(switchView)
        return layout
    }

    private fun createClickableItem(
        titleRes: Int,
        summaryRes: Int,
        onClick: () -> Unit,
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }

            addView(TextView(this@SettingsActivity).apply {
                text = getString(titleRes)
                textSize = 17f
                setTextColor(Color.parseColor("#212121"))
            })
            addView(TextView(this@SettingsActivity).apply {
                text = getString(summaryRes)
                textSize = 13f
                setTextColor(Color.parseColor("#757575"))
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun refresh() {
        refreshing = true
        val enabled = ModuleSettings.isPurifyStoryVideoAdEnabled(prefs)
        val selectedTags = ModuleSettings.getPurifyStoryVideoAdTags(prefs)
        val disableLongPressCopy = ModuleSettings.isDisableLongPressCopyEnabled(prefs)

        storyVideoAdSwitch.isChecked = enabled
        disableLongPressCopySwitch.isChecked = disableLongPressCopy
        enhanceLongPressCopySwitch.isEnabled = disableLongPressCopy
        if (!disableLongPressCopy && enhanceLongPressCopySwitch.isChecked) {
            prefs.edit().putBoolean(ModuleSettings.KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, false).apply()
        }
        enhanceLongPressCopySwitch.isChecked =
            disableLongPressCopy && ModuleSettings.isEnhanceLongPressCopyEnabled(prefs)
        tagCheckBoxes.forEach { (key, checkBox) ->
            checkBox.isEnabled = enabled
            checkBox.isChecked = key in selectedTags
        }
        blockedCountView.text = getString(
            R.string.purify_story_video_ad_blocked_count,
            prefs.getInt(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT, 0),
        )
        refreshing = false
    }

    private fun saveSelectedTags() {
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_TAGS, selectedTagKeys())
            .apply()
    }

    private fun selectedTagKeys(): Set<String> =
        tagCheckBoxes.filterValues { it.isChecked }.keys.toSet()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
