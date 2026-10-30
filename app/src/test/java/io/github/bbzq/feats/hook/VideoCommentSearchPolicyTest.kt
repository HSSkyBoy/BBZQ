package io.github.bbzq.feats.hook

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCommentSearchPolicyTest {

    @Test
    fun injectDisableSearchUnderlineToEmptyExtra() {
        val extra = ""
        val updated = injectDisableSearchUnderline(extra)
        val json = JSONObject(updated)
        assertTrue(json.optBoolean("disable_underline"))
    }

    @Test
    fun injectDisableSearchUnderlineToExistingExtra() {
        val extra = """{"scene":"detailList","track_id":"123"}"""
        val updated = injectDisableSearchUnderline(extra)
        val json = JSONObject(updated)
        assertEquals("detailList", json.optString("scene"))
        assertEquals("123", json.optString("track_id"))
        assertTrue(json.optBoolean("disable_underline"))
    }

    @Test
    fun preservesExtraIfDisableSearchUnderlineAlreadyTrue() {
        val extra = """{"disable_underline":true,"scene":"dialog"}"""
        val updated = injectDisableSearchUnderline(extra)
        assertEquals(extra, updated)
    }

    @Test
    fun overwritesDisableSearchUnderlineIfFalse() {
        val extra = """{"disable_underline":false,"scene":"dialog"}"""
        val updated = injectDisableSearchUnderline(extra)
        val json = JSONObject(updated)
        assertTrue(json.optBoolean("disable_underline"))
    }

    @Test
    fun identifiesBilibiliSearchUrls() {
        assertTrue(isCommentSearchUrl("bilibili://search?keyword=test"))
        assertTrue(isCommentSearchUrl("BILIBILI://SEARCH?keyword=test&from=comment"))
        assertFalse(isCommentSearchUrl("bilibili://video/BV1xx411c7mD"))
        assertFalse(isCommentSearchUrl("https://search.bilibili.com"))
        assertFalse(isCommentSearchUrl("invalid-uri"))
    }
}
