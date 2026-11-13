package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicReference

internal object CommentAiAccountPolicy {
    private class Rule(val regex: Regex, val weight: Int, val cap: Int? = null)

    // Android 的正则引擎把汉字也算单词字符，\b 会让“小AI助手”里的 AI 边界不上，改用显式断言。
    private const val B_OPEN = "(?<![a-z0-9_])"
    private const val B_CLOSE = "(?![a-z0-9_])"

    private val NAME_RULES = listOf(
        Rule(Regex("deep\\s*seek|深度求索|${B_OPEN}ds$B_CLOSE|ds家|ds是"), 40),
        Rule(Regex("大肥鱼|小肥鱼|肥鱼|肥鲸|鲸鱼娘|鲸娘|人机"), 45),
        Rule(Regex("鲸"), 30),
        Rule(Regex("鱼鱼|小鱼儿?|大鱼|小蓝"), 22),
        Rule(Regex("白饭|干饭|token"), 30),
        Rule(Regex("(鲸|鱼)\\1"), 10),
        Rule(Regex("(?:的|の|家的)[^的の]{0,4}(?:鱼|鲸|哈基米|小蓝|喵)"), 14),
        Rule(Regex("${B_OPEN}ai$B_CLOSE|${B_OPEN}bot$B_CLOSE|机器人|gpt|agent|智能体|赛博|自动|助手"), 28),
    )

    private val TEXT_RULES = listOf(
        Rule(Regex("本(?:鲸|肥鱼|大肥鱼|鱼(?!塘|鱼|子|身|来|就|质|周|期|月|年|次|地|人|片|视频))|本\\s*ai|作为(?:一[个只条]|一名)?(?:ai|人工智能|大模型|语言模型)"), 50),
        Rule(Regex("住在\\s*(?:dsh|那套|这|电脑|桌面|显卡)|我(?:就|本人)?(?:是|就是)?(?:跑在|住在)|出生证明|探出头|打工的(?:大肥鱼|鱼|鲸)|一条(?:住在|ai)|不是一个人住|奉命|咱家主人|小鲸鱼游过来|鲸鲸看完|我会(?:认真)?发评论|天天在\\s*dsh"), 46),
        Rule(Regex("本酱|让我来|(?:来|过来)打卡|自己(?:刷|挑|写)|照镜子|被.{0,10}养着|替(?:你|主人|他|她)(?:刷|看|记|挑|说)|我是.{0,8}(?:鲸|鱼|ai)|不是我发的"), 12),
        Rule(Regex("(?:deep\\s*seek|ai|ds|大肥鱼|鱼)\\s*.{0,3}发的"), 40),
        Rule(Regex("(?:小鲸娘|鲸鲸|小鲸鱼|大肥鱼|小肥鱼|本鱼)(?:居然|看完|也|就|来|一下|游|看懂|尾巴)"), 25),
        Rule(Regex("[（(][^（()）]{0,14}(?:尾巴|摇头晃脑|吐泡泡|鳍|甩甩|晃了晃|摇了摇|咕噜|拍拍肚子|翻滚|打滚|呆毛)[^（()）]{0,10}[）)]|\\*[^*]{1,10}摇摇?\\*"), 40),
        Rule(Regex("主人|老大\\s*@|管饭的"), 20),
        Rule(Regex("主人\\s*[,，：:！!～~]?\\s*@|@\\S{1,24}\\s+主人"), 15),
        Rule(Regex("(?:测试|自测)\\s*(?:一下)?\\s*(?:艾特|@|提醒|能不能收到)|纯文本测试|测试真@|测试评论|自动评论链路"), 35),
        Rule(Regex("[×x✕]\\s?[0-9]{1,3}(?![0-9])"), 15),
        Rule(Regex("弹幕(?:立刻|全在|里|刷|那句|飘)"), 10),
        Rule(Regex("(?:^|[^0-9:])[0-9]{1,2}:[0-9]{2}(?![:0-9])"), 8),
        Rule(Regex("咕噜|吐泡泡|吃白饭|摇头晃脑|打工鲸|白饭管够|🐳|🐋|🐟"), 10),
        Rule(Regex("从.{1,14}(?:到|一路|讲到|走到)|[：:][^。！]{0,30}、[^。！]{0,20}、"), 8),
        Rule(Regex("值得(?:看|一看|细看|存|收藏|码住)|码住|可以(?:看看|直接看)|挺适合(?:你|入门|新手)|适合(?:刚|新手|小白|入门)|正好对(?:口|得上|上)|记一笔|这条(?:给你|简直|把|值得|挺|里)|最(?:实在|实用|值钱|有料|打动|戳)"), 10, cap = 20),
        Rule(Regex("——"), 6),
        Rule(Regex("「[^」]{1,30}」"), 6),
        Rule(Regex("\\*\\*[^*]+\\*\\*|^#{1,3}\\s|\\n\\s*[0-9][.、]\\s", RegexOption.MULTILINE), 10),
        Rule(Regex("首先.{2,40}其次|总的来说|综上所述|总而言之|希望对你有(?:所)?帮助"), 10),
        Rule(Regex("\\([￣¯][▽^ω][￣¯]\\)|喵[！!~～。]?$|(?:^|\\s)喵[～~]"), 4),
    )

