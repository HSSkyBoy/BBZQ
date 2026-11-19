package io.github.bbzq.feats.hook

import io.github.bbzq.feats.hook.CommentAiAccountEcho.Root
import io.github.bbzq.feats.hook.CommentAiAccountPolicy.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentAiAccountEchoTest {

    private val rootText = "一米宽的画布能画出四季变化，镜头里的细节太绝了"
    private val echo = "一米宽的画布能画出四季变化真的太有意思了"

    private fun points(root: String?, reply: String) =
        CommentAiAccountEcho.points(Root.of(root), CommentAiAccountPolicy.normalize(reply))

    private fun plan(sensitivity: Sensitivity = Sensitivity.STANDARD, protect: Boolean = true, whitelist: String = "") =
        CommentAiAccountPolicy.plan(sensitivity.id, "", "", whitelist, protect)

    private fun removes(
        name: String?, level: Int?, message: String?, root: Root?, plan: CommentAiAccountPolicy.Plan = plan(),
    ) = CommentAiAccountPolicy.shouldRemove(plan, name, null, level, message, root)

    @Test
    fun verbatimRetellingScoresAndUnrelatedReplyDoesNot() {
        assertEquals(24, points(rootText, echo))
        assertEquals(0, points(rootText, "我昨天也去看了展览，人太多了根本挤不进去"))
        assertEquals(6, points(rootText, "我也想知道镜头里的细节是怎么拍出来的呢"))
    }

    @Test
    fun genericWindowsAndSingleCharacterRunsNeverCount() {
        assertTrue(CommentAiAccountEcho.GENERIC_TEXTS.all { it.length == 4 && it == it.lowercase() })
        assertEquals(0, points("这个视频真的太好看了，我看了三遍", "真的太好看了，这个视频我也很喜欢"))
        assertEquals(0, points("哈哈哈哈哈哈哈哈哈哈", "哈哈哈哈哈哈哈哈哈哈哈哈"))
        assertEquals(0, points("this is a really great video", "this is a really great video indeed"))
    }

    @Test
    fun shortTextsMentionsReplyPrefixesAndEmotesAreNotCompared() {
        assertEquals(0, points(rootText, "一米宽的画布"))
        assertEquals(0, points("太绝了太绝了", "太绝了太绝了太绝了太绝了"))
        assertEquals(0, points(rootText, "回复 @一米宽的画布能画出四季变化 :[doge]一米宽的画布"))
        assertEquals(24, points(rootText, "回复 @张三 :一米宽的画布能画出四季变化真的太有意思了"))
        assertEquals(0, points(null, echo))
        assertEquals(0, points("   ", echo))
    }

    @Test
    fun windowsDoNotSpanPunctuation() {
        assertEquals(0, points("画布能画，四季变化，镜头细节都太绝了吧", "画布能，画四季，变化镜，头细节，都太绝了"))
    }

    @Test
    fun retellingOnlyTipsBorderlineAccountOverTheLine() {
        val root = Root.of(rootText, "画家本人")
        assertEquals(36, CommentAiAccountPolicy.score(plan(), "深海大鱼君", 2, echo))
        assertEquals(60, CommentAiAccountPolicy.score(plan(), "深海大鱼君", 2, echo, root))
        assertTrue(removes("深海大鱼君", 2, echo, root))
        assertFalse(removes("深海大鱼君", 2, echo, null))
        assertEquals(14, CommentAiAccountPolicy.score(plan(), "路人甲", 2, echo, root))
        assertFalse(removes("路人甲", 2, echo, root, plan(Sensitivity.STRICT)))
        assertFalse(removes("小蓝的大肥鱼", 5, echo, root))
        assertTrue(removes("小蓝的大肥鱼", 5, echo, root, plan(protect = false)))
        assertFalse(removes("深海大鱼君", 2, echo, root, plan(whitelist = "深海大鱼君")))
    }

    @Test
    fun threadOwnerAnsweringInOwnThreadIsNeverCompared() {
        assertTrue(removes("深海大鱼君", 2, echo, Root.of(rootText, "别人")))
        assertFalse(removes("深海大鱼君", 2, echo, Root.of(rootText, "深海大鱼君")))
        assertFalse(removes("ＤＥＥＰ大鱼", 2, echo, Root.of(rootText, "deep大鱼")))
    }

    @Test
    fun rootIsReadOnlyWhenScoreLandsWithinReachOfThreshold() {
        var reads = 0
        val root = Root({ reads++; rootText }, { reads++; "别人" })
        assertFalse(removes("路人甲", 4, echo, root))
        assertTrue(removes("小蓝的大肥鱼", 2, echo, root))
        assertEquals(0, reads)
        CommentAiAccountPolicy.score(plan(), "深海大鱼君", 2, echo, root)
        CommentAiAccountPolicy.score(plan(), "深海大鱼君", 2, echo, root)
        assertEquals(2, reads)
    }

    @Test
    fun unreadableRootIsSkippedAndThrowingReaderFailsOpen() {
        assertFalse(removes("深海大鱼君", 2, echo, Root.of(null)))
        assertFalse(removes("深海大鱼君", 2, echo, Root({ error("boom") }, { error("boom") })))
        assertFalse(removes("深海大鱼君", 2, null, Root.of(rootText)))
    }

    @Test(timeout = 10_000)
    fun longRootsAndRepliesStayFast() {
        val root = Root.of("一米宽的画布能画出四季变化，".repeat(80))
        val reply = CommentAiAccountPolicy.normalize("一米宽的画布能画出四季变化".repeat(80))
        repeat(50) { CommentAiAccountEcho.points(root, reply) }
        assertEquals(24, CommentAiAccountEcho.points(root, reply))
    }
}
