package io.github.bbzq.feats.hook

import java.net.URLEncoder
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDeclaredVideoPolicyTest {
    @After
    fun tearDown() {
        AiDeclaredVideoRegistry.clear()
    }

    @Test
    fun `recognizes ai generation declarations only`() {
        assertTrue(AiDeclaredVideoPolicy.isAiDeclaration("含AI生成内容"))
        assertTrue(AiDeclaredVideoPolicy.isAiDeclaration("含 AI 辅助生成内容"))
        assertTrue(AiDeclaredVideoPolicy.isAiDeclaration("AIGC"))
        assertTrue(AiDeclaredVideoPolicy.isAiDeclaration("人工智能合成"))
        assertFalse(AiDeclaredVideoPolicy.isAiDeclaration("个人观点，仅供参考"))
        assertFalse(AiDeclaredVideoPolicy.isAiDeclaration(""))
        assertFalse(AiDeclaredVideoPolicy.isAiDeclaration(null))
    }

    @Test
    fun `creation tags match aigc segment exactly`() {
        assertTrue(AiDeclaredVideoPolicy.creationTagsDeclareAigc("人工智能-aigc,音乐-aigc"))
        assertTrue(AiDeclaredVideoPolicy.creationTagsDeclareAigc("音乐-AIGC"))
        assertFalse(AiDeclaredVideoPolicy.creationTagsDeclareAigc("人工智能-科技,音乐-翻唱"))
        assertFalse(AiDeclaredVideoPolicy.creationTagsDeclareAigc("aigcx-音乐"))
        assertFalse(AiDeclaredVideoPolicy.creationTagsDeclareAigc(null))
    }

    @Test
    fun `feed uri with aigc creation tags is detected`() {
        assertTrue(AiDeclaredVideoPolicy.feedUriDeclaresAigc(feedUri(creationTags = "人工智能-aigc")))
    }

    @Test
    fun `feed uri with aigc only in ai tags is not detected`() {
        val uri = feedUri(creationTags = "音乐-翻唱", aiTags = "aigc")
        assertFalse(AiDeclaredVideoPolicy.mayDeclareAigc(uri))
        assertFalse(AiDeclaredVideoPolicy.feedUriDeclaresAigc(uri))
    }

    @Test
    fun `malformed feed uri is kept`() {
        assertFalse(AiDeclaredVideoPolicy.feedUriDeclaresAigc("bilibili://video/1?player_preload=creation_tags%20aigc"))
        assertFalse(AiDeclaredVideoPolicy.feedUriDeclaresAigc(null))
    }

    @Test
    fun `only history spmid is passive`() {
        assertTrue(AiDeclaredVideoPolicy.isPassiveRequest("main.my-history.recommend.0"))
        assertFalse(AiDeclaredVideoPolicy.isPassiveRequest("main.ugc-video-detail.0.0"))
        assertFalse(AiDeclaredVideoPolicy.isPassiveRequest(null))
    }

    @Test
    fun `replacement skips self known ai same owner and non video routes`() {
        val known = setOf(3L)
        fun accept(candidate: AiRelateCandidate) =
            AiDeclaredVideoPolicy.isReplacementCandidate(candidate, 1L, 100L) { it in known }

        assertTrue(accept(AiRelateCandidate(true, 2L, "bilibili://video/2", 200L)))
        assertFalse(accept(AiRelateCandidate(true, 1L, "bilibili://video/1", 200L)))
        assertFalse(accept(AiRelateCandidate(true, 3L, "bilibili://video/3", 200L)))
        assertFalse(accept(AiRelateCandidate(true, 4L, "bilibili://video/4", 100L)))
        assertFalse(accept(AiRelateCandidate(true, 5L, "bilibili://live/5", 200L)))
        assertFalse(accept(AiRelateCandidate(false, 6L, "bilibili://video/6", 200L)))
    }

    @Test
    fun `aid is parsed from numeric param only`() {
        assertEquals(123L, AiDeclaredVideoPolicy.aidFromParam(" 123 "))
        assertNull(AiDeclaredVideoPolicy.aidFromParam("0"))
        assertNull(AiDeclaredVideoPolicy.aidFromParam("BV1xx"))
    }

    @Test
    fun `registry is bounded and evicts oldest`() {
        (1L..AiDeclaredVideoRegistry.MAX_AIDS + 1L).forEach { AiDeclaredVideoRegistry.add(it) }
        assertFalse(AiDeclaredVideoRegistry.contains(1L))
        assertTrue(AiDeclaredVideoRegistry.contains(AiDeclaredVideoRegistry.MAX_AIDS + 1L))
        assertFalse(AiDeclaredVideoRegistry.add(2L))
        assertFalse(AiDeclaredVideoRegistry.add(0L))
    }

    @Test
    fun `redirect guard limits chained redirects within window`() {
        var now = 0L
        val guard = AiRedirectGuard(maxRedirects = 2, windowMillis = 1_000L) { now }
        assertTrue(guard.tryAcquire())
        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        now = 1_001L
        assertTrue(guard.tryAcquire())
    }

    private fun feedUri(creationTags: String, aiTags: String = ""): String {
        val feature = "{\"ai_tags\":" + JSONObject.quote(aiTags) +
            ",\"creation_tags\":" + JSONObject.quote(creationTags) + "}"
        val preload = JSONObject().put("qn_feature", feature).toString()
        return "bilibili://video/1?player_preload=" + URLEncoder.encode(preload, "UTF-8")
    }
}
