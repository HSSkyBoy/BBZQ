package io.github.bbzq

import android.content.Context
import android.content.SharedPreferences

object ModuleSettings {
    const val PREFS_NAME = "bbzq_settings"
    const val KEY_MINI_PROGRAM_ENABLED = "mini_program"
    const val KEY_PURIFY_SHARE_ENABLED = "purify_share"
    const val KEY_SKIP_REWARD_AD_ENABLED = "skip_reward_ad"
    const val KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED = "block_teenagers_mode_dialog"
    const val KEY_BLOCK_UPDATE_ENABLED = "block_update_enabled"
    const val KEY_SKIP_SPLASH_AD_ENABLED = "skip_splash_ad_enabled"
    const val KEY_SKIP_VIDEO_AD_ENABLED = "skip_video_ad_enabled"
    const val KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED = "skip_video_ad_auto_like_enabled"
    const val KEY_SKIP_VIDEO_AD_CATEGORIES = "skip_video_ad_categories"
    const val KEY_SKIP_VIDEO_AD_MODE_PREFIX = "skip_video_ad_mode_"
    const val KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE = "skip_video_ad_settings_visible"
    const val KEY_ACCESS_KEY_SETTINGS_VISIBLE = "access_key_settings_visible"
    const val KEY_TRY_FREE_QUALITY_SETTINGS_VISIBLE = "try_free_quality_settings_visible"
    const val KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED = "block_video_detail_banner_ad_enabled"
    const val KEY_BLOCK_CHRONOS_PROMOTION_ENABLED = "block_chronos_promotion_enabled"
    const val KEY_UNLOCK_VIDEO_FEATURES_ENABLED = "unlock_video_features_enabled"
    const val KEY_UNLOCK_VIDEO_FEATURES_UI_ENABLED = "unlock_video_features_ui_enabled"
    const val KEY_UNLOCK_HIGHEST_BITRATE_ENABLED = "unlock_highest_bitrate_enabled"
    const val KEY_AVOID_HDR_DOLBY_ENABLED = "avoid_hdr_dolby_enabled"
    const val KEY_HALF_SCREEN_QUALITY = "half_screen_quality"
    const val KEY_FULL_SCREEN_QUALITY = "full_screen_quality"
    const val KEY_VIDEO_DOWNLOAD_ENABLED = "video_download_enabled"
    const val KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED = "auto_like_video_detail_enabled"
    const val KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED = "player_transparent_status_bar_enabled"
    const val KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED = "hide_player_portrait_control_enabled"
    const val KEY_PLAYER_TRIPLE_SPEED_ENABLED = "player_triple_speed_enabled"
    const val KEY_PLAYER_LONG_PRESS_SPEED_LOCK_ENABLED = "player_long_press_speed_lock_enabled"
    const val KEY_FIX_LIVE_QUALITY_URL_ENABLED = "fix_live_quality_url_enabled"
    const val KEY_CUSTOM_CDN_ENABLED = "custom_cdn_enabled"
    const val KEY_CUSTOM_CDN_HOST = "custom_cdn_host"
    const val KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED = "purify_home_recommend_ad_enabled"
    const val KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED = "purify_home_recommend_picture_enabled"
    const val KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED = "purify_home_recommend_game_promo_enabled"
    const val KEY_HOME_RECOMMEND_TITLE_KEYWORDS = "home_recommend_title_keywords"
    const val KEY_HOME_RECOMMEND_VERTICAL_AV_DETAIL_ENABLED = "home_recommend_vertical_av_detail_enabled"
    const val KEY_HOME_RECOMMEND_PRELOAD_ENABLED = "home_recommend_preload_enabled"
    const val KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED = "dynamic_preferred_video_tab_enabled"
    const val KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED = "dynamic_remove_city_tab_enabled"
    const val KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED = "dynamic_remove_school_tab_enabled"
    const val KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED = "custom_home_recommend_filter_enabled"
    const val KEY_HIDDEN_HOME_RECOMMEND_ITEMS = "hidden_home_recommend_items"
    const val KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED = "custom_home_recommend_tab_filter_enabled"
    const val KEY_HIDDEN_HOME_RECOMMEND_TABS = "hidden_home_recommend_tabs"
    const val KEY_KNOWN_HOME_RECOMMEND_TABS = "known_home_recommend_tabs"
    const val KEY_CUSTOM_VIDEO_DETAIL_RELATE_FILTER_ENABLED = "custom_video_detail_relate_filter_enabled"
    const val KEY_HIDDEN_VIDEO_DETAIL_RELATE_TYPES = "hidden_video_detail_relate_types"
    const val KEY_KNOWN_VIDEO_DETAIL_RELATE_TYPES = "known_video_detail_relate_types"
    const val KEY_VIDEO_DETAIL_RELATE_TITLE_KEYWORDS = "video_detail_relate_title_keywords"
    const val KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED = "hide_all_home_components_enabled"
    const val KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED = "custom_home_component_hide_enabled"
    const val KEY_HIDDEN_HOME_COMPONENTS = "hidden_home_components"
    const val KEY_KNOWN_HOME_COMPONENTS = "known_home_components"
    const val KEY_PURIFY_STORY_VIDEO_AD_ENABLED = "purify_story_video_ad_enabled"
    const val KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED = "story_video_default_launch_enabled"
    const val KEY_STORY_VIDEO_IMMERSIVE_FULLSCREEN_ENABLED = "story_video_immersive_fullscreen_enabled"
    const val KEY_STORY_VIDEO_KEEP_DANMAKU_ON_COMMENT_ENABLED = "story_video_keep_danmaku_on_comment_enabled"
    const val KEY_STORY_VIDEO_COMPONENT_ALPHA = "story_video_component_alpha"
    const val KEY_PURIFY_STORY_VIDEO_AD_TAGS = "purify_story_video_ad_tags"
    const val KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT = "purify_story_video_ad_blocked_count"
    const val KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED = "custom_download_thread_enabled"
    const val KEY_CUSTOM_DOWNLOAD_CONCURRENCY = "custom_download_concurrency"
    const val KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED = "skip_mini_game_reward_ad_enabled"
    const val KEY_BLOCK_LIVE_RESERVATION_ENABLED = "block_live_reservation_enabled"
    const val KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED = "block_live_room_qoe_popup_enabled"
    const val KEY_REMOVE_LIVE_ROOM_BLUR_MASK_ENABLED = "remove_live_room_blur_mask_enabled"
    const val KEY_DISABLE_LONG_PRESS_COPY_ENABLED = "disable_long_press_copy_enabled"
    const val KEY_ENHANCE_LONG_PRESS_COPY_ENABLED = "enhance_long_press_copy_enabled"
    const val KEY_CUSTOM_BOTTOM_BAR_ENABLED = "custom_bottom_bar_enabled"
    const val KEY_CUSTOM_THEME_ENABLED = "custom_theme_enabled"
    const val KEY_CUSTOM_THEME_COLOR = "custom_theme_color"
    const val KEY_CUSTOM_SKIN_ENABLED = "custom_skin_enabled"
    const val KEY_CUSTOM_SKIN_JSON = "custom_skin_json"
    const val KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED = "block_activity_meta_sticker_enabled"
    const val DEFAULT_CUSTOM_THEME_COLOR = 0xFFFB7299.toInt()
    const val KEY_HIDDEN_BOTTOM_BAR_ITEMS = "hidden_bottom_bar_items"
    const val KEY_KNOWN_BOTTOM_BAR_ITEMS = "known_bottom_bar_items"
    const val KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED = "hide_home_top_bar_promotion_enabled"
    const val KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED = "hide_home_search_default_word_enabled"
    const val KEY_FULL_NUMBER_FORMAT_ENABLED = "full_number_format_enabled"
    const val KEY_UNLOCK_COMMENT_GIF_ENABLED = "unlock_comment_gif_enabled"
    const val KEY_LAST_ACCESS_KEY = "last_access_key"
    const val KEY_HOST_ACCOUNT_UID = "host_account_uid"
    const val KEY_HOST_ACCOUNT_NAME = "host_account_name"
    const val KEY_HIDE_DESKTOP_ICON = "hide_desktop_icon"
    const val KEY_ACCEPT_PRERELEASE_UPDATE = "accept_prerelease_update"
    const val KEY_COMMENT_DISABLE = "vid_comment_disable"
    const val KEY_COMMENT_NO_QUICK_REPLY = "vid_comment_no_quick_reply"
    const val KEY_COMMENT_NO_VOTE = "vid_comment_no_vote"
    const val KEY_COMMENT_NO_FOLLOW = "vid_comment_no_follow"
    const val KEY_COMMENT_NO_SEARCH = "vid_comment_no_search"
    const val KEY_COMMENT_NO_EMPTY_PAGE = "vid_comment_no_empty_page"
    const val KEY_COMMENT_NO_QOE = "vid_comment_no_qoe"
    const val KEY_COMMENT_NO_OPERATION = "vid_comment_no_operation"
    const val KEY_COMMENT_KEYWORD_FILTER_ENABLED = "vid_comment_keyword_filter_enabled"
    const val KEY_COMMENT_KEYWORDS = "vid_comment_keywords"
    const val KEY_COMMENT_MIN_LEVEL_ENABLED = "vid_comment_min_level_enabled"
    const val KEY_COMMENT_MIN_LEVEL = "vid_comment_min_level"
    const val MAX_COMMENT_KEYWORDS = 64
    const val DEFAULT_COMMENT_MIN_LEVEL = 3
    const val KEY_MINE_REMOVE_VIP = "mine_remove_vip"
    const val KEY_MINE_KEEP_VIP_SPACE = "mine_keep_vip_space"
    const val KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED = "custom_mine_component_hide_enabled"
    const val KEY_HIDDEN_MINE_COMPONENTS = "hidden_mine_components"
    const val KEY_KNOWN_MINE_COMPONENTS = "known_mine_components"
    const val MAX_HOME_RECOMMEND_TITLE_KEYWORDS = 64

