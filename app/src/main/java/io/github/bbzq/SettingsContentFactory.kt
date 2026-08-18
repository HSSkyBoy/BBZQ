package io.github.bbzq

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.SystemClock
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.bbzq.DesktopIconHelper
import io.github.bbzq.R
import okhttp3.Call
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsContentFactory(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val page: String,
    private val openPage: (String) -> Unit,
    private val onExportClick: () -> Unit,
    private val onImportClick: () -> Unit,
    private val onCustomSkinImportClick: () -> Unit,
) {
    private val pageBackgroundColor: Int by lazy { context.getColor(R.color.page_background) }
    private val titleTextColor: Int by lazy { context.getColor(R.color.title_text) }
    private val summaryTextColor: Int by lazy { context.getColor(R.color.summary_text) }
    private val sectionLabelColor: Int by lazy { context.getColor(R.color.section_label) }
    private val cardBackgroundColor: Int by lazy { context.getColor(R.color.card_background) }
    private val dividerColor: Int by lazy { context.getColor(R.color.divider) }
    private val disableConfirmColor: Int by lazy { context.getColor(R.color.disable_confirm) }
    private val cancelActionColor: Int by lazy { context.getColor(R.color.cancel_action) }
    private val tagCheckBoxes = mutableMapOf<String, CheckBox>()
    private val bottomBarItemCheckBoxes = mutableMapOf<String, CheckBox>()
    private val homeRecommendItemCheckBoxes = mutableMapOf<String, CheckBox>()
    private val homeRecommendTabCheckBoxes = mutableMapOf<String, CheckBox>()
    private val homeComponentCheckBoxes = mutableMapOf<String, CheckBox>()
    private val sponsorBlockCategoryButtons = mutableMapOf<String, Button>()
    private lateinit var disableLongPressCopySwitch: Switch
    private lateinit var enhanceLongPressCopySwitch: Switch
    private lateinit var downloadThreadSwitch: Switch
    private lateinit var downloadConcurrencyRow: View
    private lateinit var downloadConcurrencySummary: TextView
    private lateinit var bottomBarSwitch: Switch
    private lateinit var customThemeSwitch: Switch
    private lateinit var customSkinSwitch: Switch
    private lateinit var customThemeColorRow: View
    private lateinit var customThemeColorSummary: TextView
    private lateinit var customThemeColorSwatch: View
    private lateinit var customSkinConfigRow: View
    private lateinit var customSkinConfigSummary: TextView
    private lateinit var commentKeywordFilterSwitch: Switch
    private lateinit var commentKeywordRow: View
    private lateinit var commentKeywordSummary: TextView
    private lateinit var commentMinLevelSwitch: Switch
    private lateinit var commentMinLevelRow: View
    private lateinit var commentMinLevelSummary: TextView
    private lateinit var homeRecommendItemSwitch: Switch
    private lateinit var homeRecommendTitleKeywordRow: View
    private lateinit var homeRecommendTabSwitch: Switch
    private lateinit var homeRecommendTitleKeywordSummaryView: TextView
    private lateinit var hideAllHomeComponentsSwitch: Switch
    private lateinit var customHomeComponentHideSwitch: Switch
    private lateinit var storyVideoAdSwitch: Switch
    private lateinit var storyVideoImmersiveFullscreenSwitch: Switch
    private lateinit var storyVideoComponentAlphaSummary: TextView
    private lateinit var storyVideoComponentAlphaSeekBar: SeekBar
    private lateinit var skipVideoAdAutoLikeSwitch: Switch
    private lateinit var blockedCountView: TextView
    private lateinit var customMineComponentHideSwitch: Switch
    private lateinit var mineComponentPickerRow: View
    private lateinit var mineComponentPickerSummary: TextView
    private lateinit var symbolScanStatusSummary: TextView
    private lateinit var customCdnHostSummary: TextView
    /** 「检查更新」行的摘要文本视图，用于回显检查状态；界面销毁时置空避免泄漏。 */
    private var updateCheckSummaryView: TextView? = null

    /** 是否正在检查更新，用于去重并发点击。 */
    private var updateChecking = false

    /** 当前在途的检查更新请求，界面销毁时取消。 */
    private var updateCheckCall: Call? = null
    private var versionTapCount = 0
    private var firstVersionTapAt = 0L
    private var refreshing = false

    fun createScrollView(): ScrollView {
        val pageRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pageBackgroundColor)
            setPadding(dp(12), dp(12), dp(12), dp(24))
        }

        when (page) {
            SettingsActivity.PAGE_SKIP_VIDEO_AD_SWITCH -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.section_skip_video_ad)))
                pageRoot.addView(createSectionCard(skipVideoAdOverviewRows()))
                pageRoot.addView(createSectionLabel(context.getString(R.string.section_thanks)))
                pageRoot.addView(createSectionCard(skipVideoAdCreditRows()))
            }

            SettingsActivity.PAGE_SKIP_VIDEO_AD_CATEGORY -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.section_category_filter)))
                pageRoot.addView(createSectionCard(skipVideoAdCategoryRows()))
                pageRoot.addView(createSectionLabel(context.getString(R.string.section_thanks)))
                pageRoot.addView(createSectionCard(skipVideoAdCreditRows()))
            }

            SettingsActivity.PAGE_HIDDEN_FEATURES -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.about_hidden_features_title)))
                pageRoot.addView(createSectionCard(hiddenFeaturesRows()))
            }

            SettingsActivity.PAGE_UPDATE -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.about_update_title)))
                pageRoot.addView(createSectionCard(updateRows()))
            }

            SettingsActivity.PAGE_CONFIG_BACKUP -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.about_config_backup_title)))
                pageRoot.addView(createSectionCard(configBackupRows()))
            }

            else -> {
                pageRoot.addView(createSectionLabel(context.getString(R.string.section_share_link)))
                pageRoot.addView(createSectionCard(shareRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_copy_enhance)))
                pageRoot.addView(createSectionCard(copyRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_startup_purify)))
                pageRoot.addView(createSectionCard(startupRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_download_features)))
                pageRoot.addView(createSectionCard(downloadRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_video_cdn)))
                pageRoot.addView(createSectionCard(customCdnRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_home_recommend_purify)))
                pageRoot.addView(createSectionCard(homeRecommendRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_dynamic_page)))
                pageRoot.addView(createSectionCard(dynamicRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_ui_customize)))
                pageRoot.addView(createSectionCard(bottomBarRows()))

                val playbackSectionRows = playbackRows()
                if (playbackSectionRows.isNotEmpty()) {
                    pageRoot.addView(createSectionLabel(context.getString(R.string.section_playback_purify)))
                    pageRoot.addView(createSectionCard(playbackSectionRows))
                }

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_comment_purify)))
                pageRoot.addView(createSectionCard(commentRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_mine_customize)))
                pageRoot.addView(createSectionCard(mineProfileRows()))

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_story_purify)))
                pageRoot.addView(createSectionCard(storyRows()))

                if (hasHiddenFeatures()) {
                    pageRoot.addView(createSectionLabel(context.getString(R.string.about_hidden_features_title)))
                    pageRoot.addView(createSectionCard(hiddenFeaturesEntryRows()))
                }

                pageRoot.addView(createSectionLabel(context.getString(R.string.section_about)))
                pageRoot.addView(createSectionCard(aboutRows()))
            }
        }

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

    private fun shareRows(): List<View> {
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

    private fun copyRows(): List<View> {
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

    private fun startupRows(): List<View> {
        val rows = mutableListOf<View>()
        if (hasHiddenFeatures()) {
            rows += createSwitchRow(
                context.getString(R.string.startup_skip_splash_title),
                context.getString(R.string.startup_skip_splash_summary),
                ModuleSettings.KEY_SKIP_SPLASH_AD_ENABLED,
                true,
            )
        }
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
        return rows
    }

    private fun downloadRows(): List<View> {
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

    private fun homeRecommendRows(): List<View> {
        val rows = mutableListOf<View>()
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_vertical_av_detail_title),
            context.getString(R.string.home_recommend_vertical_av_detail_summary),
            ModuleSettings.KEY_HOME_RECOMMEND_VERTICAL_AV_DETAIL_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_preload_title),
            context.getString(R.string.home_recommend_preload_summary),
            ModuleSettings.KEY_HOME_RECOMMEND_PRELOAD_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_custom_filter_title),
            context.getString(R.string.home_recommend_custom_filter_summary),
            ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED,
            false,
        ) {
            homeRecommendItemSwitch = it
        }

        rows += createHomeRecommendTitleKeywordRow()
        rows += createInfoRow(
            context.getString(R.string.home_recommend_custom_filter_item_title),
            context.getString(R.string.home_recommend_custom_filter_info_summary),
        )
        rows += createHomeRecommendItemGroup(homeRecommendItems())
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_tab_filter_title),
            context.getString(R.string.home_recommend_tab_filter_summary),
            ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED,
            false,
        ) {
            homeRecommendTabSwitch = it
        }
        val tabs = homeRecommendTabs()
        if (tabs.isEmpty()) {
            rows += createInfoRow(
                context.getString(R.string.home_recommend_tab_item_title),
                context.getString(R.string.home_recommend_tab_unavailable_summary),
            )
        } else {
            rows += createInfoRow(
                context.getString(R.string.home_recommend_tab_item_title),
                context.getString(R.string.home_recommend_tab_info_summary),
            )
            rows += createHomeRecommendTabGroup(tabs)
        }
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_hide_all_title),
            context.getString(R.string.home_recommend_hide_all_summary),
            ModuleSettings.KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED,
            false,
        ) {
            hideAllHomeComponentsSwitch = it
        }
        rows += createSwitchRow(
            context.getString(R.string.home_recommend_custom_hide_title),
            context.getString(R.string.home_recommend_custom_hide_summary),
            ModuleSettings.KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED,
            false,
        ) {
            customHomeComponentHideSwitch = it
        }

        val components = homeComponentItems()
        if (components.isEmpty()) {
            rows += createInfoRow(
                context.getString(R.string.home_component_title),
                context.getString(R.string.home_component_unavailable_summary),
            )
        } else {
            rows += createInfoRow(
                context.getString(R.string.home_component_title),
                context.getString(R.string.home_component_info_summary),
            )
            rows += createHomeComponentGroup(components)
        }
        return rows
    }

    private fun dynamicRows(): List<View> {
        return listOf(
            createSwitchRow(
                context.getString(R.string.dynamic_preferred_video_tab_title),
                context.getString(R.string.dynamic_preferred_video_tab_summary),
                ModuleSettings.KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.dynamic_remove_city_tab_title),
                context.getString(R.string.dynamic_remove_city_tab_summary),
                ModuleSettings.KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.dynamic_remove_school_tab_title),
                context.getString(R.string.dynamic_remove_school_tab_summary),
                ModuleSettings.KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED,
                false,
            ),
        )
    }

    private fun bottomBarRows(): List<View> {
        val rows = mutableListOf<View>()
        rows += createSwitchRow(
            context.getString(R.string.bottom_bar_title),
            context.getString(R.string.bottom_bar_summary),
            ModuleSettings.KEY_CUSTOM_BOTTOM_BAR_ENABLED,
            false,
        ) {
            bottomBarSwitch = it
        }
        rows += createSwitchRow(
            context.getString(R.string.custom_theme_title),
            context.getString(R.string.custom_theme_summary),
            ModuleSettings.KEY_CUSTOM_THEME_ENABLED,
            false,
        ) {
            customThemeSwitch = it
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
            context.getString(R.string.home_top_bar_promotion_title),
            context.getString(R.string.home_top_bar_promotion_summary),
            ModuleSettings.KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.home_search_default_word_title),
            context.getString(R.string.home_search_default_word_summary),
            ModuleSettings.KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED,
            false,
        )
        return rows
    }

    private fun playbackRows(): List<View> {
        if (!hasHiddenFeatures()) return emptyList()
        val rows = mutableListOf<View>()
        if (ModuleSettings.isSkipVideoAdSettingsVisible(prefs)) {
            rows += createInfoRow(
                context.getString(R.string.section_skip_video_ad),
                context.getString(R.string.skip_video_ad_entry_shown_summary),
            )
        }
        rows += createSwitchRow(
            context.getString(R.string.playback_hide_banner_title),
            context.getString(R.string.playback_hide_banner_summary),
            ModuleSettings.KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_block_chronos_promotion_title),
            context.getString(R.string.playback_block_chronos_promotion_summary),
            ModuleSettings.KEY_BLOCK_CHRONOS_PROMOTION_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_block_activity_meta_sticker_title),
            context.getString(R.string.playback_block_activity_meta_sticker_summary),
            ModuleSettings.KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_skip_reward_title),
            context.getString(R.string.playback_skip_reward_summary),
            ModuleSettings.KEY_SKIP_REWARD_AD_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_auto_like_title),
            context.getString(R.string.playback_auto_like_summary),
            ModuleSettings.KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_transparent_status_bar_title),
            context.getString(R.string.playback_transparent_status_bar_summary),
            ModuleSettings.KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_portrait_control_title),
            context.getString(R.string.playback_portrait_control_summary),
            ModuleSettings.KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_triple_speed_title),
            context.getString(R.string.playback_triple_speed_summary),
            ModuleSettings.KEY_PLAYER_TRIPLE_SPEED_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.playback_long_press_speed_lock_title),
            context.getString(R.string.playback_long_press_speed_lock_summary),
            ModuleSettings.KEY_PLAYER_LONG_PRESS_SPEED_LOCK_ENABLED,
            false,
        )
        return rows
    }

    private fun customCdnRows(): List<View> = listOf(
        createSwitchRow(
            context.getString(R.string.custom_cdn_enabled_title),
            context.getString(R.string.custom_cdn_enabled_summary),
            ModuleSettings.KEY_CUSTOM_CDN_ENABLED,
            false,
        ),
        createCustomCdnHostRow(),
    )

    private fun commentRows(): List<View> {
        return listOf(
            createSwitchRow(
                context.getString(R.string.comment_keyword_filter_title),
                context.getString(R.string.comment_keyword_filter_summary),
                ModuleSettings.KEY_COMMENT_KEYWORD_FILTER_ENABLED,
                false,
            ) {
                commentKeywordFilterSwitch = it
            },
            createCommentKeywordRow(),
            createSwitchRow(
                context.getString(R.string.comment_min_level_switch_title),
                context.getString(R.string.comment_min_level_switch_summary),
                ModuleSettings.KEY_COMMENT_MIN_LEVEL_ENABLED,
                false,
            ) {
                commentMinLevelSwitch = it
            },
            createCommentMinLevelRow(),
            createSwitchRow(
                context.getString(R.string.comment_disable_title),
                context.getString(R.string.comment_disable_summary),
                ModuleSettings.KEY_COMMENT_DISABLE,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_quick_reply_title),
                context.getString(R.string.comment_no_quick_reply_summary),
                ModuleSettings.KEY_COMMENT_NO_QUICK_REPLY,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_vote_title),
                context.getString(R.string.comment_no_vote_summary),
                ModuleSettings.KEY_COMMENT_NO_VOTE,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_follow_title),
                context.getString(R.string.comment_no_follow_summary),
                ModuleSettings.KEY_COMMENT_NO_FOLLOW,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_search_title),
                context.getString(R.string.comment_no_search_summary),
                ModuleSettings.KEY_COMMENT_NO_SEARCH,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_empty_page_title),
                context.getString(R.string.comment_no_empty_page_summary),
                ModuleSettings.KEY_COMMENT_NO_EMPTY_PAGE,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_qoe_title),
                context.getString(R.string.comment_no_qoe_summary),
                ModuleSettings.KEY_COMMENT_NO_QOE,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.comment_no_operation_title),
                context.getString(R.string.comment_no_operation_summary),
                ModuleSettings.KEY_COMMENT_NO_OPERATION,
                false,
            ),
        )
    }

    private fun mineProfileRows(): List<View> {
        return listOf(
            createSwitchRow(
                context.getString(R.string.mine_remove_vip_title),
                context.getString(R.string.mine_remove_vip_summary),
                ModuleSettings.KEY_MINE_REMOVE_VIP,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.mine_keep_vip_space_title),
                context.getString(R.string.mine_keep_vip_space_summary),
                ModuleSettings.KEY_MINE_KEEP_VIP_SPACE,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.mine_component_hide_title),
                context.getString(R.string.mine_component_hide_summary),
                ModuleSettings.KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED,
                false,
            ) {
                customMineComponentHideSwitch = it
            },
        ).toMutableList().also { rows ->
            val components = mineComponentItems()
            if (components.isEmpty()) {
                rows += createInfoRow(
                    context.getString(R.string.mine_component_title),
                    context.getString(R.string.mine_component_unavailable_summary),
                )
            } else {
                rows += createInfoRow(
                    context.getString(R.string.mine_component_title),
                    context.getString(R.string.mine_component_info_summary),
                )
                rows += createMineComponentPickerRow(components)
            }
            rows += createSwitchRow(
                context.getString(R.string.full_number_format_title),
                context.getString(R.string.full_number_format_summary),
                ModuleSettings.KEY_FULL_NUMBER_FORMAT_ENABLED,
                false,
            )
        }
    }

    private fun skipVideoAdOverviewRows(): List<View> {
        return listOf(
            createInfoRow(
                context.getString(R.string.skip_video_ad_function_title),
                context.getString(R.string.skip_video_ad_function_summary),
            ),
            createSwitchRow(
                context.getString(R.string.skip_video_ad_enable_title),
                context.getString(R.string.skip_video_ad_enable_summary),
                ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED,
                false,
            ),
            createSwitchRow(
                context.getString(R.string.skip_video_ad_auto_like_title),
                context.getString(R.string.skip_video_ad_auto_like_summary),
                ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED,
                false,
            ) {
                skipVideoAdAutoLikeSwitch = it
            },
            createClickableInfoRow(
                context.getString(R.string.skip_video_ad_category_entry_title),
                context.getString(R.string.skip_video_ad_category_entry_summary),
            ) {
                openPage(SettingsActivity.PAGE_SKIP_VIDEO_AD_CATEGORY)
            },
        )
    }

    private fun skipVideoAdCategoryRows(): List<View> {
        val rows = mutableListOf<View>()
        rows += createInfoRow(
            context.getString(R.string.skip_video_ad_category_description_title),
            context.getString(R.string.skip_video_ad_category_description_summary),
        )
        rows += createInfoRow(
            context.getString(R.string.skip_video_ad_category_state_title),
            if (ModuleSettings.isSkipVideoAdEnabled(prefs)) {
                context.getString(R.string.skip_video_ad_category_state_on)
            } else {
                context.getString(R.string.skip_video_ad_category_state_off)
            },
        )
        rows += createSponsorBlockCategoryGroup()
        rows += createClickableInfoRow(
            context.getString(R.string.skip_video_ad_category_back_title),
            context.getString(R.string.skip_video_ad_category_back_summary),
        ) {
            openPage(SettingsActivity.PAGE_SKIP_VIDEO_AD_SWITCH)
        }
        return rows
    }

    private fun storyRows(): List<View> {
        val rows = mutableListOf<View>()
        rows += createSwitchRow(
            context.getString(R.string.story_video_default_launch_title),
            context.getString(R.string.story_video_default_launch_summary),
            ModuleSettings.KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED,
            false,
        )
        rows += createSwitchRow(
            context.getString(R.string.story_video_immersive_fullscreen_title),
            context.getString(R.string.story_video_immersive_fullscreen_summary),
            ModuleSettings.KEY_STORY_VIDEO_IMMERSIVE_FULLSCREEN_ENABLED,
            false,
        ) {
            storyVideoImmersiveFullscreenSwitch = it
        }
        rows += createSwitchRow(
            context.getString(R.string.story_video_keep_danmaku_on_comment_title),
            context.getString(R.string.story_video_keep_danmaku_on_comment_summary),
            ModuleSettings.KEY_STORY_VIDEO_KEEP_DANMAKU_ON_COMMENT_ENABLED,
            false,
        )
        rows += createStoryVideoComponentAlphaRow()
        if (hasHiddenFeatures()) {
            rows += createSwitchRow(
                context.getString(R.string.purify_story_video_ad_title),
                context.getString(R.string.purify_story_video_ad_summary),
                ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED,
                false,
            ) {
                storyVideoAdSwitch = it
            }
            rows += createInfoRow(
                context.getString(R.string.story_filter_selected_tags_title),
                context.getString(R.string.story_filter_selected_tags_summary),
            )
            rows += createTagGroup()
            rows += createBlockedCountRow()
        }
        return rows
    }

    private fun aboutRows(): List<View> {
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
            context.getString(R.string.about_update_title),
            context.getString(R.string.about_update_summary),
        ) {
            openPage(SettingsActivity.PAGE_UPDATE)
        }
        rows += createClickableInfoRow(
            context.getString(R.string.about_config_backup_title),
            context.getString(R.string.about_config_backup_summary),
        ) {
            openPage(SettingsActivity.PAGE_CONFIG_BACKUP)
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

    private fun updateRows(): List<View> = listOf(
        createUpdateCheckRow(),
        createSwitchRow(
            context.getString(R.string.about_accept_prerelease_title),
            context.getString(R.string.about_accept_prerelease_summary),
            ModuleSettings.KEY_ACCEPT_PRERELEASE_UPDATE,
            false,
        ),
    )

    private fun configBackupRows(): List<View> = listOf(
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

    private fun hiddenFeaturesEntryRows(): List<View> {
        return listOf(
            createClickableInfoRow(
                context.getString(R.string.hidden_features_entry_title),
                "",
            ) {
                openPage(SettingsActivity.PAGE_HIDDEN_FEATURES)
            },
        )
    }

    private fun hiddenFeaturesRows(): List<View> {
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
        }
        return rows
    }

    private fun skipVideoAdCreditRows(): List<View> {
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

    private fun handleVersionRowClick() {
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

    private fun hasHiddenFeatures(): Boolean =
        ModuleSettings.isSkipVideoAdSettingsVisible(prefs) ||
            ModuleSettings.isAccessKeySettingsVisible(prefs) ||
            ModuleSettings.isTryFreeQualitySettingsVisible(prefs)

    private fun createUpdateCheckRow(): View {
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
        }
    }

    private fun handleUpdateCheckClick() {
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

    /** 界面销毁时调用：取消在途请求并断开视图引用，避免回调持有已销毁的 Activity。 */
    fun destroy() {
        updateCheckCall?.cancel()
        updateCheckCall = null
        updateCheckSummaryView = null
    }

    private fun isActivityFinishing(): Boolean {
        val activity = context as? Activity ?: return false
        return activity.isFinishing || activity.isDestroyed
    }

    private fun onUpdateCheckResult(result: UpdateChecker.Result) {
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

    private fun showUpdateAvailableDialog(result: UpdateChecker.Result) {
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
    private fun formatVersionWithCode(version: String, code: Int): String =
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

    private fun handleAccessKeyClick() {
        val key = AccessKeyRepository.read(prefs)
        if (key == null) {
            AlertDialog.Builder(context)
                .setTitle(R.string.access_key_error_title)
                .setMessage(R.string.access_key_error_message)
                .setPositiveButton(R.string.runtime_environment_ok, null)
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
            .setNegativeButton(R.string.runtime_environment_ok, null)
            .show()
    }

    private fun createSymbolCacheRefreshRow(): View {
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
        }
    }

    private fun handleSymbolCacheRefreshClick() {
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

    private fun startSymbolCacheRefresh() {
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

    private fun launchHostAppForSymbolRefresh(appContext: Context) {
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

    private fun symbolRefreshHostPackages(): List<String> {
        val recorded = prefs.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, null)
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_RUNTIME_VALUE }
        return buildList {
            if (recorded != null) add(recorded)
            SUPPORTED_HOST_PACKAGES.forEach { if (it !in this) add(it) }
        }
    }

    private fun symbolScanSummary(): String {
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

    private fun symbolScanReport(): String =
        prefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, null)
            ?: context.getString(R.string.symbol_cache_refresh_no_report)

    private fun formatTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))

    private fun showCustomDownloadConcurrencyDialog() {
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

    private fun createSectionLabel(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(sectionLabelColor)
            setPadding(dp(4), dp(14), dp(4), dp(8))
        }
    }

    private fun createSectionCard(rows: List<View>): View {
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

    private fun createDivider(): View {
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

    private fun createInfoRow(title: String, summary: String): View {
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

    private fun createClickableInfoRow(title: String, summary: String, onClick: () -> Unit): View {
        return createInfoRow(title, summary).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun createHomeRecommendTitleKeywordRow(): View {
        homeRecommendTitleKeywordSummaryView = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showHomeRecommendTitleKeywordDialog() }
            addView(TextView(context).apply {
                text = context.getString(R.string.home_recommend_title_keyword_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(homeRecommendTitleKeywordSummaryView)
        }.also {
            homeRecommendTitleKeywordRow = it
        }
    }

    private fun showHomeRecommendTitleKeywordDialog() {
        val input = EditText(context).apply {
            setText(ModuleSettings.getHomeRecommendTitleKeywordsText(prefs))
            minLines = 4
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            setSelectAllOnFocus(false)
            setHint(R.string.home_recommend_title_keyword_hint)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.home_recommend_title_keyword_dialog_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                prefs.edit()
                    .putString(ModuleSettings.KEY_HOME_RECOMMEND_TITLE_KEYWORDS, input.text?.toString()?.trim().orEmpty())
                    .apply()
                refresh()
            }
            .show()
    }

    private fun createCommentKeywordRow(): View {
        commentKeywordSummary = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showCommentKeywordDialog() }
            addView(TextView(context).apply {
                text = context.getString(R.string.comment_keyword_row_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(commentKeywordSummary)
        }.also {
            commentKeywordRow = it
        }
    }

    private fun showCommentKeywordDialog() {
        val input = EditText(context).apply {
            setText(ModuleSettings.getCommentKeywordsText(prefs))
            minLines = 4
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            setSelectAllOnFocus(false)
            setHint(R.string.comment_keyword_hint)
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.comment_keyword_dialog_title)
            .setView(content)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                prefs.edit()
                    .putString(ModuleSettings.KEY_COMMENT_KEYWORDS, input.text?.toString()?.trim().orEmpty())
                    .apply()
                refresh()
            }
            .show()
    }

    private fun createCommentMinLevelRow(): View {
        return createClickableInfoRow(
            context.getString(R.string.comment_min_level_row_title),
            commentMinLevelSummaryText(),
        ) {
            showCommentMinLevelDialog()
        }.also {
            commentMinLevelRow = it
            commentMinLevelSummary = (it as ViewGroup).getChildAt(1) as TextView
        }
    }

    private fun showCommentMinLevelDialog() {
        val picker = NumberPicker(context).apply {
            minValue = 1
            maxValue = 6
            wrapSelectorWheel = false
            value = ModuleSettings.getCommentMinLevel(prefs).coerceIn(1, 6)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.comment_min_level_dialog_title)
            .setView(picker)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                prefs.edit()
                    .putInt(ModuleSettings.KEY_COMMENT_MIN_LEVEL, picker.value)
                    .apply()
                refresh()
            }
            .show()
    }

    private fun commentKeywordSummaryText(): String {
        val keywords = ModuleSettings.parseCommentKeywords(ModuleSettings.getCommentKeywordsText(prefs))
        if (keywords.isEmpty()) {
            return context.getString(R.string.comment_keyword_empty_summary)
        }
        return context.getString(
            R.string.comment_keyword_enabled_summary,
            keywords.size,
            keywords.take(TITLE_KEYWORD_SUMMARY_MAX_ITEMS).joinToString(context.getString(R.string.list_separator)),
        )
    }

    private fun commentMinLevelSummaryText(): String =
        context.getString(R.string.comment_min_level_row_summary, ModuleSettings.getCommentMinLevel(prefs))

    private fun createCustomThemeColorRow(): View {
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
        }.also { customThemeColorRow = it }
    }

    private fun showCustomThemeColorDialog() {
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

    private fun createCustomSkinConfigRow(): View {
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
        }.also { customSkinConfigRow = it }
    }

    private fun createCustomCdnHostRow(): View {
        customCdnHostSummary = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showCustomCdnHostDialog() }
            addView(TextView(context).apply {
                text = context.getString(R.string.custom_cdn_host_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(customCdnHostSummary)
        }
    }

    private fun showCustomCdnHostDialog() {
        val endpoints = ModuleSettings.cdnEndpoints
        val current = ModuleSettings.getCustomCdnHost(prefs)
        val selected = endpoints.indexOfFirst { it.host == current }
        val labels = (endpoints.map { "${it.name}\n${it.host}" } + context.getString(R.string.custom_cdn_host_custom)).toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.custom_cdn_host_dialog_title)
            .setSingleChoiceItems(labels, if (selected >= 0) selected else endpoints.size) { dialog, which ->
                dialog.dismiss()
                if (which < endpoints.size) {
                    prefs.edit().putString(ModuleSettings.KEY_CUSTOM_CDN_HOST, endpoints[which].host).apply()
                    refresh()
                } else {
                    showCustomCdnHostInputDialog()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showCustomCdnHostInputDialog() {
        val input = EditText(context).apply {
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setText(ModuleSettings.getCustomCdnHost(prefs).orEmpty())
            hint = "upos-sz-mirrorali.bilivideo.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.custom_cdn_host_custom)
            .setMessage(R.string.custom_cdn_host_input_message)
            .setView(input)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = ModuleSettings.normalizeCdnHost(input.text?.toString())
                if (host == null) {
                    input.error = context.getString(R.string.custom_cdn_host_invalid)
                    return@setOnClickListener
                }
                prefs.edit().putString(ModuleSettings.KEY_CUSTOM_CDN_HOST, host).apply()
                refresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showCustomSkinConfigDialog() {
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

    private fun createBlockedCountRow(): View {
        blockedCountView = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(TextView(context).apply {
                text = context.getString(R.string.story_filter_blocked_count_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(blockedCountView.apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createStoryVideoComponentAlphaRow(): View {
        storyVideoComponentAlphaSummary = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
        }
        storyVideoComponentAlphaSeekBar = SeekBar(context).apply {
            max = 100
            progress = ModuleSettings.getStoryVideoComponentAlphaPercent(prefs)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!refreshing) {
                        storyVideoComponentAlphaSummary.text = storyVideoComponentAlphaSummary(progress)
                    }
                    if (fromUser) {
                        prefs.edit()
                            .putInt(ModuleSettings.KEY_STORY_VIDEO_COMPONENT_ALPHA, progress.coerceIn(0, 100))
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
                text = context.getString(R.string.story_video_component_alpha_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(storyVideoComponentAlphaSummary.apply {
                setPadding(0, dp(4), 0, dp(8))
            })
            addView(storyVideoComponentAlphaSeekBar)
        }
    }

    private fun createTagGroup(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            ModuleSettings.storyVideoAdTags.forEach { tag ->
                addView(CheckBox(context).apply {
                    text = tag.label
                    textSize = 14f
                    setTextColor(titleTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setOnCheckedChangeListener { _, _ ->
                        if (!refreshing) saveSelectedTags()
                    }
                    tagCheckBoxes[tag.key] = this
                })
            }
        }
    }

    private fun createSponsorBlockCategoryGroup(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            ModuleSettings.skipVideoAdCategories.forEach { category ->
                addView(createSkipModeRow(category))
            }
        }
    }

    private fun createSkipModeRow(category: SponsorBlockCategory): View {
        val button = Button(context).apply {
            text = ModuleSettings.getSkipVideoAdMode(prefs, category.key).label
            textSize = 12f
            setOnClickListener { showSkipModeDialog(category) }
            sponsorBlockCategoryButtons[category.key] = this
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(
                createTextColumn(category.label, category.summary),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(createCategoryColorLegend(category))
            addView(button)
        }
    }

    private fun showSkipModeDialog(category: SponsorBlockCategory) {
        val currentMode = ModuleSettings.getSkipVideoAdMode(prefs, category.key)
        val modes = SkipVideoAdMode.entries.toTypedArray()
        val labels = modes.map { it.label }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.skip_mode_dialog_title, category.label))
            .setSingleChoiceItems(labels, currentMode.ordinal) { dialog, which ->
                val selectedMode = modes[which]
                prefs.edit()
                    .putInt(
                        "${ModuleSettings.KEY_SKIP_VIDEO_AD_MODE_PREFIX}${category.key}",
                        selectedMode.value,
                    )
                    .apply()
                refresh()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.skip_mode_cancel, null)
            .show()
    }

    private fun createCategoryColorLegend(category: SponsorBlockCategory): View {
        fun swatch(color: Int): View =
            View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(color)
                }
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    marginEnd = dp(6)
                }
            }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, dp(10), 0)
            addView(swatch(category.color))
            addView(swatch(category.previewColor))
        }
    }

    private fun createBottomBarItemGroup(items: List<BottomBarItem>): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            items.forEach { item ->
                addView(CheckBox(context).apply {
                    text = item.name
                    textSize = 14f
                    setTextColor(titleTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setOnCheckedChangeListener { _, _ ->
                        if (!refreshing) saveHiddenBottomBarItems()
                    }
                    bottomBarItemCheckBoxes[item.id] = this
                })
            }
        }
    }

    private fun createHomeRecommendItemGroup(items: List<HomeRecommendItem>): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            items.forEach { item ->
                addView(CheckBox(context).apply {
                    text = "${item.title}\n${item.summary}"
                    textSize = 14f
                    setTextColor(titleTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setOnCheckedChangeListener { _, _ ->
                        if (!refreshing) saveHiddenHomeRecommendItems()
                    }
                    homeRecommendItemCheckBoxes[item.key] = this
                })
            }
        }
    }

    private fun createHomeRecommendTabGroup(items: List<HomeRecommendTabItem>): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            items.forEach { item ->
                addView(CheckBox(context).apply {
                    text = item.displayText()
                    textSize = 14f
                    setTextColor(titleTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setOnCheckedChangeListener { _, _ ->
                        if (!refreshing) saveHiddenHomeRecommendTabs()
                    }
                    homeRecommendTabCheckBoxes[item.key] = this
                })
            }
        }
    }

    private fun createHomeComponentGroup(items: List<HomeComponentItem>): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            items.forEach { item ->
                addView(CheckBox(context).apply {
                    text = "${item.name}\n${item.className}"
                    textSize = 14f
                    setTextColor(titleTextColor)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setOnCheckedChangeListener { _, _ ->
                        if (!refreshing) saveHiddenHomeComponents()
                    }
                    homeComponentCheckBoxes[item.className] = this
                })
            }
        }
    }

    private fun createMineComponentPickerRow(items: List<MineComponentItem>): View {
        mineComponentPickerSummary = TextView(context).apply {
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showMineComponentPickerDialog(items) }
            addView(TextView(context).apply {
                text = context.getString(R.string.mine_component_picker_title)
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(mineComponentPickerSummary)
        }.also { mineComponentPickerRow = it }
    }

    private fun showMineComponentPickerDialog(items: List<MineComponentItem>) {
        val retained = BooleanArray(items.size) { index ->
            items[index].name !in ModuleSettings.getHiddenMineComponents(prefs)
        }
        AlertDialog.Builder(context)
            .setTitle(R.string.mine_component_title)
            .setMultiChoiceItems(items.map(MineComponentItem::name).toTypedArray(), retained) { _, which, isChecked ->
                retained[which] = isChecked
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_save) { _, _ ->
                val hidden = items.indices
                    .filterNot { retained[it] }
                    .mapTo(linkedSetOf()) { items[it].name }
                prefs.edit().putStringSet(ModuleSettings.KEY_HIDDEN_MINE_COMPONENTS, hidden).apply()
                refresh()
            }
            .show()
    }

    private fun createSwitchRow(
        title: String,
        summary: String,
        key: String,
        defaultValue: Boolean,
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
            addView(createTextColumn(title, summary), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(switchView)
        }
    }

    private fun handleSwitchChanged(key: String, isChecked: Boolean) {
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

    private fun isSkipVideoAdSwitchKey(key: String): Boolean =
        key == ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED ||
            key == ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED

    private fun shouldRefreshAfterSwitchChanged(key: String): Boolean =
        key == ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED ||
            key == ModuleSettings.KEY_DISABLE_LONG_PRESS_COPY_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_BOTTOM_BAR_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_THEME_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_SKIN_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED ||
            key == ModuleSettings.KEY_COMMENT_KEYWORD_FILTER_ENABLED ||
            key == ModuleSettings.KEY_COMMENT_MIN_LEVEL_ENABLED ||
            key == ModuleSettings.KEY_SKIP_VIDEO_AD_ENABLED ||
            key == ModuleSettings.KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED ||
            key == ModuleSettings.KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED

    private fun applyDesktopIconSetting(isChecked: Boolean) {
        DesktopIconHelper.applySetting(context, isChecked)
        if (!isChecked) {
            Toast.makeText(context, "恢复图标需重启手机后生效", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createTextColumn(title: String, summary: String): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(14), 0)
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTextColor(titleTextColor)
            })
            addView(TextView(context).apply {
                text = summary
                textSize = 12f
                setTextColor(summaryTextColor)
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun showSkipVideoAdAutoLikeDisableDialog() {
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.skip_video_ad_auto_like_disable_dialog_title))
            .setMessage(context.getString(R.string.skip_video_ad_auto_like_disable_dialog_message))
            .setNegativeButton(R.string.skip_video_ad_auto_like_disable_dialog_confirm, null)
            .setPositiveButton(R.string.skip_video_ad_auto_like_disable_dialog_cancel) { _, _ ->
                restoreSkipVideoAdAutoLikeEnabled()
            }
            .setOnCancelListener {
                restoreSkipVideoAdAutoLikeEnabled()
            }
            .show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(disableConfirmColor)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(cancelActionColor)
    }

    private fun restoreSkipVideoAdAutoLikeEnabled() {
        prefs.edit().putBoolean(ModuleSettings.KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, true).apply()
        ModuleSettings.refreshSkipVideoAdCache(prefs)
        if (::skipVideoAdAutoLikeSwitch.isInitialized) {
            refreshing = true
            skipVideoAdAutoLikeSwitch.isChecked = true
            refreshing = false
        }
    }

    private fun refresh() {
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
        if (::customThemeColorRow.isInitialized) {
            val enabled = ModuleSettings.isCustomThemeEnabled(prefs)
            customThemeColorRow.isEnabled = enabled
            customThemeColorRow.alpha = if (enabled) 1f else 0.45f
        }
        if (::customThemeColorSummary.isInitialized) {
            customThemeColorSummary.text = context.getString(
                R.string.custom_theme_color_summary,
                "#%06X".format(ModuleSettings.getCustomThemeColor(prefs) and 0xFFFFFF),
            )
        }
        if (::customThemeColorSwatch.isInitialized) {
            customThemeColorSwatch.setBackgroundColor(ModuleSettings.getCustomThemeColor(prefs))
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
        if (::customCdnHostSummary.isInitialized) {
            val host = ModuleSettings.getCustomCdnHost(prefs)
            customCdnHostSummary.text = if (host == null) {
                context.getString(R.string.custom_cdn_host_empty_summary)
            } else {
                context.getString(R.string.custom_cdn_host_current_summary, host)
            }
        }
        bottomBarItemCheckBoxes.forEach { (id, checkBox) ->
            checkBox.isEnabled = bottomBarEnabled
            checkBox.isChecked = id !in hiddenBottomBarItems
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

    private fun saveSelectedTags() {
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_TAGS, selectedTagKeys().toMutableSet())
            .apply()
    }

    private fun saveHiddenBottomBarItems() {
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_HIDDEN_BOTTOM_BAR_ITEMS, hiddenBottomBarItemIds().toMutableSet())
            .apply()
    }

    private fun saveHiddenHomeRecommendItems() {
        prefs.edit()
            .putStringSet(
                ModuleSettings.KEY_HIDDEN_HOME_RECOMMEND_ITEMS,
                hiddenHomeRecommendItemKeys().toMutableSet(),
            )
            .also { ModuleSettings.clearLegacyHomeRecommendFilterSwitches(it) }
            .apply()
    }

    private fun saveHiddenHomeRecommendTabs() {
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_HIDDEN_HOME_RECOMMEND_TABS, hiddenHomeRecommendTabKeys().toMutableSet())
            .apply()
    }

    private fun saveHiddenHomeComponents() {
        prefs.edit()
            .putStringSet(ModuleSettings.KEY_HIDDEN_HOME_COMPONENTS, hiddenHomeComponentClassNames().toMutableSet())
            .apply()
    }

    private fun selectedTagKeys(): Set<String> =
        tagCheckBoxes.filterValues { it.isChecked }.keys.toSet()

    private fun hiddenBottomBarItemIds(): Set<String> =
        bottomBarItemCheckBoxes.filterValues { !it.isChecked }.keys.toSet()

    private fun hiddenHomeRecommendItemKeys(): Set<String> =
        homeRecommendItemCheckBoxes.filterValues { it.isChecked }.keys.toSet()

    private fun hiddenHomeRecommendTabKeys(): Set<String> =
        homeRecommendTabCheckBoxes.filterValues { it.isChecked }.keys.toSet()

    private fun hiddenHomeComponentClassNames(): Set<String> =
        homeComponentCheckBoxes.filterValues { !it.isChecked }.keys.toSet()

    private fun storyVideoComponentAlphaSummary(percent: Int): String =
        context.getString(R.string.story_video_component_alpha_summary, percent.coerceIn(0, 100))

    private fun homeRecommendTitleKeywordSummary(): String {
        val keywords = ModuleSettings.parseHomeRecommendTitleKeywords(
            ModuleSettings.getHomeRecommendTitleKeywordsText(prefs),
        )
        if (keywords.isEmpty()) {
            return context.getString(R.string.home_recommend_title_keyword_empty_summary)
        }
        return context.getString(
            R.string.home_recommend_title_keyword_enabled_summary,
            keywords.size,
            keywords.take(TITLE_KEYWORD_SUMMARY_MAX_ITEMS).joinToString(context.getString(R.string.list_separator)),
        )
    }

    private fun showRuntimeEnvironmentDialog() {
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

    private fun bottomBarItems(): List<BottomBarItem> =
        ModuleSettings.refreshKnownBottomBarItemsCache(prefs)
            .mapNotNull(::parseBottomBarItem)
            .distinctBy(BottomBarItem::id)
            .sortedBy(BottomBarItem::order)

    private fun homeRecommendTabs(): List<HomeRecommendTabItem> =
        ModuleSettings.getKnownHomeRecommendTabs(prefs)
            .mapNotNull(::parseHomeRecommendTabItem)
            .distinctBy(HomeRecommendTabItem::key)
            .sortedBy(HomeRecommendTabItem::order)

    private fun homeRecommendItems(): List<HomeRecommendItem> {
        val items = mutableListOf<HomeRecommendItem>()
        if (hasHiddenFeatures()) {
            items += HomeRecommendItem(
                ModuleSettings.HOME_RECOMMEND_FILTER_AD,
                context.getString(R.string.home_recommend_filter_ad_title),
                context.getString(R.string.home_recommend_filter_ad_summary),
            )
        }
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_PICTURE,
            context.getString(R.string.home_recommend_filter_picture_title),
            context.getString(R.string.home_recommend_filter_picture_summary),
        )
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_GAME_PROMO,
            context.getString(R.string.home_recommend_filter_game_promo_title),
            context.getString(R.string.home_recommend_filter_game_promo_summary),
        )
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_LIVE,
            context.getString(R.string.home_recommend_filter_live_title),
            context.getString(R.string.home_recommend_filter_live_summary),
        )
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_KETANG,
            context.getString(R.string.home_recommend_filter_ketang_title),
            context.getString(R.string.home_recommend_filter_ketang_summary),
        )
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_VERTICAL_AV,
            context.getString(R.string.home_recommend_filter_vertical_av_title),
            context.getString(R.string.home_recommend_filter_vertical_av_summary),
        )
        items += HomeRecommendItem(
            ModuleSettings.HOME_RECOMMEND_FILTER_LARGE_COVER,
            context.getString(R.string.home_recommend_filter_large_cover_title),
            context.getString(R.string.home_recommend_filter_large_cover_summary),
        )
        return items
    }

    private fun homeComponentItems(): List<HomeComponentItem> =
        ModuleSettings.getKnownHomeComponents(prefs)
            .mapNotNull(::parseHomeComponentItem)
            .distinctBy(HomeComponentItem::className)
            .sortedWith(compareBy<HomeComponentItem> { it.order }.thenBy { it.name }.thenBy { it.className })

    private fun mineComponentItems(): List<MineComponentItem> =
        ModuleSettings.getKnownMineComponents(prefs)
            .filter { it.isNotBlank() }
            .map(::MineComponentItem)
            .distinctBy(MineComponentItem::name)
            .sortedBy(MineComponentItem::name)

    private fun parseBottomBarItem(raw: String): BottomBarItem? {
        val parts = raw.split('\t', limit = 4)
        if (parts.size == 4) {
            val order = parts[0].toIntOrNull() ?: return null
            if (parts[1].isBlank() || !isBottomBarName(parts[2])) return null
            return BottomBarItem(order, parts[1], parts[2], parts[3])
        }
        if (parts.size == 3) {
            if (parts[0].isBlank() || !isBottomBarName(parts[1])) return null
            return BottomBarItem(Int.MAX_VALUE, parts[0], parts[1], parts[2])
        }
        return null
    }

    private fun isBottomBarName(value: String): Boolean =
        value.isNotBlank() && !looksLikeBottomBarUri(value)

    private fun looksLikeBottomBarUri(value: String): Boolean =
        "://" in value ||
            value.startsWith("bilibili://", ignoreCase = true) ||
            value.startsWith("activity://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)

    private fun parseHomeRecommendTabItem(raw: String): HomeRecommendTabItem? {
        val parts = raw.split('\t', limit = 5)
        if (parts.size == 5) {
            val order = parts[0].toIntOrNull() ?: return null
            return HomeRecommendTabItem(order, parts[1], parts[2], parts[3], parts[4])
        }
        if (parts.size == 4) {
            val order = parts[0].toIntOrNull() ?: return null
            return HomeRecommendTabItem(order, parts[1], parts[2], parts[3], "")
        }
        return null
    }

    private fun parseHomeComponentItem(raw: String): HomeComponentItem? {
        val parts = raw.split('\t', limit = 3)
        if (parts.size != 3) return null
        val order = parts[0].toIntOrNull() ?: return null
        return HomeComponentItem(order, parts[1], parts[2])
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.open_url_failed_toast), Toast.LENGTH_SHORT).show()
            }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private data class BottomBarItem(
        val order: Int,
        val id: String,
        val name: String,
        val uri: String,
    )

    private data class HomeRecommendItem(
        val key: String,
        val title: String,
        val summary: String,
    )

    private data class HomeRecommendTabItem(
        val order: Int,
        val key: String,
        val name: String,
        val uri: String,
        val reporterId: String,
    ) {
        fun displayText(): String {
            val details = listOf(uri, reporterId)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
            return if (details.isBlank()) name else "$name\n$details"
        }
    }

    private data class HomeComponentItem(
        val order: Int,
        val name: String,
        val className: String,
    )

    private data class MineComponentItem(val name: String)

    private companion object {
        private const val PROJECT_REPOSITORY_URL = "https://github.com/HSSkyBoy/BBZQ"
        private const val TELEGRAM_CHANNEL_URL = "https://t.me/bbx_show"
        private const val VERSION_TAP_WINDOW_MS = 1500L
        private const val TITLE_KEYWORD_SUMMARY_MAX_ITEMS = 4
        private const val UNKNOWN_RUNTIME_VALUE = "unknown"
        private val SUPPORTED_HOST_PACKAGES = listOf(
            "tv.danmaku.bili",
            "com.bilibili.app.blue",
            "top.nkbe.npatch",
        )
        /** 检查更新弹窗「前往下载」的兜底地址，当 Release 未给出链接时使用。 */
        private const val RELEASE_PAGE_URL =
            "https://github.com/Xposed-Modules-Repo/io.github.bbzq/releases/latest"
    }
}