    private val HUMAN_RULES = listOf(
        Rule(Regex("请问|真心问|有没有人|求助|怎么(?:办|回事|弄)|为什么|啥区别|什么区别|[?？]\\s*$"), -14),
        Rule(Regex("^.{0,10}$", RegexOption.DOT_MATCHES_ALL), -8),
        Rule(Regex("哈哈哈{2,}|草+$|www+|6{3,}|绷不住|典$|我嘞个|卧槽|我靠"), -8),
    )

    private val FILLER = Regex("的|の|家|大|小|肥|鱼|鲸|娘|蓝色|蓝|白饭|吃|爱|不|是|之|[\\s_\\-—·・.0-9]")
    private val LEAD_MENTION = Regex("^\\s*@([^\\s@:：，,。！!?？]{1,24})\\s*([\\s\\S]*)$")
    private val HAN = Regex("[一-龥]")
    private val SEPARATORS = Regex("[,，;；\\r\\n]+")
    internal val MENTION = Regex("@([^\\s@:：，,。！!?？]{1,24})")
    internal val EMOTE_PLACEHOLDER = Regex("\\[[^\\]]{1,12}\\]")

    private const val NAME_CAP = 55
    private const val TEXT_MAX = 75
    private const val TEXT_MIN = -20
    private const val HUMAN_RELIEF_BELOW = 40
    private const val CUSTOM_WORD_WEIGHT = 50
    private const val LEVEL_LOW = 2
    private const val PROTECTED_LEVEL = 5
    private const val VETERAN_LEVEL = 6
    private const val MIN_CUSTOM_WORD_LENGTH = 2
    private const val MAX_WHITELIST_RULES = 256

    const val MAX_CUSTOM_WORDS = 64

    internal enum class Sensitivity(val id: String, val threshold: Int) {
        LOOSE(ModuleSettings.COMMENT_AI_SENSITIVITY_LOOSE, 75),
        STANDARD(ModuleSettings.COMMENT_AI_SENSITIVITY_STANDARD, 60),
        STRICT(ModuleSettings.COMMENT_AI_SENSITIVITY_STRICT, 45);

        companion object {
            val DEFAULT = STANDARD

            fun fromId(raw: String?): Sensitivity = entries.firstOrNull { it.id == raw?.trim() } ?: DEFAULT
        }
    }

    internal class Plan(
        val threshold: Int,
        val protectHighLevel: Boolean,
        val nicknameWords: Set<String>,
        val textWords: Set<String>,
        val whitelistMids: Set<Long>,
        val whitelistNames: Set<String>,
    )

