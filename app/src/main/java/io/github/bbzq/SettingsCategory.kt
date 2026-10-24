package io.github.bbzq

internal enum class SettingsCategory(
    val page: String,
    val titleRes: Int,
    val summaryRes: Int,
    val iconRes: Int,
    val general: Boolean,
) {
    APPEARANCE(
        SettingsActivity.PAGE_CATEGORY_APPEARANCE,
        R.string.category_appearance_title,
        R.string.category_appearance_summary,
        R.drawable.ic_category_appearance,
        general = true,
    ),
    SHARE(
        SettingsActivity.PAGE_CATEGORY_SHARE,
        R.string.category_share_title,
        R.string.category_share_summary,
        R.drawable.ic_category_share,
        general = true,
    ),
    STARTUP(
        SettingsActivity.PAGE_CATEGORY_STARTUP,
        R.string.category_startup_title,
        R.string.category_startup_summary,
        R.drawable.ic_category_startup,
        general = true,
    ),
    NETWORK(
        SettingsActivity.PAGE_CATEGORY_NETWORK,
        R.string.category_network_title,
        R.string.category_network_summary,
        R.drawable.ic_category_network,
        general = true,
    ),
    HOME(
        SettingsActivity.PAGE_CATEGORY_HOME,
        R.string.category_home_title,
        R.string.category_home_summary,
        R.drawable.ic_category_home,
        general = false,
    ),
    SEARCH(
        SettingsActivity.PAGE_CATEGORY_SEARCH,
        R.string.category_search_title,
        R.string.category_search_summary,
        R.drawable.ic_category_search,
        general = false,
    ),
    PLAYBACK(
        SettingsActivity.PAGE_CATEGORY_PLAYBACK,
        R.string.category_playback_title,
        R.string.category_playback_summary,
        R.drawable.ic_category_playback,
        general = false,
    ),
    STORY(
        SettingsActivity.PAGE_CATEGORY_STORY,
        R.string.category_story_title,
        R.string.category_story_summary,
        R.drawable.ic_category_story,
        general = false,
    ),
    LIVE(
        SettingsActivity.PAGE_CATEGORY_LIVE,
        R.string.category_live_title,
        R.string.category_live_summary,
        R.drawable.ic_category_live,
        general = false,
    ),
    COMMENT(
        SettingsActivity.PAGE_CATEGORY_COMMENT,
        R.string.category_comment_title,
        R.string.category_comment_summary,
        R.drawable.ic_category_comment,
        general = false,
    ),
    DYNAMIC(
        SettingsActivity.PAGE_CATEGORY_DYNAMIC,
        R.string.category_dynamic_title,
        R.string.category_dynamic_summary,
        R.drawable.ic_category_dynamic,
        general = false,
    ),
    MINE(
        SettingsActivity.PAGE_CATEGORY_MINE,
        R.string.category_mine_title,
        R.string.category_mine_summary,
        R.drawable.ic_category_mine,
        general = false,
    );

    companion object {
        fun forPage(page: String): SettingsCategory? = entries.firstOrNull { it.page == page }
    }
}
