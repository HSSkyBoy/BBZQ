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
    private lateinit var storyVideoAdSwitch: Switch
    private lateinit var blockedCountView: TextView
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F4F4"))
        }

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            elevation = dp(2).toFloat()
        }
        toolbar.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 20f
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        mainLayout.addView(toolbar)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        root.addView(createSectionTitle("账号工具"))
        root.addView(createClickableItem(
            R.string.copy_access_key_title,
            R.string.copy_access_key_summary
        ) {
            val token = prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)
            if (token.isNullOrEmpty()) {
                Toast.makeText(this, R.string.copy_access_key_not_found, Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("access_key", token)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copy_access_key_success, Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(createSectionTitle("通用功能"))
        
        root.addView(createFeatureSwitch(
            R.string.skip_splash_ad_title,
            ModuleSettings.KEY_SKIP_SPLASH_AD_ENABLED,
            true,
        ))
        root.addView(createFeatureSwitch(
            R.string.unlock_video_features_title,
            ModuleSettings.KEY_UNLOCK_VIDEO_FEATURES_ENABLED,
            true,
        ))
        root.addView(createFeatureSwitch(
            R.string.auto_like_video_detail_title,
            ModuleSettings.KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED,
            false,
        ))
        root.addView(createFeatureSwitch(
            R.string.fix_live_quality_url_title,
            ModuleSettings.KEY_FIX_LIVE_QUALITY_URL_ENABLED,
            false,
        ))
        root.addView(createFeatureSwitch(
            R.string.skip_mini_game_reward_ad_title,
            ModuleSettings.KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED,
            true,
        ))
        root.addView(createFeatureSwitch(
            R.string.block_live_reservation_title,
            ModuleSettings.KEY_BLOCK_LIVE_RESERVATION_ENABLED,
            false,
        ))

        root.addView(createSectionTitle(getString(R.string.purify_story_video_ad_title)))

        storyVideoAdSwitch = Switch(this).apply {
            text = "启用过滤"
            textSize = 18f
            setPadding(0, dp(12), 0, dp(8))
            setOnCheckedChangeListener { _, isChecked ->
                if (refreshing) return@setOnCheckedChangeListener
                prefs.edit().putBoolean(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED, isChecked).apply()
                if (isChecked && selectedTagKeys().isEmpty()) {
                    prefs.edit()
                        .putStringSet(
                            ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_TAGS,
                            ModuleSettings.defaultStoryVideoAdTags,
                        )
                        .apply()
                }
                refresh()
            }
        }
        root.addView(storyVideoAdSwitch)

        val tagsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        ModuleSettings.storyVideoAdTags.forEach { tag ->
            val checkBox = CheckBox(this).apply {
                text = tag.label
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveSelectedTags()
                }
            }
            tagCheckBoxes[tag.key] = checkBox
            tagsLayout.addView(checkBox)
        }
        root.addView(tagsLayout)

        blockedCountView = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(16), 0, dp(16))
            setTextColor(Color.GRAY)
        }
        root.addView(blockedCountView)

        mainLayout.addView(ScrollView(this).apply {
            addView(root)
        })
        
        setContentView(mainLayout)
        refresh()
    }

    private fun createSectionTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.parseColor("#FB7299"))
            setPadding(0, dp(16), 0, dp(8))
        }
    }

    private fun createFeatureSwitch(titleRes: Int, key: String, defaultValue: Boolean): Switch {
        return Switch(this).apply {
            text = getString(titleRes)
            textSize = 18f
            setPadding(0, dp(12), 0, dp(12))
            isChecked = prefs.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, isChecked ->
                if (!refreshing) prefs.edit().putBoolean(key, isChecked).apply()
            }
        }
    }

    private fun createClickableItem(titleRes: Int, summaryRes: Int, onClick: () -> Unit): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { onClick() }
        }
        layout.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 18f
            setTextColor(Color.BLACK)
        })
        layout.addView(TextView(this).apply {
            text = getString(summaryRes)
            textSize = 14f
            setTextColor(Color.GRAY)
        })
        return layout
    }

    private fun refresh() {
        refreshing = true
        val enabled = ModuleSettings.isPurifyStoryVideoAdEnabled(prefs)
        val selectedTags = ModuleSettings.getPurifyStoryVideoAdTags(prefs)

        storyVideoAdSwitch.isChecked = enabled
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

    private fun selectedTagKeys(): Set<String> {
        return tagCheckBoxes
            .filterValues { it.isChecked }
            .keys
            .toSet()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
