package io.github.bbzq.feats.hook

internal object CommentAiAccountEcho {
    const val MAX_POINTS = 24
    const val MIN_LETTERS = 8

    private const val WINDOW = 4
    private const val RUN_MEDIUM = 5
    private const val RUN_LONG = 7
    private const val RUN_VERBATIM = 10

    private val LEADING_REPLY = Regex("^\\s*回复\\s*@[^\\s@:：，,]{1,24}\\s*[:：]?")

    internal val GENERIC_TEXTS = listOf(
        "这个视频", "视频里的", "视频真的", "这个东西", "这个up", "up主的", "up主真", "什么意思", "什么时候",
        "怎么回事", "为什么要", "有没有人", "不知道为", "我觉得这", "我也觉得", "真的太好", "真的很好",
        "太好看了", "太好笑了", "谢谢大佬", "感谢分享", "第一次看", "一直在看",
    )
    private val GENERIC_WINDOWS: LongArray = GENERIC_TEXTS.map(::packString).sorted().toLongArray()

    class Root(private val readText: () -> String?, private val readName: () -> String? = { null }) {
        @Volatile private var grams: Any? = null
        @Volatile private var name: Any? = null

        internal fun windows(): LongArray? {
            val cached = grams
            if (cached != null) return cached as? LongArray
            val built = runCatching { readText() }.getOrNull()
                ?.takeIf(String::isNotBlank)?.let(CommentAiAccountPolicy::normalize)
                ?.let(::indexOf)
            grams = built ?: ABSENT
            return built
        }

        internal fun authorName(): String? {
            val cached = name
            if (cached != null) return cached as? String
            val built = runCatching { readName() }.getOrNull()
                ?.takeIf(String::isNotBlank)?.let(CommentAiAccountPolicy::normalize)
            name = built ?: ABSENT
            return built
        }

        companion object {
            private val ABSENT = Any()

            fun of(text: String?, authorName: String? = null) = Root({ text }, { authorName })
        }
    }

    fun points(root: Root, reply: String): Int {
        val index = root.windows() ?: return 0
        val text = clean(reply)
        val letters = letterCount(text)
        if (letters < MIN_LETTERS) return 0
        var covered = 0
        var coveredEnd = 0
        var run = 0
        var longest = 0
        for (start in 0..text.length - WINDOW) {
            val key = windowKey(text, start)
            if (key != 0L && java.util.Arrays.binarySearch(index, key) >= 0) {
                run += 1
                if (run > longest) longest = run
                covered += start + WINDOW - maxOf(start, coveredEnd)
                coveredEnd = start + WINDOW
            } else {
                run = 0
            }
        }
        if (longest == 0) return 0
        val verbatim = longest + WINDOW - 1
        var points = when {
            verbatim >= RUN_VERBATIM -> 16
            verbatim >= RUN_LONG -> 12
            verbatim >= RUN_MEDIUM -> 6
            else -> 0
        }
        if (covered >= MIN_LETTERS && covered * 2 >= letters) points += 8
        return minOf(MAX_POINTS, points)
    }

    private fun indexOf(rootKey: String): LongArray? {
        val text = clean(rootKey)
        if (letterCount(text) < MIN_LETTERS) return null
        val keys = LongArray(maxOf(0, text.length - WINDOW + 1))
        var size = 0
        for (start in keys.indices) {
            val key = windowKey(text, start)
            if (key != 0L) keys[size++] = key
        }
        if (size == 0) return null
        val sorted = keys.copyOf(size).also { it.sort() }
        var unique = 1
        for (i in 1 until sorted.size) if (sorted[i] != sorted[unique - 1]) sorted[unique++] = sorted[i]
        return sorted.copyOf(unique)
    }

    private fun clean(text: String): String {
        var out = LEADING_REPLY.replace(text, "")
        out = CommentAiAccountPolicy.MENTION.replace(out, " ")
        return CommentAiAccountPolicy.EMOTE_PLACEHOLDER.replace(out, " ")
    }

    private fun letterCount(text: String): Int {
        var count = 0
        for (ch in text) if (Character.isLetterOrDigit(ch)) count++
        return count
    }

    private fun windowKey(text: String, start: Int): Long {
        val a = text[start]
        val b = text[start + 1]
        val c = text[start + 2]
        val d = text[start + 3]
        if (!Character.isLetterOrDigit(a) || !Character.isLetterOrDigit(b) ||
            !Character.isLetterOrDigit(c) || !Character.isLetterOrDigit(d)
        ) return 0L
        if (a.code < 0x80 && b.code < 0x80 && c.code < 0x80 && d.code < 0x80) return 0L
        var distinct = 1
        if (b != a) distinct++
        if (c != a && c != b) distinct++
        if (d != a && d != b && d != c) distinct++
        if (distinct < 3) return 0L
        val key = pack(a, b, c, d)
        return if (java.util.Arrays.binarySearch(GENERIC_WINDOWS, key) >= 0) 0L else key
    }

    private fun pack(a: Char, b: Char, c: Char, d: Char): Long =
        (a.code.toLong() shl 48) or (b.code.toLong() shl 32) or (c.code.toLong() shl 16) or d.code.toLong()

    private fun packString(window: String): Long = pack(window[0], window[1], window[2], window[3])
}