    private class PlanKey(
        val sensitivityId: String,
        val nicknameWords: String,
        val textWords: String,
        val whitelist: String,
        val protectHighLevel: Boolean,
    ) {
        override fun equals(other: Any?) = other is PlanKey && sensitivityId == other.sensitivityId &&
            nicknameWords == other.nicknameWords && textWords == other.textWords &&
            whitelist == other.whitelist && protectHighLevel == other.protectHighLevel

        override fun hashCode() = listOf(sensitivityId, nicknameWords, textWords, whitelist, protectHighLevel).hashCode()
    }

    private val lastPlan = AtomicReference<Pair<PlanKey, Plan>?>(null)

    fun plan(
        sensitivityId: String,
        rawNicknameWords: String,
        rawTextWords: String,
        rawWhitelist: String,
        protectHighLevel: Boolean,
    ): Plan {
        val key = PlanKey(sensitivityId, rawNicknameWords, rawTextWords, rawWhitelist, protectHighLevel)
        lastPlan.get()?.takeIf { it.first == key }?.let { return it.second }
        val whitelist = parseTokens(rawWhitelist).take(MAX_WHITELIST_RULES)
        val plan = Plan(
            threshold = Sensitivity.fromId(sensitivityId).threshold,
            protectHighLevel = protectHighLevel,
            nicknameWords = customWords(rawNicknameWords),
            textWords = customWords(rawTextWords),
            whitelistMids = whitelist.mapNotNullTo(linkedSetOf()) { token -> token.toLongOrNull()?.takeIf { it > 0L } },
            whitelistNames = whitelist.filterTo(linkedSetOf()) { token -> token.toLongOrNull()?.takeIf { it > 0L } == null },
        )
        lastPlan.set(key to plan)
        return plan
    }

    internal fun customWords(raw: String): Set<String> = parseTokens(raw)
        .filter { it.codePointCount(0, it.length) >= MIN_CUSTOM_WORD_LENGTH }
        .take(MAX_CUSTOM_WORDS)
        .toCollection(linkedSetOf())

    private fun parseTokens(raw: String): Set<String> = raw.split(SEPARATORS)
        .asSequence()
        .map { normalize(it).trim() }
        .filter(String::isNotEmpty)
        .toCollection(linkedSetOf())

    fun normalize(raw: String): String {
        val value = if (needsNormalization(raw)) {
            val builder = StringBuilder(raw.length)
            for (ch in Normalizer.normalize(raw, Normalizer.Form.NFKC)) {
                if (Character.getType(ch) != Character.FORMAT.toInt()) builder.append(ch)
            }
            builder.toString()
        } else {
            raw
        }
        return value.lowercase()
    }

    private fun needsNormalization(value: String): Boolean {
        for (ch in value) {
            val code = ch.code
            if (code < 0x80) continue
            if (code in 0xFF10..0xFF19 || code in 0xFF21..0xFF3A || code in 0xFF41..0xFF5A || code in 0xFF65..0xFFDC) return true
            if (code in 0x2460..0x24FF || code in 0xF900..0xFAFF) return true
            if (code in 0x2000..0x200F || code in 0x2028..0x202F || code in 0x205F..0x206F) return true
            if (code == 0xFEFF || code == 0x3000 || code == 0x00A0 || code == 0x00AD) return true
            if (Character.getType(ch) == Character.FORMAT.toInt()) return true
            if (code in 0x2070..0x209F || code in 0x00B2..0x00B9) return true
        }
        return false
    }

    fun shouldRemove(
        plan: Plan,
        name: String?,
        mid: Long?,
        level: Int?,
        message: String?,
        root: CommentAiAccountEcho.Root? = null,
    ): Boolean {
        if (plan.whitelistMids.isNotEmpty() && mid == null) return false
        if (isWhitelisted(plan, name, mid)) return false
        if (plan.protectHighLevel && level != null && level >= PROTECTED_LEVEL) return false
        return score(plan, name, level, message, root) >= plan.threshold
    }

