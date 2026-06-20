package io.github.bbzq

import android.content.SharedPreferences

object ModuleSettings {
    const val PREFS_NAME = "bbzq_settings"
    const val KEY_MINI_PROGRAM_ENABLED = "mini_program"
    const val KEY_PURIFY_SHARE_ENABLED = "purify_share"
    const val KEY_SKIP_REWARD_AD_ENABLED = "skip_reward_ad"
    const val KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED = "block_teenagers_mode_dialog"
    const val KEY_SKIP_SPLASH_AD_ENABLED = "skip_splash_ad_enabled"
    const val KEY_SKIP_VIDEO_AD_ENABLED = "skip_video_ad_enabled"
    const val KEY_SKIP_VIDEO_AD_CATEGORIES = "skip_video_ad_categories"
    const val KEY_SKIP_VIDEO_AD_MODE_PREFIX = "skip_video_ad_mode_"
    const val KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE = "skip_video_ad_settings_visible"
    const val KEY_ACCESS_KEY_SETTINGS_VISIBLE = "access_key_settings_visible"
    const val KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED = "block_video_detail_banner_ad_enabled"
    const val KEY_UNLOCK_VIDEO_FEATURES_ENABLED = "unlock_video_features_enabled"
    const val KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED = "auto_like_video_detail_enabled"
    const val KEY_FIX_LIVE_QUALITY_URL_ENABLED = "fix_live_quality_url_enabled"
    const val KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED = "purify_home_recommend_ad_enabled"
    const val KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED = "purify_home_recommend_picture_enabled"
    const val KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED = "purify_home_recommend_game_promo_enabled"
    const val KEY_BLOCK_HOME_RECOMMEND_AUTO_REFRESH_ENABLED = "block_home_recommend_auto_refresh_enabled"
    const val KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED = "hide_all_home_components_enabled"
    const val KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED = "custom_home_component_hide_enabled"
    const val KEY_HIDDEN_HOME_COMPONENTS = "hidden_home_components"
    const val KEY_KNOWN_HOME_COMPONENTS = "known_home_components"
    const val KEY_PURIFY_STORY_VIDEO_AD_ENABLED = "purify_story_video_ad_enabled"
    const val KEY_PURIFY_STORY_VIDEO_AD_TAGS = "purify_story_video_ad_tags"
    const val KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT = "purify_story_video_ad_blocked_count"
    const val KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED = "custom_download_thread_enabled"
    const val KEY_CUSTOM_DOWNLOAD_CONCURRENCY = "custom_download_concurrency"
    const val KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED = "skip_mini_game_reward_ad_enabled"
    const val KEY_BLOCK_LIVE_RESERVATION_ENABLED = "block_live_reservation_enabled"
    const val KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED = "block_live_room_qoe_popup_enabled"
    const val KEY_DISABLE_LONG_PRESS_COPY_ENABLED = "disable_long_press_copy_enabled"
    const val KEY_ENHANCE_LONG_PRESS_COPY_ENABLED = "enhance_long_press_copy_enabled"
    const val KEY_CUSTOM_BOTTOM_BAR_ENABLED = "custom_bottom_bar_enabled"
    const val KEY_HIDDEN_BOTTOM_BAR_ITEMS = "hidden_bottom_bar_items"
    const val KEY_KNOWN_BOTTOM_BAR_ITEMS = "known_bottom_bar_items"
    const val KEY_FULL_NUMBER_FORMAT_ENABLED = "full_number_format_enabled"
    const val KEY_UNLOCK_COMMENT_GIF_ENABLED = "unlock_comment_gif_enabled"
    const val KEY_LAST_ACCESS_KEY = "last_access_key"
    const val KEY_HIDE_DESKTOP_ICON = "hide_desktop_icon"

    const val KEY_TARGET_APP_VERSION = "target_app_version"
    const val CACHE_BILI_SETTINGS_ACTIVITY = "cache_settings_activity"
    const val KEY_RUNTIME_HOST_PACKAGE = "runtime_host_package"
    const val KEY_RUNTIME_HOST_VERSION_NAME = "runtime_host_version_name"
    const val KEY_RUNTIME_HOST_VERSION_CODE = "runtime_host_version_code"
    const val KEY_RUNTIME_HOST_SOURCE_KIND = "runtime_host_source_kind"
    const val KEY_RUNTIME_XPOSED_API_VERSION = "runtime_xposed_api_version"
    const val KEY_RUNTIME_XPOSED_FRAMEWORK_NAME = "runtime_xposed_framework_name"
    const val KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION = "runtime_xposed_framework_version"
    const val KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE = "runtime_xposed_framework_version_code"
    const val KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES = "runtime_xposed_framework_properties"
    const val KEY_RUNTIME_KIND = "runtime_kind"
    const val KEY_RUNTIME_PATCH_MODE = "runtime_patch_mode"
    const val KEY_RUNTIME_PROCESS_NAME = "runtime_process_name"
    const val KEY_RUNTIME_LAST_UPDATE_TIME = "runtime_last_update_time"

