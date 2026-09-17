package io.github.bbzq

import android.view.View
import java.util.Locale

data class SettingsSearchItem(
    val key: String,
    val title: String,
    val detail: String,
    val section: String,
)

data class SettingsSearchMatch(
    val item: SettingsSearchItem,
    val titleRanges: List<IntRange>,
    val detailRanges: List<IntRange>,
    val sectionRanges: List<IntRange>,
)

data class SettingsSearchTarget(
    val item: SettingsSearchItem,
    val view: View?,
    val page: String,
)

object SettingsSearchMatcher {
    fun search(
        query: String,
        items: List<SettingsSearchItem>,
        limit: Int = 40,
    ): List<SettingsSearchItem> = searchMatches(query, items, limit).map(SettingsSearchMatch::item)

    fun searchMatches(
        query: String,
        items: List<SettingsSearchItem>,
        limit: Int = 40,
    ): List<SettingsSearchMatch> {
        val tokens = normalize(query).split(Regex("\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty() || limit <= 0) return emptyList()

        return items.asSequence()
            .distinctBy { normalize(it.title) }
            .mapNotNull { item ->
                val title = normalize(item.title)
                val haystack = normalize("${item.title} ${item.detail} ${item.section}")
                if (tokens.all(haystack::contains)) {
                    val score = when {
                        title == tokens.joinToString(" ") -> 0
                        tokens.all(title::contains) -> 1
                        else -> 2
                    }
                    score to SettingsSearchMatch(
                        item = item,
                        titleRanges = findRanges(item.title, tokens),
                        detailRanges = findRanges(item.detail, tokens),
                        sectionRanges = findRanges(item.section, tokens),
                    )
                } else {
                    null
                }
            }
            .sortedWith(
                compareBy<Pair<Int, SettingsSearchMatch>> { it.first }
                    .thenBy { it.second.item.title.length },
            )
            .map(Pair<Int, SettingsSearchMatch>::second)
            .take(limit)
            .toList()
    }

    private fun findRanges(value: String, tokens: List<String>): List<IntRange> {
        if (value.isEmpty()) return emptyList()
        val ranges = tokens.distinct().flatMap { token ->
            buildList {
                var startIndex = 0
                while (startIndex <= value.length - token.length) {
                    val matchIndex = value.indexOf(
                        string = token,
                        startIndex = startIndex,
                        ignoreCase = true,
                    )
                    if (matchIndex < 0) break
                    add(matchIndex until matchIndex + token.length)
                    startIndex = matchIndex + token.length.coerceAtLeast(1)
                }
            }
        }.sortedBy(IntRange::first)
        if (ranges.isEmpty()) return emptyList()

        return buildList {
            var current = ranges.first()
            ranges.drop(1).forEach { next ->
                if (next.first <= current.last + 1) {
                    current = current.first..maxOf(current.last, next.last)
                } else {
                    add(current)
                    current = next
                }
            }
            add(current)
        }
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