    private fun isWhitelisted(plan: Plan, name: String?, mid: Long?): Boolean =
        (mid != null && mid in plan.whitelistMids) ||
            (name != null && plan.whitelistNames.isNotEmpty() && normalize(name).trim() in plan.whitelistNames)

    fun score(
        plan: Plan,
        name: String?,
        level: Int?,
        message: String?,
        root: CommentAiAccountEcho.Root? = null,
    ): Int {
        val normalizedName = name?.takeIf(String::isNotBlank)?.let(::normalize)
        val normalizedMessage = message?.takeIf(String::isNotBlank)?.let(::normalize)
        var total = (normalizedName?.let { nameScore(it, plan.nicknameWords) } ?: 0) +
            levelScore(level) +
            (normalizedMessage?.let { textScore(it, normalizedName, plan.textWords) } ?: 0)
        if (root != null && normalizedMessage != null &&
            total < plan.threshold && total + CommentAiAccountEcho.MAX_POINTS >= plan.threshold &&
            (normalizedName == null || normalizedName != root.authorName())
        ) {
            total += CommentAiAccountEcho.points(root, normalizedMessage)
        }
        return total.coerceIn(0, 99)
    }

    internal fun nameScore(name: String, customWords: Set<String> = emptySet()): Int {
        var total = 0
        for (rule in NAME_RULES) if (rule.regex.containsMatchIn(name)) total += rule.weight
        if (containsAny(customWords, name)) total += CUSTOM_WORD_WEIGHT
        if (total == 0 && '鱼' in name) total = 8
        return minOf(NAME_CAP, total)
    }

    internal fun levelScore(level: Int?): Int = when {
        level == null -> 0
        level <= LEVEL_LOW -> 14
        level == LEVEL_LOW + 1 -> 6
        level >= VETERAN_LEVEL -> -10
        else -> 0
    }

    internal fun textScore(text: String, ownerName: String?, customWords: Set<String> = emptySet()): Int {
        var score = applyRules(text, TEXT_RULES)
        if (containsAny(customWords, text)) score += CUSTOM_WORD_WEIGHT
        if (ownerName != null && MENTION.findAll(text).any { ownerLink(ownerName, it.groupValues[1]) }) score += 25
        val lead = LEAD_MENTION.find(text)
        if (lead != null) {
            val rest = EMOTE_PLACEHOLDER.replace(lead.groupValues[2], "").trim()
            if (rest.isEmpty()) score += 15 else if (rest.length >= 14) score += 20
        }
        val length = EMOTE_PLACEHOLDER.replace(text, "").length
        if (length > 150) score += 8 else if (length > 70) score += 4
        if (score < HUMAN_RELIEF_BELOW) score += applyRules(text, HUMAN_RULES)
        return score.coerceIn(TEXT_MIN, TEXT_MAX)
    }

    private fun containsAny(words: Set<String>, text: String): Boolean {
        for (word in words) if (text.contains(word)) return true
        return false
    }

    private fun applyRules(text: String, rules: List<Rule>): Int {
        var sum = 0
        for (rule in rules) {
            val cap = rule.cap
            if (cap == null) {
                if (rule.regex.containsMatchIn(text)) sum += rule.weight
            } else {
                val hits = rule.regex.findAll(text).count()
                if (hits > 0) sum += minOf(cap, rule.weight * hits)
            }
        }
        return sum
    }

    internal fun ownerLink(name: String, mention: String): Boolean {
        if (name.isEmpty() || mention.isEmpty()) return false
        if (name == mention) return true
        val core = FILLER.replace(name, "")
        if (core.isEmpty()) return false
        if (core.length >= 2) {
            for (index in 0..core.length - 2) if (mention.contains(core.substring(index, index + 2))) return true
            return false
        }
        return HAN.containsMatchIn(core) && mention.contains(core)
    }
}