    val defaultStoryVideoAdTags = setOf("ad")
    val defaultSkipVideoAdModes = mapOf(
        "sponsor" to SkipVideoAdMode.AUTO_SKIP,
        "selfpromo" to SkipVideoAdMode.MANUAL_SKIP,
        "interaction" to SkipVideoAdMode.MANUAL_SKIP,
        "intro" to SkipVideoAdMode.MANUAL_SKIP,
        "outro" to SkipVideoAdMode.MANUAL_SKIP,
        "preview" to SkipVideoAdMode.MANUAL_SKIP,
        "music_offtopic" to SkipVideoAdMode.SHOW_IN_BAR,
        "poi_highlight" to SkipVideoAdMode.MANUAL_SKIP,
        "filler" to SkipVideoAdMode.SHOW_IN_BAR,
        "exclusive_access" to SkipVideoAdMode.SHOW_IN_BAR,
    )

    val storyVideoAdTags = listOf(
        StoryVideoAdTag("ad", "广告", null),
        StoryVideoAdTag("short", "短剧", "短剧"),
        StoryVideoAdTag("shopping", "购物", "购物"),
        StoryVideoAdTag("tv", "电视剧", "电视剧"),
        StoryVideoAdTag("doc", "纪录片", "纪录片"),
        StoryVideoAdTag("ent", "娱乐", "娱乐"),
        StoryVideoAdTag("movie", "电影", "电影"),
        StoryVideoAdTag("music", "音乐", "音乐"),
        StoryVideoAdTag("topic", "话题", "话题"),
    )

    val skipVideoAdCategories = listOf(
        SponsorBlockCategory("sponsor", "赞助 / 恰饭", "付费推广、推荐和直接广告。", 0xFF00D400.toInt(), 0xFF007800.toInt()),
        SponsorBlockCategory("selfpromo", "无偿 / 自我推广", "UP 主引流、关注提醒、推广其他内容。", 0xFFFFFF00.toInt(), 0xFFBFBF35.toInt()),
        SponsorBlockCategory("interaction", "三连 / 互动提醒", "点赞、投币、评论等互动号召。", 0xFFCC00FF.toInt(), 0xFF6C0087.toInt()),
        SponsorBlockCategory("intro", "片头", "与正文关系不大的固定开场。", 0xFF00FFFF.toInt(), 0xFF008080.toInt()),
        SponsorBlockCategory("outro", "片尾", "结束卡、鸣谢和结尾引导。", 0xFF0202ED.toInt(), 0xFF000070.toInt()),
        SponsorBlockCategory("preview", "预览 / 回顾", "下集预告、前情提要和重复回顾。", 0xFF008FD6.toInt(), 0xFF005799.toInt()),
        SponsorBlockCategory("music_offtopic", "离题音乐", "与内容无关的纯音乐或演奏片段。", 0xFFFF9900.toInt(), 0xFFA6634A.toInt()),
        SponsorBlockCategory("poi_highlight", "精彩片段 / 高光", "值得直接空降或重点标记的内容。", 0xFFFF1684.toInt(), 0xFF9B044C.toInt()),
        SponsorBlockCategory("filler", "填充内容", "与主线关系较弱的灌水片段。", 0xFF7300FF.toInt(), 0xFF2E0066.toInt()),
        SponsorBlockCategory("exclusive_access", "独家访问 / 抢先体验", "用于整段视频标签，例如仅限会员或抢先看的内容。", 0xFF008A5C.toInt(), 0xFF00543A.toInt()),
    )

