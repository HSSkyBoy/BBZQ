package io.github.bbzq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchMatcherTest {

    private val items = listOf(
        SettingsSearchItem("portrait", "竖屏内容过滤", "选择需要过滤的内容", "进阶设置"),
        SettingsSearchItem("backup", "设置备份与恢复", "导出或导入备份", "基础设置"),
        SettingsSearchItem("duplicate", "竖屏内容过滤", "重复入口", "实验性功能"),
    )

    @Test
    fun multipleTokensMustAllMatchLocalizedText() {
        assertEquals(
            listOf("portrait"),
            SettingsSearchMatcher.search("竖屏 过滤", items).map { it.key },
        )
        assertTrue(SettingsSearchMatcher.search("竖屏 备份", items).isEmpty())
    }

    @Test
    fun titleMatchesRankAheadOfDetailMatchesAndDuplicatesCollapse() {
        val results = SettingsSearchMatcher.search("设置", items)

        assertEquals("backup", results.first().key)
        assertEquals(results.map { it.title }.distinct(), results.map { it.title })
    }

    @Test
    fun blankQueryAndZeroLimitReturnNoResult() {
        assertTrue(SettingsSearchMatcher.search(" ", items).isEmpty())
        assertTrue(SettingsSearchMatcher.search("设置", items, limit = 0).isEmpty())
    }

    @Test
    fun searchMatchExposesTitleAndSectionKeywordRanges() {
        val match = SettingsSearchMatcher.searchMatches("竖屏 进阶", items).single()

        assertEquals(
            listOf("竖屏"),
            match.titleRanges.map { match.item.title.substring(it) },
        )
        assertEquals(
            listOf("进阶"),
            match.sectionRanges.map { match.item.section.substring(it) },
        )
        assertTrue(match.detailRanges.isEmpty())
    }

    @Test
    fun searchMatchExposesEverySeparatedDetailKeywordRange() {
        val match = SettingsSearchMatcher.searchMatches("选择 的", items)
            .single { it.item.key == "portrait" }

        assertEquals(
            listOf("选择", "的"),
            match.detailRanges.map { match.item.detail.substring(it) },
        )
        assertTrue(match.titleRanges.isEmpty())
        assertTrue(match.sectionRanges.isEmpty())
    }
}
