package io.github.bbzq.ui.settings

import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.TextView
import io.github.bbzq.ModuleSettings
import io.github.bbzq.R
import io.github.bbzq.SettingsActivity
import io.github.bbzq.SkipVideoAdMode
import io.github.bbzq.SponsorBlockCategory
import io.github.bbzq.ui.CdnPriorityListDialog
import io.github.bbzq.ui.CdnSpeedTestDialog
import io.github.bbzq.ui.settings.SettingsContentFactory.Companion.TITLE_KEYWORD_SUMMARY_MAX_ITEMS

internal fun SettingsContentFactory.playbackPromoRows(): List<View> {
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
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_block_chronos_promotion_title),
        context.getString(R.string.playback_block_chronos_promotion_summary),
        ModuleSettings.KEY_BLOCK_CHRONOS_PROMOTION_ENABLED,
        false,
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_block_activity_meta_sticker_title),
        context.getString(R.string.playback_block_activity_meta_sticker_summary),
        ModuleSettings.KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED,
        false,
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_block_pgc_activity_popup_title),
        context.getString(R.string.playback_block_pgc_activity_popup_summary),
        ModuleSettings.KEY_BLOCK_PGC_ACTIVITY_POPUP_ENABLED,
        false,
        badges = unfinishedBadges() + restartBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_hide_video_mention_title),
        context.getString(R.string.playback_hide_video_mention_summary),
        ModuleSettings.KEY_PURIFY_VIDEO_MENTION_ENABLED,
        false,
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_block_ai_declared_video_title),
        context.getString(R.string.playback_block_ai_declared_video_summary),
        ModuleSettings.KEY_BLOCK_AI_DECLARED_VIDEO_ENABLED,
        false,
        badges = unfinishedBadges() + restartBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_skip_reward_title),
        context.getString(R.string.playback_skip_reward_summary),
        ModuleSettings.KEY_SKIP_REWARD_AD_ENABLED,
        false,
        badges = unfinishedBadges(),
    )
    rows += createSwitchRow(
        context.getString(R.string.skip_mini_game_reward_ad_title),
        context.getString(R.string.skip_mini_game_reward_ad_summary),
        ModuleSettings.KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED,
        false,
        badges = unfinishedBadges(),
    )
    return rows
}

internal fun SettingsContentFactory.playbackPlayerRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.playback_transparent_status_bar_title),
        context.getString(R.string.playback_transparent_status_bar_summary),
        ModuleSettings.KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.playback_portrait_control_title),
        context.getString(R.string.playback_portrait_control_summary),
        ModuleSettings.KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED,
        false,
        badges = restartBadges(),
    ),
    createSwitchRow(
        context.getString(R.string.split_screen_fullscreen_title),
        context.getString(R.string.split_screen_fullscreen_summary),
        ModuleSettings.KEY_SPLIT_SCREEN_FULLSCREEN_ENABLED,
        false,
        badges = restartBadges(),
    ),
    createSwitchRow(
        context.getString(R.string.disable_background_play_title),
        context.getString(R.string.disable_background_play_summary),
        ModuleSettings.KEY_DISABLE_BACKGROUND_PLAY_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.allow_mini_play_title),
        context.getString(R.string.allow_mini_play_summary),
        ModuleSettings.KEY_ALLOW_MINI_PLAY_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.play_arc_conf_title),
        context.getString(R.string.play_arc_conf_summary),
        ModuleSettings.KEY_PLAY_ARC_CONF_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.playback_fix_media_session_card_title),
        context.getString(R.string.playback_fix_media_session_card_summary),
        ModuleSettings.KEY_FIX_MEDIA_SESSION_CARD,
        false,
    ),
)

internal fun SettingsContentFactory.playbackSpeedRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.playback_triple_speed_title),
        context.getString(R.string.playback_triple_speed_summary),
        ModuleSettings.KEY_PLAYER_TRIPLE_SPEED_ENABLED,
        false,
        badges = restartBadges(),
    ),
    createSwitchRow(
        context.getString(R.string.playback_long_press_speed_lock_title),
        context.getString(R.string.playback_long_press_speed_lock_summary),
        ModuleSettings.KEY_PLAYER_LONG_PRESS_SPEED_LOCK_ENABLED,
        false,
        badges = restartBadges(),
    ),
    createSwitchRow(
        context.getString(R.string.playback_custom_long_press_speed_title),
        context.getString(R.string.playback_custom_long_press_speed_summary),
        ModuleSettings.KEY_PLAYER_CUSTOM_LONG_PRESS_SPEED_ENABLED,
        false,
        badges = restartBadges(),
    ) {
        customLongPressSpeedSwitch = it
    },
    createCustomLongPressSpeedRow(),
)

