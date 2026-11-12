package io.github.bbzq.feats.hook

import io.github.bbzq.feats.hook.CommentAiAccountPolicy.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentAiAccountPolicyTest {

    private fun plan(
        sensitivity: Sensitivity = Sensitivity.STANDARD,
        protectHighLevel: Boolean = true,
        nicknameWords: String = "",
        textWords: String = "",
        whitelist: String = "",
    ) = CommentAiAccountPolicy.plan(sensitivity.id, nicknameWords, textWords, whitelist, protectHighLevel)

    private fun removes(
        name: String?,
        level: Int?,
        message: String?,
        plan: CommentAiAccountPolicy.Plan = plan(),
        mid: Long? = null,
    ) = CommentAiAccountPolicy.shouldRemove(plan, name, mid, level, message)

    private fun key(raw: String) = CommentAiAccountPolicy.normalize(raw)

    @Test
    fun removesTypicalAiAccountEvenWhenTextLooksHarmless() {
        assertTrue(removes("小蓝的大肥鱼", 2, "这个视频讲得挺清楚的"))
        assertTrue(removes("小蓝的大肥鱼", 2, null))
        assertTrue(removes("普通昵称", 3, "本鲸来汇报啦（摇了摇尾巴）@阿离 主人快看"))
    }

    @Test
    fun noSingleWeakDimensionRemovesAtStandardThreshold() {
        assertFalse(removes("咸鱼爱好者", 3, "今天也是摸鱼的一天呢"))
        assertFalse(removes("鲸鱼爱好者", 2, "鲸鱼的叫声真好听，我昨天刚在纪录片里看到"))
        assertFalse(removes("路过的网友", 1, "哈哈哈哈哈哈"))
        assertFalse(removes("路过的网友", 2, "请问这个是什么歌？"))
        assertFalse(removes("肥鱼", 2, "哈哈哈哈"))
        assertFalse(removes("普通昵称", 2, "来打卡"))
        assertFalse(removes("普通昵称", 2, "让我来看看，这个我也想知道"))
        assertFalse(removes("学AI的小李", 2, "我是学AI的，来看看大家怎么说"))
    }

    @Test
    fun veteransAndWhitelistAreNeverScored() {
        val ai = "本鲸来汇报啦（摇了摇尾巴）@阿离 主人快看"
        assertFalse(removes("小蓝的大肥鱼", 5, ai))
        assertFalse(removes("小蓝的大肥鱼", 6, ai))
        assertTrue(removes("小蓝的大肥鱼", 5, ai, plan(protectHighLevel = false)))
        val allow = plan(whitelist = "小蓝的大肥鱼，10086")
        assertFalse(removes("小蓝的大肥鱼", 2, ai, allow))
        assertFalse(removes("别的名字", 2, ai, allow, mid = 10086))
        assertTrue(removes("别的名字", 2, ai, allow, mid = 10087))
        assertTrue(removes("小蓝的大肥鱼二号", 2, ai, allow, mid = 10087))
    }

    @Test
    fun whitelistWithUidKeepsCommentWhenUidUnreadable() {
        val allow = plan(whitelist = "10086")
        assertFalse(removes("小蓝的大肥鱼", 2, "本鲸来汇报啦", allow, mid = null))
        assertTrue(removes("小蓝的大肥鱼", 2, "本鲸来汇报啦", plan(whitelist = "某某")))
    }

    @Test
    fun unreadableSignalsAreNeverScored() {
        assertFalse(removes(null, null, null))
        assertFalse(removes("   ", null, "   "))
        assertEquals(45, CommentAiAccountPolicy.score(plan(), "肥鱼", null, null))
        assertFalse(removes("肥鱼", null, null))
        assertEquals(45 + 14, CommentAiAccountPolicy.score(plan(), "肥鱼", 2, null))
        assertEquals(14, CommentAiAccountPolicy.score(plan(), null, 2, null))
    }

    @Test
    fun sensitivityMovesThresholdAndBoundaryIsInclusive() {
        assertEquals(59, CommentAiAccountPolicy.score(plan(), "肥鱼", 2, null))
        assertFalse(removes("肥鱼", 2, null, plan(Sensitivity.LOOSE)))
        assertFalse(removes("肥鱼", 2, null, plan(Sensitivity.STANDARD)))
        assertTrue(removes("肥鱼", 2, null, plan(Sensitivity.STRICT)))
        assertTrue(removes("肥鱼", null, null, plan(Sensitivity.STRICT)))
        assertEquals(60, plan(Sensitivity.STANDARD).threshold)
        assertEquals(Sensitivity.STANDARD, Sensitivity.fromId("nonsense"))
        assertEquals(Sensitivity.STRICT, Sensitivity.fromId(" strict "))
        assertEquals(setOf("loose", "standard", "strict"), Sensitivity.entries.map { it.id }.toSet())
    }

    @Test
    fun nameRulesAreCappedAndFishFallbackIsSmall() {
        assertEquals(8, CommentAiAccountPolicy.nameScore("咸鱼君"))
        assertEquals(0, CommentAiAccountPolicy.nameScore("普通昵称"))
        assertEquals(55, CommentAiAccountPolicy.nameScore("深度求索鲸鱼娘"))
        assertEquals(55, CommentAiAccountPolicy.nameScore("小蓝的大肥鱼"))
        assertTrue(CommentAiAccountPolicy.nameScore("小AI助手".lowercase()) >= 28)
        assertEquals(0, CommentAiAccountPolicy.nameScore("mailbox"))
        assertEquals(0, CommentAiAccountPolicy.nameScore("rails"))
    }

    @Test
    fun levelAddsForNewAccountsAndSubtractsForVeterans() {
        assertEquals(14, CommentAiAccountPolicy.levelScore(0))
        assertEquals(14, CommentAiAccountPolicy.levelScore(2))
        assertEquals(6, CommentAiAccountPolicy.levelScore(3))
        assertEquals(0, CommentAiAccountPolicy.levelScore(4))
        assertEquals(0, CommentAiAccountPolicy.levelScore(5))
        assertEquals(-10, CommentAiAccountPolicy.levelScore(6))
        assertEquals(0, CommentAiAccountPolicy.levelScore(null))
    }

    @Test
    fun textRulesCapRepeatedHitsAndHumanReliefOnlyAppliesToWeakText() {
        assertEquals(20, CommentAiAccountPolicy.textScore(key("值得看，码住，适合新手，正好对口"), null))
        assertEquals(-20, CommentAiAccountPolicy.textScore(key("请问这个怎么办？哈哈哈哈"), null))
        assertEquals(75, CommentAiAccountPolicy.textScore(key("本鲸来汇报啦（摇了摇尾巴）哈哈哈哈"), null))
        assertEquals(58, CommentAiAccountPolicy.textScore(key("本鲸".repeat(400)), null))
    }

    @Test
    fun weakGenericPhrasesStayBelowStrongSelfReference() {
        assertEquals(50, CommentAiAccountPolicy.textScore(key("本鲸今天也很开心地来了呢"), null))
        assertTrue(CommentAiAccountPolicy.textScore(key("今天来打卡，顺便看看大家都在聊什么内容呢"), null) in 0..12)
    }

    @Test
    fun mentioningOwnerWhoseNameMatchesNicknameAddsScore() {
        assertTrue(CommentAiAccountPolicy.ownerLink("阿离的大肥鱼", "阿离酱"))
        assertTrue(CommentAiAccountPolicy.ownerLink("相同名字", "相同名字"))
        assertFalse(CommentAiAccountPolicy.ownerLink("阿离的大肥鱼", "路人甲"))
        assertFalse(CommentAiAccountPolicy.ownerLink("大肥鱼", "大肥鱼的朋友"))
        assertTrue(CommentAiAccountPolicy.ownerLink("阿的鱼", "阿离"))
        assertFalse(CommentAiAccountPolicy.ownerLink("a的鱼", "banana"))
        val linked = CommentAiAccountPolicy.textScore(key("这个视频真不错 @阿离酱"), key("阿离的大肥鱼"))
        val unlinked = CommentAiAccountPolicy.textScore(key("这个视频真不错 @路人甲"), key("阿离的大肥鱼"))
        assertEquals(25, linked - unlinked)
    }

    @Test
    fun leadingMentionIsScoredButReplyPrefixIsNot() {
        val bare = CommentAiAccountPolicy.textScore("@张三", null)
        val longPitch = CommentAiAccountPolicy.textScore("@张三 这个视频整理得很完整，建议收藏", null)
        val reply = CommentAiAccountPolicy.textScore("回复 @张三 :这个视频整理得很完整，建议收藏", null)
        assertEquals(15 - 8, bare)
        assertTrue(longPitch >= 20)
        assertTrue(reply < longPitch)
    }

    @Test
    fun invisibleAndFullwidthTricksDoNotHideAiAccount() {
        assertTrue(removes("Ｄｅｅｐ\u200BＳｅｅｋ助手", 2, null))
        assertEquals(50, CommentAiAccountPolicy.textScore(key("本\u200B鲸来了"), null))
        assertEquals(50, CommentAiAccountPolicy.textScore(key("本鲸来了"), null))
    }

    @Test
    fun customWordsScoreAsStrongEvidenceButNeverRemoveVeteransAlone() {
        val custom = plan(nicknameWords = "鲨鱼酱", textWords = "主人安好")
        assertTrue(removes("深海的鲨鱼酱", 2, null, custom))
        assertFalse(removes("深海的鲨鱼酱", 4, null, custom))
        assertTrue(removes("路人", 2, "主人安好，今天也在认真刷视频", custom))
        assertFalse(removes("路人", 6, "主人安好，今天也在认真刷视频", custom))
        assertFalse(removes("深海的鲨鱼酱", 2, null, plan()))
    }

    @Test
    fun customWordsNeedTwoCharactersAndAreBounded() {
        assertEquals(setOf("鲨鱼", "ab"), CommentAiAccountPolicy.customWords("鲸，a，鲨鱼；AB\n"))
        assertTrue(CommentAiAccountPolicy.customWords("鲸,a, ").isEmpty())
        val many = (1..200).joinToString(",") { "词语$it" }
        assertEquals(CommentAiAccountPolicy.MAX_CUSTOM_WORDS, CommentAiAccountPolicy.customWords(many).size)
    }

    @Test
    fun settingsBecomeImmutablePlanAndAreCached() {
        val strict = CommentAiAccountPolicy.plan("strict", "鲨鱼酱", "主人安好", "10086,某某", false)
        assertEquals(45, strict.threshold)
        assertFalse(strict.protectHighLevel)
        assertEquals(setOf("鲨鱼酱"), strict.nicknameWords)
        assertEquals(setOf("主人安好"), strict.textWords)
        assertEquals(setOf(10086L), strict.whitelistMids)
        assertEquals(setOf("某某"), strict.whitelistNames)
        assertSame(strict, CommentAiAccountPolicy.plan("strict", "鲨鱼酱", "主人安好", "10086,某某", false))
        assertEquals(60, CommentAiAccountPolicy.plan("", "", "", "", true).threshold)
        assertEquals(60, CommentAiAccountPolicy.plan("garbage", "", "", "", true).threshold)
    }

    @Test(timeout = 10_000)
    fun longAdversarialTextStaysLinear() {
        val plan = plan()
        val text = "（" + "尾巴".repeat(2_000) + "@" + "a".repeat(5_000) + "：、" + "从到".repeat(1_000)
        repeat(20) { removes("小蓝的大肥鱼", 2, text, plan) }
    }
}