    const val HOME_RECOMMEND_FILTER_AD = "ad"
    const val HOME_RECOMMEND_FILTER_PICTURE = "picture"
    const val HOME_RECOMMEND_FILTER_GAME_PROMO = "game_promo"
    const val HOME_RECOMMEND_FILTER_LIVE = "live"
    const val HOME_RECOMMEND_FILTER_KETANG = "ketang"
    const val HOME_RECOMMEND_FILTER_VERTICAL_AV = "vertical_av"
    const val HOME_RECOMMEND_FILTER_LARGE_COVER = "large_cover"

    val homeRecommendFilterKeys = listOf(
        HOME_RECOMMEND_FILTER_AD,
        HOME_RECOMMEND_FILTER_PICTURE,
        HOME_RECOMMEND_FILTER_GAME_PROMO,
        HOME_RECOMMEND_FILTER_LIVE,
        HOME_RECOMMEND_FILTER_KETANG,
        HOME_RECOMMEND_FILTER_VERTICAL_AV,
        HOME_RECOMMEND_FILTER_LARGE_COVER,
    )

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
    const val KEY_SYMBOL_SCAN_STATUS_SUMMARY = "symbol_scan_status_summary"
    const val KEY_SYMBOL_SCAN_STATUS_REPORT = "symbol_scan_status_report"
    const val KEY_SYMBOL_SCAN_STATUS_UPDATED_AT = "symbol_scan_status_updated_at"
    const val KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID = "symbol_scan_refresh_request_id"
    const val KEY_SYMBOL_SCAN_REFRESH_HANDLED_ID = "symbol_scan_refresh_handled_id"

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
        StoryVideoAdTag("ad", "广告"),
        StoryVideoAdTag("live", "直播"),
    )
    private val storyVideoAdTagKeys = storyVideoAdTags.mapTo(linkedSetOf()) { it.key }

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

    @Volatile
    private var skipVideoAdCache: SkipVideoAdCache? = null
    @Volatile
    private var knownBottomBarItemsCache: Set<String>? = null
    @Volatile
    private var knownHomeRecommendTabsCache: Set<String>? = null
    @Volatile
    private var knownHomeComponentsCache: Set<String>? = null
    @Volatile
    private var knownMineComponentsCache: Set<String>? = null
    @Volatile
    private var knownVideoDetailRelateTypesCache: Set<String>? = null

    enum class ExportableValueType {
        BOOLEAN,
        INT,
        STRING,
        STRING_SET,
    }

    data class ExportableConfigSpec(
        val key: String,
        val type: ExportableValueType,
        val read: (SharedPreferences) -> Any?,
    )

    val exportableSwitchSpecs = listOf(
        ExportableConfigSpec(KEY_MINI_PROGRAM_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_MINI_PROGRAM_ENABLED, false) },
        ExportableConfigSpec(KEY_PURIFY_SHARE_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_PURIFY_SHARE_ENABLED, false) },
        ExportableConfigSpec(KEY_SKIP_REWARD_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_REWARD_AD_ENABLED, false) },
        ExportableConfigSpec(KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED, false) },
        ExportableConfigSpec(KEY_BLOCK_UPDATE_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_UPDATE_ENABLED, false) },
        ExportableConfigSpec(KEY_SKIP_SPLASH_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_SPLASH_AD_ENABLED, true) },
        ExportableConfigSpec(KEY_SKIP_VIDEO_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_VIDEO_AD_ENABLED, false) },
        ExportableConfigSpec(KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, false) },
        ExportableConfigSpec(KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE, false) },
        ExportableConfigSpec(KEY_ACCESS_KEY_SETTINGS_VISIBLE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_ACCESS_KEY_SETTINGS_VISIBLE, false) },
        ExportableConfigSpec(KEY_TRY_FREE_QUALITY_SETTINGS_VISIBLE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_TRY_FREE_QUALITY_SETTINGS_VISIBLE, false) },
        ExportableConfigSpec(KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED, false) },
        ExportableConfigSpec(KEY_BLOCK_CHRONOS_PROMOTION_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_CHRONOS_PROMOTION_ENABLED, false) },
        ExportableConfigSpec(KEY_UNLOCK_VIDEO_FEATURES_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_ENABLED, false) },
        ExportableConfigSpec(KEY_UNLOCK_VIDEO_FEATURES_UI_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_UI_ENABLED, true)
        },
        ExportableConfigSpec(KEY_UNLOCK_HIGHEST_BITRATE_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_UNLOCK_HIGHEST_BITRATE_ENABLED, false)
        },
        ExportableConfigSpec(KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED, false) },
        ExportableConfigSpec(KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED, false)
        },
        ExportableConfigSpec(KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED, false)
        },
        ExportableConfigSpec(KEY_AVOID_HDR_DOLBY_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_AVOID_HDR_DOLBY_ENABLED, false)
        },
        ExportableConfigSpec(KEY_HALF_SCREEN_QUALITY, ExportableValueType.INT) {
            getHalfScreenQuality(it)
        },
        ExportableConfigSpec(KEY_FULL_SCREEN_QUALITY, ExportableValueType.INT) {
            getFullScreenQuality(it)
        },
        ExportableConfigSpec(KEY_VIDEO_DOWNLOAD_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_VIDEO_DOWNLOAD_ENABLED, false)
        },
        ExportableConfigSpec(KEY_PLAYER_TRIPLE_SPEED_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_PLAYER_TRIPLE_SPEED_ENABLED, false)
        },
        ExportableConfigSpec(KEY_FIX_LIVE_QUALITY_URL_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_FIX_LIVE_QUALITY_URL_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_CDN_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_CDN_ENABLED, false) },
        ExportableConfigSpec(KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED, false) },
        ExportableConfigSpec(KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED, false) },
        ExportableConfigSpec(KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED, false) },
        ExportableConfigSpec(KEY_HOME_RECOMMEND_VERTICAL_AV_DETAIL_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HOME_RECOMMEND_VERTICAL_AV_DETAIL_ENABLED, false) },
        ExportableConfigSpec(KEY_HOME_RECOMMEND_PRELOAD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HOME_RECOMMEND_PRELOAD_ENABLED, false) },
        ExportableConfigSpec(KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED, false)
        },
        ExportableConfigSpec(KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED, false)
        },
        ExportableConfigSpec(KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED, false)
        },
        ExportableConfigSpec(KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_VIDEO_DETAIL_RELATE_FILTER_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_VIDEO_DETAIL_RELATE_FILTER_ENABLED, false) },
        ExportableConfigSpec(KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED, false) },
        ExportableConfigSpec(KEY_PURIFY_STORY_VIDEO_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_PURIFY_STORY_VIDEO_AD_ENABLED, false) },
        ExportableConfigSpec(KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED, false)
        },
        ExportableConfigSpec(KEY_STORY_VIDEO_IMMERSIVE_FULLSCREEN_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_STORY_VIDEO_IMMERSIVE_FULLSCREEN_ENABLED, false)
        },
        ExportableConfigSpec(KEY_STORY_VIDEO_KEEP_DANMAKU_ON_COMMENT_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_STORY_VIDEO_KEEP_DANMAKU_ON_COMMENT_ENABLED, false)
        },
        ExportableConfigSpec(KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED, false) },
        ExportableConfigSpec(KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED, true) },
        ExportableConfigSpec(KEY_BLOCK_LIVE_RESERVATION_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_LIVE_RESERVATION_ENABLED, false) },
        ExportableConfigSpec(KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_LIVE_ROOM_QOE_POPUP_ENABLED, false) },
        ExportableConfigSpec(KEY_REMOVE_LIVE_ROOM_BLUR_MASK_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_REMOVE_LIVE_ROOM_BLUR_MASK_ENABLED, false) },
        ExportableConfigSpec(KEY_DISABLE_LONG_PRESS_COPY_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_DISABLE_LONG_PRESS_COPY_ENABLED, false) },
        ExportableConfigSpec(KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_ENHANCE_LONG_PRESS_COPY_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_BOTTOM_BAR_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_BOTTOM_BAR_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_THEME_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_THEME_ENABLED, false) },
        ExportableConfigSpec(KEY_CUSTOM_SKIN_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_CUSTOM_SKIN_ENABLED, false) },
        ExportableConfigSpec(KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED, false) },
        ExportableConfigSpec(KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED, false) },
        ExportableConfigSpec(KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED, false) },
        ExportableConfigSpec(KEY_FULL_NUMBER_FORMAT_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_FULL_NUMBER_FORMAT_ENABLED, false) },
        ExportableConfigSpec(KEY_UNLOCK_COMMENT_GIF_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_UNLOCK_COMMENT_GIF_ENABLED, false) },
        ExportableConfigSpec(KEY_HIDE_DESKTOP_ICON, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_HIDE_DESKTOP_ICON, false) },
        ExportableConfigSpec(KEY_ACCEPT_PRERELEASE_UPDATE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_ACCEPT_PRERELEASE_UPDATE, false) },
        ExportableConfigSpec(KEY_COMMENT_DISABLE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_DISABLE, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_QUICK_REPLY, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_QUICK_REPLY, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_VOTE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_VOTE, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_FOLLOW, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_FOLLOW, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_SEARCH, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_SEARCH, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_EMPTY_PAGE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_EMPTY_PAGE, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_QOE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_QOE, false) },
        ExportableConfigSpec(KEY_COMMENT_NO_OPERATION, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_NO_OPERATION, false) },
        ExportableConfigSpec(KEY_COMMENT_KEYWORD_FILTER_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_KEYWORD_FILTER_ENABLED, false) },
        ExportableConfigSpec(KEY_COMMENT_MIN_LEVEL_ENABLED, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_COMMENT_MIN_LEVEL_ENABLED, false) },
        ExportableConfigSpec(KEY_MINE_REMOVE_VIP, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_MINE_REMOVE_VIP, false) },
        ExportableConfigSpec(KEY_MINE_KEEP_VIP_SPACE, ExportableValueType.BOOLEAN) { it.getBoolean(KEY_MINE_KEEP_VIP_SPACE, false) },
        ExportableConfigSpec(KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED, ExportableValueType.BOOLEAN) {
            it.getBoolean(KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED, false)
        },
    )

    val exportableManualSpecs = buildList<ExportableConfigSpec> {
        add(ExportableConfigSpec(KEY_HOME_RECOMMEND_TITLE_KEYWORDS, ExportableValueType.STRING) { it.getString(KEY_HOME_RECOMMEND_TITLE_KEYWORDS, "").orEmpty() })
        add(ExportableConfigSpec(KEY_COMMENT_KEYWORDS, ExportableValueType.STRING) { it.getString(KEY_COMMENT_KEYWORDS, "").orEmpty() })
        add(ExportableConfigSpec(KEY_COMMENT_MIN_LEVEL, ExportableValueType.INT) { getCommentMinLevel(it) })
        add(ExportableConfigSpec(KEY_CUSTOM_DOWNLOAD_CONCURRENCY, ExportableValueType.INT) { prefs ->
            prefs.getInt(KEY_CUSTOM_DOWNLOAD_CONCURRENCY, 1).coerceIn(1, 12)
        })
        add(ExportableConfigSpec(KEY_PURIFY_STORY_VIDEO_AD_TAGS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_PURIFY_STORY_VIDEO_AD_TAGS, defaultStoryVideoAdTags)?.toSet() ?: defaultStoryVideoAdTags
        })
        add(ExportableConfigSpec(KEY_STORY_VIDEO_COMPONENT_ALPHA, ExportableValueType.INT) { prefs ->
            getStoryVideoComponentAlphaPercent(prefs)
        })
        add(ExportableConfigSpec(KEY_CUSTOM_THEME_COLOR, ExportableValueType.INT) { prefs ->
            getCustomThemeColor(prefs)
        })
        add(ExportableConfigSpec(KEY_CUSTOM_SKIN_JSON, ExportableValueType.STRING) { prefs ->
            getCustomSkinJson(prefs)
        })
        add(ExportableConfigSpec(KEY_CUSTOM_CDN_HOST, ExportableValueType.STRING) { prefs ->
            getCustomCdnHost(prefs)
        })
        add(ExportableConfigSpec(KEY_HIDDEN_HOME_RECOMMEND_ITEMS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_HOME_RECOMMEND_ITEMS, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_HIDDEN_HOME_RECOMMEND_TABS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_HOME_RECOMMEND_TABS, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_HIDDEN_HOME_COMPONENTS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_HOME_COMPONENTS, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_HIDDEN_MINE_COMPONENTS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_MINE_COMPONENTS, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_HIDDEN_BOTTOM_BAR_ITEMS, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_BOTTOM_BAR_ITEMS, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_HIDDEN_VIDEO_DETAIL_RELATE_TYPES, ExportableValueType.STRING_SET) {
            it.getStringSet(KEY_HIDDEN_VIDEO_DETAIL_RELATE_TYPES, emptySet<String>())?.toSet() ?: emptySet<String>()
        })
        add(ExportableConfigSpec(KEY_VIDEO_DETAIL_RELATE_TITLE_KEYWORDS, ExportableValueType.STRING) { prefs ->
            getVideoDetailRelateTitleKeywordsText(prefs)
        })
        skipVideoAdCategories.forEach { category ->
            add(ExportableConfigSpec("$KEY_SKIP_VIDEO_AD_MODE_PREFIX${category.key}", ExportableValueType.INT) { prefs ->
                prefs.getInt(
                    "$KEY_SKIP_VIDEO_AD_MODE_PREFIX${category.key}",
                    defaultSkipVideoAdModes[category.key]?.value ?: SkipVideoAdMode.IGNORE.value,
                )
            })
        }
    }

    fun isSkipSplashAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_SPLASH_AD_ENABLED, true)

    fun isBlockTeenagersModeDialogEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_TEENAGERS_MODE_DIALOG_ENABLED, false)

    fun isBlockUpdateEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_UPDATE_ENABLED, false)

    fun isUnlockVideoFeaturesEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_ENABLED, false)

    fun isUnlockVideoFeaturesUiEnabled(prefs: SharedPreferences): Boolean =
        isUnlockVideoFeaturesEnabled(prefs) &&
            prefs.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_UI_ENABLED, true)

    fun isUnlockHighestBitrateEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_UNLOCK_HIGHEST_BITRATE_ENABLED, false)

    fun isAvoidHdrDolbyEnabled(prefs: SharedPreferences): Boolean =
        isUnlockHighestBitrateEnabled(prefs) &&
            prefs.getBoolean(KEY_AVOID_HDR_DOLBY_ENABLED, false)

    data class QualityOption(val qn: Int, val label: String)

    val halfScreenQualityOptions = listOf(
        QualityOption(0, "默认"),
        QualityOption(1, "跟随全屏清晰度"),
        QualityOption(16, "360P 流畅"),
        QualityOption(32, "480P 清晰"),
        QualityOption(64, "720P 高清"),
        QualityOption(74, "720P60 高帧率"),
        QualityOption(80, "1080P 高清"),
        QualityOption(112, "1080P 高码率"),
        QualityOption(116, "1080P60 高帧率"),
        QualityOption(120, "4K 超清"),
        QualityOption(127, "8K 超高清"),
    )

    val fullScreenQualityOptions = listOf(
        QualityOption(0, "默认"),
        QualityOption(16, "360P 流畅"),
        QualityOption(32, "480P 清晰"),
        QualityOption(64, "720P 高清"),
        QualityOption(74, "720P60 高帧率"),
        QualityOption(80, "1080P 高清"),
        QualityOption(112, "1080P 高码率"),
        QualityOption(116, "1080P60 高帧率"),
        QualityOption(120, "4K 超清"),
        QualityOption(127, "8K 超高清"),
    )

    fun getHalfScreenQuality(prefs: SharedPreferences): Int {
        return prefs.getInt(KEY_HALF_SCREEN_QUALITY, 0)
    }

    fun getFullScreenQuality(prefs: SharedPreferences): Int {
        return prefs.getInt(KEY_FULL_SCREEN_QUALITY, 0)
    }

    fun isVideoDownloadEnabled(prefs: SharedPreferences): Boolean =
        isTryFreeQualitySettingsVisible(prefs) && prefs.getBoolean(KEY_VIDEO_DOWNLOAD_ENABLED, false)

    fun isPlayerTransparentStatusBarEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PLAYER_TRANSPARENT_STATUS_BAR_ENABLED, false)

    fun isHidePlayerPortraitControlEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_PLAYER_PORTRAIT_CONTROL_ENABLED, false)

    fun isPlayerTripleSpeedEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PLAYER_TRIPLE_SPEED_ENABLED, false)

    fun isPlayerLongPressSpeedLockEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PLAYER_LONG_PRESS_SPEED_LOCK_ENABLED, false)

    fun isSkipVideoAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_VIDEO_AD_ENABLED, false)

    fun refreshSkipVideoAdCache(prefs: SharedPreferences): SkipVideoAdCache =
        SkipVideoAdCache(
            enabled = prefs.getBoolean(KEY_SKIP_VIDEO_AD_ENABLED, false),
            autoLikeEnabled = prefs.getBoolean(KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, false),
            modes = buildMap {
                val legacyCategories = prefs.getStringSet(KEY_SKIP_VIDEO_AD_CATEGORIES, null)
                skipVideoAdCategories.forEach { category ->
                    put(category.key, resolveSkipVideoAdMode(prefs, category.key, legacyCategories))
                }
            },
        ).also { cache ->
            skipVideoAdCache = cache
        }

    fun getSkipVideoAdCache(prefs: SharedPreferences): SkipVideoAdCache =
        skipVideoAdCache ?: refreshSkipVideoAdCache(prefs)

    fun isSkipVideoAdEnabledCached(prefs: SharedPreferences): Boolean =
        getSkipVideoAdCache(prefs).enabled

    fun isSkipVideoAdAutoLikeEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_VIDEO_AD_AUTO_LIKE_ENABLED, false)

    fun getSkipVideoAdMode(prefs: SharedPreferences, category: String): SkipVideoAdMode =
        resolveSkipVideoAdMode(prefs, category)

    fun getSkipVideoAdModeCached(prefs: SharedPreferences, category: String): SkipVideoAdMode =
        getSkipVideoAdCache(prefs).modes[category] ?: SkipVideoAdMode.IGNORE

    fun getSkipVideoAdCategories(prefs: SharedPreferences): Set<String> =
        skipVideoAdCategories
            .asSequence()
            .filter { getSkipVideoAdMode(prefs, it.key) != SkipVideoAdMode.IGNORE }
            .map { it.key }
            .toSet()

    fun getSkipVideoAdCategoriesCached(prefs: SharedPreferences): Set<String> =
        getSkipVideoAdCache(prefs).enabledCategories

    fun isSkipVideoAdSettingsVisible(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SKIP_VIDEO_AD_SETTINGS_VISIBLE, false)

    fun isAccessKeySettingsVisible(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ACCESS_KEY_SETTINGS_VISIBLE, false)

    fun isTryFreeQualitySettingsVisible(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_TRY_FREE_QUALITY_SETTINGS_VISIBLE, false)

    fun isBlockVideoDetailBannerAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_VIDEO_DETAIL_BANNER_AD_ENABLED, false)

    fun isBlockChronosPromotionEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_CHRONOS_PROMOTION_ENABLED, false)

    fun isAutoLikeVideoDetailEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED, false)

    fun isFixLiveQualityUrlEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FIX_LIVE_QUALITY_URL_ENABLED, false)

    /** 已知的 UPos 节点，显示名称与 PiliPlus 当前的 CDN 列表保持一致。 */
    data class CdnEndpoint(val name: String, val host: String)

    val cdnEndpoints = listOf(
        CdnEndpoint("ali（阿里）", "upos-sz-mirrorali.bilivideo.com"),
        CdnEndpoint("alib（阿里）", "upos-sz-mirroralib.bilivideo.com"),
        CdnEndpoint("alio1（阿里）", "upos-sz-mirroralio1.bilivideo.com"),
        CdnEndpoint("bos（百度）", "upos-sz-mirrorbos.bilivideo.com"),
        CdnEndpoint("cos（腾讯）", "upos-sz-mirrorcos.bilivideo.com"),
        CdnEndpoint("cosb（腾讯）", "upos-sz-mirrorcosb.bilivideo.com"),
        CdnEndpoint("coso1（腾讯）", "upos-sz-mirrorcoso1.bilivideo.com"),
        CdnEndpoint("hw（华为）", "upos-sz-mirrorhw.bilivideo.com"),
        CdnEndpoint("hwb（华为）", "upos-sz-mirrorhwb.bilivideo.com"),
        CdnEndpoint("hwo1（华为）", "upos-sz-mirrorhwo1.bilivideo.com"),
        CdnEndpoint("08c（华为）", "upos-sz-mirror08c.bilivideo.com"),
        CdnEndpoint("08h（华为）", "upos-sz-mirror08h.bilivideo.com"),
        CdnEndpoint("08ct（华为）", "upos-sz-mirror08ct.bilivideo.com"),
        CdnEndpoint("tf_hw（华为）", "upos-tf-all-hw.bilivideo.com"),
        CdnEndpoint("tf_tx（腾讯）", "upos-tf-all-tx.bilivideo.com"),
        CdnEndpoint("akamai（海外）", "upos-hz-mirrorakam.akamaized.net"),
        CdnEndpoint("aliov（阿里海外）", "upos-sz-mirroraliov.bilivideo.com"),
        CdnEndpoint("cosov（腾讯海外）", "upos-sz-mirrorcosov.bilivideo.com"),
        CdnEndpoint("hwov（华为海外）", "upos-sz-mirrorhwov.bilivideo.com"),
        CdnEndpoint("hk_bcache（Bilibili 海外）", "cn-hk-eq-bcache-01.bilivideo.com"),
    )

    fun isCustomCdnEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_CDN_ENABLED, false) && getCustomCdnHost(prefs) != null

    fun getCustomCdnHost(prefs: SharedPreferences): String? =
        normalizeCdnHost(prefs.getString(KEY_CUSTOM_CDN_HOST, null))

    fun normalizeCdnHost(value: String?): String? {
        val host = value.orEmpty().trim().removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').trimEnd('/')
        return host.takeIf {
            it.length in 1..253 &&
                it.none(Char::isWhitespace) &&
                it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?(?::[0-9]{1,5})?"))
        }
    }

    fun getHomeRecommendTitleKeywordsText(prefs: SharedPreferences): String =
        prefs.getString(KEY_HOME_RECOMMEND_TITLE_KEYWORDS, "").orEmpty()

    fun parseHomeRecommendTitleKeywords(raw: String): List<String> =
        raw.split('\n', '\r', ',', '，', ';', '；')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_HOME_RECOMMEND_TITLE_KEYWORDS)
            .toList()

    fun isHomeRecommendVerticalAvDetailEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HOME_RECOMMEND_VERTICAL_AV_DETAIL_ENABLED, false)

    fun isHomeRecommendPreloadEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HOME_RECOMMEND_PRELOAD_ENABLED, false)

    fun isDynamicPreferredVideoTabEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_DYNAMIC_PREFERRED_VIDEO_TAB_ENABLED, false)

    fun isDynamicRemoveCityTabEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_DYNAMIC_REMOVE_CITY_TAB_ENABLED, false)

    fun isDynamicRemoveSchoolTabEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_DYNAMIC_REMOVE_SCHOOL_TAB_ENABLED, false)

    fun isCustomHomeRecommendFilterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_HOME_RECOMMEND_FILTER_ENABLED, false) ||
            legacyHomeRecommendFilterItems(prefs).isNotEmpty()

    fun getHiddenHomeRecommendItems(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_HOME_RECOMMEND_ITEMS, emptySet())
            ?.filterTo(linkedSetOf()) { it in homeRecommendFilterKeys }
            .orEmpty() + legacyHomeRecommendFilterItems(prefs)

    fun clearLegacyHomeRecommendFilterSwitches(editor: SharedPreferences.Editor): SharedPreferences.Editor =
        editor
            .putBoolean(KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED, false)
            .putBoolean(KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED, false)
            .putBoolean(KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED, false)

    fun isCustomHomeRecommendTabFilterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_HOME_RECOMMEND_TAB_FILTER_ENABLED, false)

    fun getHiddenHomeRecommendTabs(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_HOME_RECOMMEND_TABS, emptySet()) ?: emptySet()

    fun getKnownHomeRecommendTabs(prefs: SharedPreferences): Set<String> =
        knownHomeRecommendTabsCache
            ?: prefs.getStringSet(KEY_KNOWN_HOME_RECOMMEND_TABS, emptySet())
            ?: emptySet()

    fun refreshKnownHomeRecommendTabsCache(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_KNOWN_HOME_RECOMMEND_TABS, emptySet())
            ?.toSet()
            .orEmpty()
            .also { knownHomeRecommendTabsCache = it }

    fun cacheKnownHomeRecommendTabs(items: Set<String>) {
        knownHomeRecommendTabsCache = items.toSet()
    }

    private fun legacyHomeRecommendFilterItems(prefs: SharedPreferences): Set<String> = buildSet {
        if (prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_AD_ENABLED, false)) {
            add(HOME_RECOMMEND_FILTER_AD)
        }
        if (prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_PICTURE_ENABLED, false)) {
            add(HOME_RECOMMEND_FILTER_PICTURE)
        }
        if (prefs.getBoolean(KEY_PURIFY_HOME_RECOMMEND_GAME_PROMO_ENABLED, false)) {
            add(HOME_RECOMMEND_FILTER_GAME_PROMO)
        }
    }

    fun isHideAllHomeComponentsEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_ALL_HOME_COMPONENTS_ENABLED, false)

    fun isCustomHomeComponentHideEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_HOME_COMPONENT_HIDE_ENABLED, false)

    fun getHiddenHomeComponents(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_HOME_COMPONENTS, emptySet()) ?: emptySet()

    fun getKnownHomeComponents(prefs: SharedPreferences): Set<String> =
        knownHomeComponentsCache
            ?: prefs.getStringSet(KEY_KNOWN_HOME_COMPONENTS, emptySet())
            ?: emptySet()

    fun cacheKnownHomeComponents(items: Set<String>) {
        knownHomeComponentsCache = items.toSet()
    }

    fun isCustomMineComponentHideEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_MINE_COMPONENT_HIDE_ENABLED, false)

    fun getHiddenMineComponents(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_MINE_COMPONENTS, emptySet()) ?: emptySet()

    fun getKnownMineComponents(prefs: SharedPreferences): Set<String> =
        knownMineComponentsCache
            ?: prefs.getStringSet(KEY_KNOWN_MINE_COMPONENTS, emptySet())
            ?: emptySet()

    fun cacheKnownMineComponents(items: Set<String>) {
        knownMineComponentsCache = items.toSet()
    }

    fun isCustomVideoDetailRelateFilterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_VIDEO_DETAIL_RELATE_FILTER_ENABLED, false)

    fun getHiddenVideoDetailRelateTypes(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_HIDDEN_VIDEO_DETAIL_RELATE_TYPES, emptySet()) ?: emptySet()

    fun getKnownVideoDetailRelateTypes(prefs: SharedPreferences): Set<String> =
        knownVideoDetailRelateTypesCache
            ?: prefs.getStringSet(KEY_KNOWN_VIDEO_DETAIL_RELATE_TYPES, emptySet())
            ?: emptySet()

    fun cacheKnownVideoDetailRelateTypes(items: Set<String>) {
        knownVideoDetailRelateTypesCache = items.toSet()
    }

    fun refreshKnownVideoDetailRelateTypesCache(prefs: SharedPreferences) {
        knownVideoDetailRelateTypesCache = prefs.getStringSet(KEY_KNOWN_VIDEO_DETAIL_RELATE_TYPES, emptySet())?.toSet()
    }

    fun getVideoDetailRelateTitleKeywordsText(prefs: SharedPreferences): String =
        prefs.getString(KEY_VIDEO_DETAIL_RELATE_TITLE_KEYWORDS, "")?.trim().orEmpty()

    fun parseVideoDetailRelateTitleKeywords(raw: String): List<String> =
        raw.split('\n', ',', '，')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun getRelateTypeDisplayName(type: String, context: Context): String {
        val upper = type.trim().uppercase()
        return when (upper) {
            "AV" -> context.getString(R.string.video_detail_relate_type_av)
            "BANGUMI" -> context.getString(R.string.video_detail_relate_type_bangumi)
            "RESOURCE" -> context.getString(R.string.video_detail_relate_type_resource)
            "GAME" -> context.getString(R.string.video_detail_relate_type_game)
            "CM" -> context.getString(R.string.video_detail_relate_type_cm)
            "LIVE" -> context.getString(R.string.video_detail_relate_type_live)
            "SPECIAL" -> context.getString(R.string.video_detail_relate_type_special)
            "SPECIAL_S" -> context.getString(R.string.video_detail_relate_type_special_s)
            "COURSE" -> context.getString(R.string.video_detail_relate_type_course)
            "MINI_PROGRAM" -> context.getString(R.string.video_detail_relate_type_mini_program)
            "AI_CARD", "AI_RECOMMEND" -> context.getString(R.string.video_detail_relate_type_ai_card)
            "BANGUMI_AV" -> context.getString(R.string.video_detail_relate_type_bangumi_av)
            "BANGUMI_SEASON" -> context.getString(R.string.video_detail_relate_type_bangumi_season)
            "ACTIVITY" -> context.getString(R.string.video_detail_relate_type_activity)
            "COUPON" -> context.getString(R.string.video_detail_relate_type_coupon)
            "COMMUNITY" -> context.getString(R.string.video_detail_relate_type_community)
            "HISTORY_AV" -> context.getString(R.string.video_detail_relate_type_history_av)
            else -> context.getString(R.string.video_detail_relate_type_unknown, type)
        }
    }

    fun isPurifyStoryVideoAdEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PURIFY_STORY_VIDEO_AD_ENABLED, false)

    fun isStoryVideoDefaultLaunchEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_STORY_VIDEO_DEFAULT_LAUNCH_ENABLED, false)

    fun isStoryVideoImmersiveFullscreenEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_STORY_VIDEO_IMMERSIVE_FULLSCREEN_ENABLED, false)

    fun isStoryVideoKeepDanmakuOnCommentEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_STORY_VIDEO_KEEP_DANMAKU_ON_COMMENT_ENABLED, false)

    fun getStoryVideoComponentAlphaPercent(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_STORY_VIDEO_COMPONENT_ALPHA, 100).coerceIn(0, 100)

    fun getStoryVideoComponentAlpha(prefs: SharedPreferences): Float =
        getStoryVideoComponentAlphaPercent(prefs) / 100f

    fun getPurifyStoryVideoAdTags(prefs: SharedPreferences): Set<String> =
        (prefs.getStringSet(KEY_PURIFY_STORY_VIDEO_AD_TAGS, defaultStoryVideoAdTags)
            ?: defaultStoryVideoAdTags)
            .filterTo(linkedSetOf()) { it in storyVideoAdTagKeys }

    fun isCustomDownloadThreadEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_DOWNLOAD_THREAD_ENABLED, false)

    fun isCustomThemeEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_THEME_ENABLED, false)

    fun getCustomThemeColor(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_CUSTOM_THEME_COLOR, DEFAULT_CUSTOM_THEME_COLOR)
            .let { color -> (color and 0x00FFFFFF) or 0xFF000000.toInt() }

    fun isCustomSkinEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_CUSTOM_SKIN_ENABLED, false)

    fun isBlockActivityMetaStickerEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_BLOCK_ACTIVITY_META_STICKER_ENABLED, false)

    fun getCustomSkinJson(prefs: SharedPreferences): String =
        prefs.getString(KEY_CUSTOM_SKIN_JSON, "").orEmpty().trim()

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

    fun isRemoveLiveRoomBlurMaskEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_REMOVE_LIVE_ROOM_BLUR_MASK_ENABLED, false)

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
        knownBottomBarItemsCache
            ?: prefs.getStringSet(KEY_KNOWN_BOTTOM_BAR_ITEMS, emptySet())
            ?: emptySet()

    fun refreshKnownBottomBarItemsCache(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_KNOWN_BOTTOM_BAR_ITEMS, emptySet())
            ?.toSet()
            .orEmpty()
            .also { knownBottomBarItemsCache = it }

    fun cacheKnownBottomBarItems(items: Set<String>) {
        knownBottomBarItemsCache = items.toSet()
    }

    fun isHideHomeTopBarPromotionEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_HOME_TOP_BAR_PROMOTION_ENABLED, false)

    fun isHideHomeSearchDefaultWordEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_HOME_SEARCH_DEFAULT_WORD_ENABLED, false)

    fun isFullNumberFormatEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_FULL_NUMBER_FORMAT_ENABLED, false)

    fun isUnlockCommentGifEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_UNLOCK_COMMENT_GIF_ENABLED, false)

    fun isHideDesktopIconEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_HIDE_DESKTOP_ICON, false)

    fun isAcceptPrereleaseUpdateEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_ACCEPT_PRERELEASE_UPDATE, false)

    fun isCommentDisableEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_DISABLE, false)

    fun isCommentNoQuickReplyEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_QUICK_REPLY, false)

    fun isCommentNoVoteEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_VOTE, false)

    fun isCommentNoFollowEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_FOLLOW, false)

    fun isCommentNoSearchEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_SEARCH, false)

    fun isCommentNoEmptyPageEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_EMPTY_PAGE, false)

    fun isCommentNoQoeEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_QOE, false)

    fun isCommentNoOperationEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_NO_OPERATION, false)

    fun isCommentKeywordFilterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_KEYWORD_FILTER_ENABLED, false)

    fun getCommentKeywordsText(prefs: SharedPreferences): String =
        prefs.getString(KEY_COMMENT_KEYWORDS, "").orEmpty()

    fun parseCommentKeywords(raw: String): List<String> =
        raw.split('\n', '\r', ',', '，', ';', '；')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_COMMENT_KEYWORDS)
            .toList()

    fun isCommentMinLevelEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_COMMENT_MIN_LEVEL_ENABLED, false)

    fun getCommentMinLevel(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_COMMENT_MIN_LEVEL, DEFAULT_COMMENT_MIN_LEVEL).coerceIn(0, 6)

    fun isMineRemoveVipEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_MINE_REMOVE_VIP, false)

    fun isMineKeepVipSpaceEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_MINE_KEEP_VIP_SPACE, false)

    private fun resolveSkipVideoAdMode(
        prefs: SharedPreferences,
        category: String,
        legacyCategories: Set<String>? = prefs.getStringSet(KEY_SKIP_VIDEO_AD_CATEGORIES, null),
    ): SkipVideoAdMode =
        if (prefs.contains("$KEY_SKIP_VIDEO_AD_MODE_PREFIX$category")) {
            SkipVideoAdMode.fromValue(
                prefs.getInt(
                    "$KEY_SKIP_VIDEO_AD_MODE_PREFIX$category",
                    defaultSkipVideoAdModes[category]?.value ?: SkipVideoAdMode.IGNORE.value,
                ),
            )
        } else if (legacyCategories != null) {
            if (category in legacyCategories) SkipVideoAdMode.AUTO_SKIP else SkipVideoAdMode.IGNORE
        } else {
            defaultSkipVideoAdModes[category] ?: SkipVideoAdMode.IGNORE
        }
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
)

data class SponsorBlockCategory(
    val key: String,
    val label: String,
    val summary: String,
    val color: Int,
    val previewColor: Int,
)

data class SkipVideoAdCache(
    val enabled: Boolean,
    val autoLikeEnabled: Boolean,
    val modes: Map<String, SkipVideoAdMode>,
) {
    val enabledCategories: Set<String> = modes
        .asSequence()
        .filter { (_, mode) -> mode != SkipVideoAdMode.IGNORE }
        .map { (category, _) -> category }
        .toSet()
}