internal fun SettingsContentFactory.playbackRecommendRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.video_detail_relate_custom_filter_title),
        context.getString(R.string.video_detail_relate_custom_filter_summary),
        ModuleSettings.KEY_CUSTOM_VIDEO_DETAIL_RELATE_FILTER_ENABLED,
        false,
    ) {
        videoDetailRelateFilterSwitch = it
    }
    rows += createVideoDetailRelateTitleKeywordRow()
    val relateTypes = videoDetailRelateTypes()
    if (relateTypes.isEmpty()) {
        rows += createInfoRow(
            context.getString(R.string.video_detail_relate_custom_filter_item_title),
            context.getString(R.string.video_detail_relate_unavailable_summary),
        )
    } else {
        rows += createInfoRow(
            context.getString(R.string.video_detail_relate_custom_filter_item_title),
            context.getString(R.string.video_detail_relate_custom_filter_info_summary),
        )
        rows += createVideoDetailRelateTypeGroup(relateTypes)
    }
    rows += createSwitchRow(
        context.getString(R.string.playback_disable_half_end_page_title),
        context.getString(R.string.playback_disable_half_end_page_summary),
        ModuleSettings.KEY_DISABLE_HALF_END_PAGE,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.playback_disable_all_end_page_title),
        context.getString(R.string.playback_disable_all_end_page_summary),
        ModuleSettings.KEY_DISABLE_ALL_END_PAGE,
        false,
    )
    return rows
}

internal fun SettingsContentFactory.playbackInteractRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.playback_auto_like_title),
        context.getString(R.string.playback_auto_like_summary),
        ModuleSettings.KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.playback_share_redirect_to_overflow_title),
        context.getString(R.string.playback_share_redirect_to_overflow_summary),
        ModuleSettings.KEY_SHARE_REDIRECT_TO_OVERFLOW_ENABLED,
        false,
    ),
)

internal fun SettingsContentFactory.liveRows(): List<View> = listOf(
    createSwitchRow(
        context.getString(R.string.block_live_reservation_title),
        context.getString(R.string.block_live_reservation_summary),
        ModuleSettings.KEY_BLOCK_LIVE_RESERVATION_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.block_live_room_qoe_popup_title),
        context.getString(R.string.block_live_room_qoe_popup_summary),
        ModuleSettings.KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.remove_live_room_blur_mask_title),
        context.getString(R.string.remove_live_room_blur_mask_summary),
        ModuleSettings.KEY_REMOVE_LIVE_ROOM_BLUR_MASK_ENABLED,
        false,
    ),
    createSwitchRow(
        context.getString(R.string.disable_live_room_vertical_swipe_title),
        context.getString(R.string.disable_live_room_vertical_swipe_summary),
        ModuleSettings.KEY_DISABLE_LIVE_ROOM_VERTICAL_SWIPE_ENABLED,
        false,
    ),
)

internal fun SettingsContentFactory.customCdnRows(): List<View> = listOf(
    createCustomCdnWifiRow(),
    createCustomCdnCellularRow(),
    createSwitchRow(
        context.getString(R.string.cdn_audio_independent_title),
        context.getString(R.string.cdn_audio_independent_summary),
        ModuleSettings.KEY_CDN_AUDIO_INDEPENDENT,
        false,
    ),
    createCustomCdnSpeedTestRow(),
    createSwitchRow(
        context.getString(R.string.block_p2p_title),
        context.getString(R.string.block_p2p_summary),
        ModuleSettings.KEY_BLOCK_P2P_ENABLED,
        false,
    ),
)

internal fun SettingsContentFactory.commentRows(): List<View> {
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
        createSwitchRow(
            context.getString(R.string.comment_no_hotspot_title),
            context.getString(R.string.comment_no_hotspot_summary),
            ModuleSettings.KEY_COMMENT_NO_HOTSPOT,
            false,
        ),
    )
}

