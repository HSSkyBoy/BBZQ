package io.github.bzzq

import android.content.SharedPreferences

object ModuleSettings {
    const val PREFS_NAME = "bzzq_settings"
    const val KEY_SKIP_SPLASH_AD_ENABLED = "skip_splash_ad_enabled"
    const val KEY_UNLOCK_VIDEO_FEATURES_ENABLED = "unlock_video_features_enabled"
    const val KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED = "auto_like_video_detail_enabled"
    const val KEY_FIX_LIVE_QUALITY_URL_ENABLED = "fix_live_quality_url_enabled"
    const val KEY_PURIFY_STORY_VIDEO_AD_ENABLED = "purify_story_video_ad_enabled"
    const val KEY_PURIFY_STORY_VIDEO_AD_TAGS = "purify_story_video_ad_tags"
    const val KEY_PURIFY_STORY_VIDEO_AD_BLOCKED_COUNT = "purify_story_video_ad_blocked_count"
    const val KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED = "skip_mini_game_reward_ad_enabled"
    const val KEY_BLOCK_LIVE_RESERVATION_ENABLED = "block_live_reservation_enabled"
    const val KEY_LAST_ACCESS_KEY = "last_access_key"

    val defaultStoryVideoAdTags = setOf("ad")

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

    fun isSkipSplashAdEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_SKIP_SPLASH_AD_ENABLED, true)
    }

    fun isUnlockVideoFeaturesEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_UNLOCK_VIDEO_FEATURES_ENABLED, true)
    }

    fun isAutoLikeVideoDetailEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_AUTO_LIKE_VIDEO_DETAIL_ENABLED, false)
    }

    fun isFixLiveQualityUrlEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_FIX_LIVE_QUALITY_URL_ENABLED, false)
    }

    fun isPurifyStoryVideoAdEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_PURIFY_STORY_VIDEO_AD_ENABLED, false)
    }

    fun getPurifyStoryVideoAdTags(prefs: SharedPreferences): Set<String> {
        return prefs.getStringSet(KEY_PURIFY_STORY_VIDEO_AD_TAGS, defaultStoryVideoAdTags)
            ?: defaultStoryVideoAdTags
    }

    fun isSkipMiniGameRewardAdEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_SKIP_MINI_GAME_REWARD_AD_ENABLED, true)
    }

    fun isBlockLiveReservationEnabled(prefs: SharedPreferences): Boolean {
        return prefs.getBoolean(KEY_BLOCK_LIVE_RESERVATION_ENABLED, false)
    }
}

data class StoryVideoAdTag(
    val key: String,
    val label: String,
    val cartText: String?,
)