    fun isSkipSplashAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_SPLASH_AD_ENABLED, true)

    fun isBlockTeenagersModeDialogEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED, false)

    fun isUnlockVideoFeaturesEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_ENABLED, true)

    fun isSkipVideoAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_VIDEO_AD_ENABLED, false)

    fun getSkipVideoAdMode(prefs: SharedPreferences, category: String): SkipVideoAdMode =
        if (prefs.contains("$KEY_SKIP_VIDEO_AD_MODE_PREFIX$category")) {
                SkipVideoAdMode.fromValue(
                    prefs.getInt(
                        "$KEY_SKIP_VIDEO_AD_MODE_PREFIX$category",
                        defaultSkipVideoAdModes[category]?.value ?: SkipVideoAdMode.IGNORE.value,
                    ),
                )
        } else {
            val legacyCategories = prefs.getStringSet(KEY_SKIP_VIDEO_AD_CATEGORIES, null)
            if (legacyCategories != null) {
                if (category in legacyCategories) SkipVideoAdMode.AUTO_SKIP else SkipVideoAdMode.IGNORE
            } else {
                defaultSkipVideoAdModes[category] ?: SkipVideoAdMode.IGNORE
            }
        }

    fun getSkipVideoAdCategories(prefs: SharedPreferences): Set<String> =
        skipVideoAdCategories
            .asSequence()
            .filter { getSkipVideoAdMode(prefs, it.key) != SkipVideoAdMode.IGNORE }
            .map { it.key }
            .toSet()

    fun isSkipVideoAdSettingsVisible(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE, false)

    fun isAccessKeySettingsVisible(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ACCESS_KEY_SETTINGS_VISIBLE, false)

    fun isBlockVideoDetailBannerAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED, false)

    fun isAutoLikeVideoDetailEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED, false)

    fun isFixLiveQualityUrlEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FIX_LIVE_QUALITY_URL_ENABLED, false)

    fun isPurifyHomeRecommendAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED, false)

    fun isPurifyHomeRecommendPictureEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED, false)

    fun isPurifyHomeRecommendGamePromoEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED, false)

    fun isBlockHomeRecommendAutoRefreshEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_HOME_RECOMMEND_AUTO_REFRESH_ENABLED, false)

    fun isHideAllHomeComponentsEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED, false)

    fun isCustomHomeComponentHideEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED, false)

    fun getHiddenHomeComponents(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_HOME_COMPONENTS, emptySet()) ?: emptySet()

    fun getKnownHomeComponents(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_KNOWN_HOME_COMPONENTS, emptySet()) ?: emptySet()

    fun isPurifyStoryVideoAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_STORY_VIDEO_AD_ENABLED, false)

    fun getPurifyStoryVideoAdTags(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_PURIFY_STORY_VIDEO_AD_TAGS, defaultStoryVideoAdTags)
            ?: defaultStoryVideoAdTags

    fun isCustomDownloadThreadEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED, false)

    fun getCustomDownloadConcurrency(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_CUSTOM_DOWNLOAD_CONCURRENCY, 1).coerceIn(1, 12)

    fun isSkipMiniGameRewardAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED, true)

    fun isSkipRewardAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_REWARD_AD_ENABLED, false)

    fun isBlockLiveReservationEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_LIVE_RESERVATION_ENABLED, false)

    fun isBlockLiveRoomQoePopupEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED, false)

    fun isDisableLongPressCopyEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_DISABLE_LONG_PRESS_COPY_ENABLED, false)

    fun isEnhanceLongPressCopyEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, false)

    fun isPurifyShareEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_SHARE_ENABLED, false)

    fun isCustomBottomBarEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_BOTTOM_BAR_ENABLED, false)

    fun getHiddenBottomBarItems(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_BOTTOM_BAR_ITEMS, emptySet()) ?: emptySet()

    fun getKnownBottomBarItems(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_KNOWN_BOTTOM_BAR_ITEMS, emptySet()) ?: emptySet()

    fun isFullNumberFormatEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FULL_NUMBER_FORMAT_ENABLED, false)

    fun isUnlockCommentGifEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_UNLOCK_COMMENT_GIF_ENABLED, false)

    fun isHideDesktopIconEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_DESKTOP_ICON, false)
}

enum class SkipVideoAdMode(val value: Int, val label: String) {
    AUTO_SKIP(0, "自动跳过"),
    MANUAL_SKIP(1, "手动跳过"),
    SHOW_IN_BAR(2, "显示标记"),
    IGNORE(3, "不处理");

    companion object {
        fun fromValue(value: Int): SkipVideoAdMode =
            entries.firstOrNull { it.value == value } ?: AUTO_SKIP
    }
}

data class StoryVideoAdTag(
    val key: String,
    val label: String,
    val cartText: String?,
)

data class SponsorBlockCategory(
    val key: String,
    val label: String,
    val summary: String,
    val color: Int,
    val previewColor: Int,
)