internal fun SettingsContentFactory.skipVideoAdOverviewRows(): List<View> {
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

internal fun SettingsContentFactory.skipVideoAdCategoryRows(): List<View> {
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

internal fun SettingsContentFactory.storyRows(): List<View> {
    val rows = mutableListOf<View>()
    rows += createSwitchRow(
        context.getString(R.string.story_video_default_launch_title),
        context.getString(R.string.story_video_default_launch_summary),
        ModuleSettings.KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED,
        false,
    )
    rows += createSwitchRow(
        context.getString(R.string.story_video_as_detail_title),
        context.getString(R.string.story_video_as_detail_summary),
        ModuleSettings.KEY_STORY_VIDEO_AS_DETAIL_ENABLED,
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
    rows += createSwitchRow(
        context.getString(R.string.purify_story_video_ad_title),
        context.getString(R.string.purify_story_video_ad_summary),
        ModuleSettings.KEY_PURIFY_STORY_VIDEO_AD_ENABLED,
        false,
        badges = unfinishedBadges(),
    ) {
        storyVideoAdSwitch = it
    }
    if (hasHiddenFeatures()) {
        rows += createInfoRow(
            context.getString(R.string.story_filter_selected_tags_title),
            context.getString(R.string.story_filter_selected_tags_summary),
        )
        rows += createTagGroup()
        rows += createBlockedCountRow()
    }
    return rows
}

private fun SettingsContentFactory.createVideoDetailRelateTitleKeywordRow(): View {
    videoDetailRelateTitleKeywordSummaryView = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showVideoDetailRelateTitleKeywordDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.video_detail_relate_title_keyword_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(videoDetailRelateTitleKeywordSummaryView)
    }.also {
        videoDetailRelateTitleKeywordRow = it
        registerSearchTarget(
            it,
            context.getString(R.string.video_detail_relate_title_keyword_title),
            context.getString(R.string.video_detail_relate_title_keyword_title),
            "action:${context.getString(R.string.video_detail_relate_title_keyword_title)}",
        )
    }
}

private fun SettingsContentFactory.showVideoDetailRelateTitleKeywordDialog() {
    val input = EditText(context).apply {
        setText(ModuleSettings.getVideoDetailRelateTitleKeywordsText(prefs))
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
        .setTitle(R.string.video_detail_relate_title_keyword_dialog_title)
        .setView(content)
        .setNegativeButton(R.string.dialog_cancel, null)
        .setPositiveButton(R.string.dialog_save) { _, _ ->
            prefs.edit()
                .putString(ModuleSettings.KEY_VIDEO_DETAIL_RELATE_TITLE_KEYWORDS, input.text?.toString()?.trim().orEmpty())
                .apply()
            refresh()
        }
        .show()
}

private fun SettingsContentFactory.createCommentKeywordRow(): View {
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
        registerSearchTarget(
            it,
            context.getString(R.string.comment_keyword_row_title),
            context.getString(R.string.comment_keyword_row_title),
            "action:${context.getString(R.string.comment_keyword_row_title)}",
        )
    }
}

private fun SettingsContentFactory.showCommentKeywordDialog() {
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

private fun SettingsContentFactory.createCommentMinLevelRow(): View {
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

private fun SettingsContentFactory.showCommentMinLevelDialog() {
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

internal fun SettingsContentFactory.commentKeywordSummaryText(): String {
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

internal fun SettingsContentFactory.commentMinLevelSummaryText(): String =
    context.getString(R.string.comment_min_level_row_summary, ModuleSettings.getCommentMinLevel(prefs))

internal fun SettingsContentFactory.createHalfScreenQualityRow(): View {
    halfScreenQualitySummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showHalfScreenQualityDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.half_screen_quality_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(halfScreenQualitySummary)
        addView(TextView(context).apply {
            text = context.getString(R.string.quality_first_use_hint)
            textSize = 11f
            setTextColor(summaryTextColor)
            alpha = 0.7f
            setPadding(0, dp(3), 0, 0)
        })
        registerSearchTarget(
            this,
            context.getString(R.string.half_screen_quality_title),
            context.getString(R.string.half_screen_quality_summary, ""),
            ModuleSettings.KEY_HALF_SCREEN_QUALITY,
        )
    }
}

private fun SettingsContentFactory.showHalfScreenQualityDialog() {
    val options = ModuleSettings.halfScreenQualityOptions
    val current = ModuleSettings.getHalfScreenQuality(prefs)
    val selected = options.indexOfFirst { it.qn == current }.coerceAtLeast(0)
    val labels = options.map { it.label }.toTypedArray()
    AlertDialog.Builder(context)
        .setTitle(R.string.half_screen_quality_dialog_title)
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            dialog.dismiss()
            prefs.edit().putInt(ModuleSettings.KEY_HALF_SCREEN_QUALITY, options[which].qn).apply()
            refresh()
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .show()
}

internal fun SettingsContentFactory.createFullScreenQualityRow(): View {
    fullScreenQualitySummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { showFullScreenQualityDialog() }
        addView(TextView(context).apply {
            text = context.getString(R.string.full_screen_quality_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(fullScreenQualitySummary)
        addView(TextView(context).apply {
            text = context.getString(R.string.quality_first_use_hint)
            textSize = 11f
            setTextColor(summaryTextColor)
            alpha = 0.7f
            setPadding(0, dp(3), 0, 0)
        })
        registerSearchTarget(
            this,
            context.getString(R.string.full_screen_quality_title),
            context.getString(R.string.full_screen_quality_summary, ""),
            ModuleSettings.KEY_FULL_SCREEN_QUALITY,
        )
    }
}

private fun SettingsContentFactory.showFullScreenQualityDialog() {
    val options = ModuleSettings.fullScreenQualityOptions
    val current = ModuleSettings.getFullScreenQuality(prefs)
    val selected = options.indexOfFirst { it.qn == current }.coerceAtLeast(0)
    val labels = options.map { it.label }.toTypedArray()
    AlertDialog.Builder(context)
        .setTitle(R.string.full_screen_quality_dialog_title)
        .setSingleChoiceItems(labels, selected) { dialog, which ->
            dialog.dismiss()
            prefs.edit().putInt(ModuleSettings.KEY_FULL_SCREEN_QUALITY, options[which].qn).apply()
            refresh()
        }
        .setNegativeButton(R.string.dialog_cancel, null)
        .show()
}

private fun SettingsContentFactory.createCustomCdnWifiRow(): View {
    customCdnWifiSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            io.github.bbzq.ui.CdnPriorityListDialog(context, prefs, isCellular = false) {
                refresh()
            }.show()
        }
        addView(TextView(context).apply {
            text = context.getString(R.string.cdn_wifi_priority_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(customCdnWifiSummary)
        registerSearchTarget(
            this,
            context.getString(R.string.cdn_wifi_priority_title),
            context.getString(R.string.cdn_wifi_priority_summary_empty),
            "action:${context.getString(R.string.cdn_wifi_priority_title)}",
        )
    }
}

private fun SettingsContentFactory.createCustomCdnCellularRow(): View {
    customCdnCellularSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
        setPadding(0, dp(4), 0, 0)
    }
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            io.github.bbzq.ui.CdnPriorityListDialog(context, prefs, isCellular = true) {
                refresh()
            }.show()
        }
        addView(TextView(context).apply {
            text = context.getString(R.string.cdn_cellular_priority_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(customCdnCellularSummary)
        registerSearchTarget(
            this,
            context.getString(R.string.cdn_cellular_priority_title),
            context.getString(R.string.cdn_wifi_priority_summary_empty),
            "action:${context.getString(R.string.cdn_cellular_priority_title)}",
        )
    }
}

private fun SettingsContentFactory.createCustomCdnSpeedTestRow(): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener {
            io.github.bbzq.ui.CdnSpeedTestDialog(context, prefs) {
                refresh()
            }.show()
        }
        addView(TextView(context).apply {
            text = context.getString(R.string.custom_cdn_speed_test_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(TextView(context).apply {
            text = context.getString(R.string.custom_cdn_speed_test_summary)
            textSize = 12f
            setTextColor(summaryTextColor)
            setPadding(0, dp(4), 0, 0)
        })
        registerSearchTarget(
            this,
            context.getString(R.string.custom_cdn_speed_test_title),
            context.getString(R.string.custom_cdn_speed_test_summary),
            "action:${context.getString(R.string.custom_cdn_speed_test_title)}",
        )
    }
}

private fun SettingsContentFactory.createBlockedCountRow(): View {
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

private fun SettingsContentFactory.createStoryVideoComponentAlphaRow(): View {
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
        registerSearchTarget(
            this,
            context.getString(R.string.story_video_component_alpha_title),
            context.getString(R.string.story_video_component_alpha_title),
            ModuleSettings.KEY_STORY_VIDEO_COMPONENT_ALPHA,
        )
    }
}

private fun SettingsContentFactory.createCustomLongPressSpeedRow(): View {
    customLongPressSpeedSummary = TextView(context).apply {
        textSize = 12f
        setTextColor(summaryTextColor)
    }
    customLongPressSpeedSeekBar = SeekBar(context).apply {
        max = ModuleSettings.MAX_CUSTOM_LONG_PRESS_SPEED_TENTHS - ModuleSettings.MIN_CUSTOM_LONG_PRESS_SPEED_TENTHS
        progress = ModuleSettings.getPlayerCustomLongPressSpeedTenths(prefs) - ModuleSettings.MIN_CUSTOM_LONG_PRESS_SPEED_TENTHS
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val tenths = progress + ModuleSettings.MIN_CUSTOM_LONG_PRESS_SPEED_TENTHS
                if (!refreshing) {
                    customLongPressSpeedSummary.text = customLongPressSpeedSummary(tenths)
                }
                if (fromUser) {
                    prefs.edit()
                        .putInt(ModuleSettings.KEY_PLAYER_CUSTOM_LONG_PRESS_SPEED_VALUE, tenths)
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
            text = context.getString(R.string.playback_custom_long_press_speed_value_title)
            textSize = 15f
            setTextColor(titleTextColor)
        })
        addView(customLongPressSpeedSummary.apply {
            setPadding(0, dp(4), 0, dp(8))
        })
        addView(customLongPressSpeedSeekBar)
        registerSearchTarget(
            this,
            context.getString(R.string.playback_custom_long_press_speed_value_title),
            context.getString(R.string.playback_custom_long_press_speed_value_title),
            ModuleSettings.KEY_PLAYER_CUSTOM_LONG_PRESS_SPEED_VALUE,
        )
    }.also {
        customLongPressSpeedRow = it
    }
}

internal fun SettingsContentFactory.customLongPressSpeedSummary(tenths: Int): String =
    context.getString(R.string.playback_custom_long_press_speed_value_summary, tenths / 10f)

private fun SettingsContentFactory.createSponsorBlockCategoryGroup(): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        ModuleSettings.skipVideoAdCategories.forEach { category ->
            addView(createSkipModeRow(category))
        }
    }
}

private fun SettingsContentFactory.createSkipModeRow(category: SponsorBlockCategory): View {
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
        registerSearchTarget(
            this,
            category.label,
            category.summary,
            "skip_cat:${category.key}",
        )
    }
}

private fun SettingsContentFactory.showSkipModeDialog(category: SponsorBlockCategory) {
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

private fun SettingsContentFactory.createCategoryColorLegend(category: SponsorBlockCategory): View {
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

private fun SettingsContentFactory.createVideoDetailRelateTypeGroup(types: List<String>): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        types.forEach { type ->
            addView(CheckBox(context).apply {
                text = ModuleSettings.getRelateTypeDisplayName(type, context)
                textSize = 14f
                setTextColor(titleTextColor)
                setPadding(dp(6), dp(2), dp(6), dp(2))
                setOnCheckedChangeListener { _, _ ->
                    if (!refreshing) saveHiddenVideoDetailRelateTypes()
                }
                videoDetailRelateTypeCheckBoxes[type] = this
            })
        }
    }
}

internal fun SettingsContentFactory.showSkipVideoAdAutoLikeDisableDialog() {
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

private fun SettingsContentFactory.saveHiddenVideoDetailRelateTypes() {
    prefs.edit()
        .putStringSet(
            ModuleSettings.KEY_HIDDEN_VIDEO_DETAIL_RELATE_TYPES,
            hiddenVideoDetailRelateTypeKeys().toMutableSet(),
        )
        .apply()
}

private fun SettingsContentFactory.hiddenVideoDetailRelateTypeKeys(): Set<String> =
    videoDetailRelateTypeCheckBoxes.filterValues { it.isChecked }.keys.toSet()

internal fun SettingsContentFactory.storyVideoComponentAlphaSummary(percent: Int): String =
    context.getString(R.string.story_video_component_alpha_summary, percent.coerceIn(0, 100))

internal fun SettingsContentFactory.videoDetailRelateTitleKeywordSummary(): String {
    val keywords = ModuleSettings.parseVideoDetailRelateTitleKeywords(
        ModuleSettings.getVideoDetailRelateTitleKeywordsText(prefs),
    )
    if (keywords.isEmpty()) {
        return context.getString(R.string.video_detail_relate_title_keyword_empty_summary)
    }
    return context.getString(
        R.string.video_detail_relate_title_keyword_enabled_summary,
        keywords.size,
        keywords.take(TITLE_KEYWORD_SUMMARY_MAX_ITEMS).joinToString(context.getString(R.string.list_separator)),
    )
}

private fun SettingsContentFactory.videoDetailRelateTypes(): List<String> =
    ModuleSettings.getKnownVideoDetailRelateTypes(prefs)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
