package io.github.bbzq.feats.hook

internal object DynamicTabPolicy {
    fun <T> filter(
        tabs: List<T>, hideCity: Boolean, hideSchool: Boolean,
        title: (T) -> String?, name: (T) -> String?,
        preserveNonEmpty: Boolean = true,
    ): List<T> {
        val kept = tabs.filterNot { tab ->
            val label = title(tab)?.trim()
            val key = name(tab)?.trim()?.lowercase()
            (hideCity && (label == "同城" || key == "city")) ||
                (hideSchool && (label == "校园" || key == "campus" || key == "school"))
        }
        return if ((preserveNonEmpty && kept.isEmpty()) || kept.size == tabs.size) tabs else kept
    }

    fun <T> preferVideo(
        tabs: List<T>, title: (T) -> String?, name: (T) -> String?,
        isDefault: (T) -> Boolean, withDefault: (T, Boolean) -> T,
    ): List<T> {
        val video = tabs.indexOfFirst {
            title(it)?.trim() == "视频" || name(it)?.trim()?.lowercase() in setOf("video", "videos")
        }
        if (video < 0) return tabs
        var changed = false
        val updated = tabs.mapIndexed { index, tab ->
            val expected = index == video
            if (isDefault(tab) == expected) tab else {
                changed = true
                withDefault(tab, expected)
            }
        }
        return if (changed) updated else tabs
    }
}
