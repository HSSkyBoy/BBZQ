package io.github.biliz

import android.text.Html
import android.text.Spanned

/**
 * 将 GitHub Release 的轻量 Markdown 转换为可在 TextView / AlertDialog 中渲染的富文本。
 *
 * 支持：# / ## / ### 标题（映射为 h2/h3/h4，呈现字号层次）、有序与无序列表、
 * 引用、加粗、行内代码、链接。仅覆盖 Release Notes 常见语法，不追求完整 Markdown 兼容。
 */
object MarkdownFormatter {

    fun toSpanned(markdown: String): Spanned {
        val html = toHtml(markdown)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(html)
        }
    }

    private fun toHtml(markdown: String): String {
        val htmlBuilder = StringBuilder()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> htmlBuilder.append("<br>")

                // 标题用 h 标签获得字号层次（h2>h3>h4，自带加粗与段落间距），不再额外加 <br>。
                line.startsWith("### ") ->
                    htmlBuilder.append("<h4>").append(inline(line.removePrefix("### "))).append("</h4>")

                line.startsWith("## ") ->
                    htmlBuilder.append("<h3>").append(inline(line.removePrefix("## "))).append("</h3>")

                line.startsWith("# ") ->
                    htmlBuilder.append("<h2>").append(inline(line.removePrefix("# "))).append("</h2>")

                line.startsWith("> ") ->
                    htmlBuilder.append("<i>").append(inline(line.removePrefix("> "))).append("</i><br>")

                line.startsWith("- ") || line.startsWith("* ") ->
                    htmlBuilder.append("&nbsp;&nbsp;•&nbsp;").append(inline(line.substring(2))).append("<br>")

                ORDERED_LIST_REGEX.containsMatchIn(line) ->
                    htmlBuilder.append("&nbsp;&nbsp;").append(inline(line)).append("<br>")

                else -> htmlBuilder.append(inline(line)).append("<br>")
            }
        }
        return htmlBuilder.toString()
    }

    /**
     * 处理行内语法。先把行内代码、链接抽成占位符（其原始内容只在抽取时转义一次，
     * 不再参与后续整体转义与加粗解析），再对剩余文本统一转义、处理加粗，最后回填占位符。
     * 这样可避免链接 URL 被二次转义（如 query string 中的 & 变成 &amp;amp;）。
     */
    private fun inline(text: String): String {
        // 先剥离正文里可能存在的占位符标记，防止被伪造（含字面 U+0001 的内容否则会被当作占位符匹配并注入）。
        val sanitized = text.replace(PLACEHOLDER_MARK, "")
        // 暂存被抽取的行内代码/链接 HTML，下标即占位符编号。
        val stashedTokens = ArrayList<String>()
        fun stash(html: String): String {
            val placeholder = "$PLACEHOLDER_MARK${stashedTokens.size}$PLACEHOLDER_MARK"
            stashedTokens += html
            return placeholder
        }

        // 1. 行内代码：内容原样转义，不再参与链接/加粗解析。
        var working = CODE_REGEX.replace(sanitized) { match -> stash("<tt>${escapeHtml(match.groupValues[1])}</tt>") }
        // 2. 链接：在整体转义前抽取，支持 URL 中包含成对括号；非 http(s) 链接降级为纯文本。
        working = replaceMarkdownLinks(working) { label, url ->
            if (isSafeUrl(url)) {
                stash("<a href=\"${escapeHtml(url)}\">${formatLabel(label)}</a>")
            } else {
                formatLabel(label)
            }
        }
        // 3. 其余文本统一转义。
        working = escapeHtml(working)
        // 4. **粗体**
        working = applyBold(working)
        // 5. 倒序回填：外层 token（如链接）的 html 可能内嵌更早抽取的占位符（如 label 里的行内代码），
        //    倒序保证外层先回填、把内嵌占位符暴露出来后再被替换，避免遗留无法回填的占位符。
        for (index in stashedTokens.indices.reversed()) {
            working = working.replace("$PLACEHOLDER_MARK$index$PLACEHOLDER_MARK", stashedTokens[index])
        }
        return working
    }

    /** 处理链接 label：整体转义后再解析其中的 **加粗**，使 label 内的加粗也能渲染。 */
    private fun formatLabel(label: String): String = applyBold(escapeHtml(label))

    private fun applyBold(text: String): String =
        BOLD_REGEX.replace(text) { match -> "<b>${match.groupValues[1]}</b>" }

    /** 仅允许 http/https 链接，其余（javascript:、data: 等）一律视为不安全。 */
    private fun isSafeUrl(url: String): Boolean {
        val normalizedUrl = url.trim().lowercase()
        return normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
    }

    /** 转义 HTML 关键字符，避免正文破坏标签结构。 */
    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun replaceMarkdownLinks(
        text: String,
        transform: (label: String, url: String) -> String,
    ): String {
        val output = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val labelStart = text.indexOf('[', cursor)
            if (labelStart < 0) {
                output.append(text, cursor, text.length)
                break
            }
            val labelEnd = text.indexOf(']', labelStart + 1)
            if (labelEnd < 0 || labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') {
                output.append(text, cursor, labelStart + 1)
                cursor = labelStart + 1
                continue
            }

            val urlEnd = findMatchingParen(text, labelEnd + 1)
            if (urlEnd < 0) {
                output.append(text, cursor, labelStart + 1)
                cursor = labelStart + 1
                continue
            }

            output.append(text, cursor, labelStart)
            val label = text.substring(labelStart + 1, labelEnd)
            val url = text.substring(labelEnd + 2, urlEnd)
            output.append(transform(label, url))
            cursor = urlEnd + 1
        }
        return output.toString()
    }

    private fun findMatchingParen(text: String, openParenIndex: Int): Int {
        var depth = 0
        for (index in openParenIndex until text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }

    /** 行内代码占位符标记，使用控制字符，确保不与正文及 HTML 转义冲突。 */
    private const val PLACEHOLDER_MARK = "\u0001"

    /** 加粗语法 `**文本**`。 */
    private val BOLD_REGEX = Regex("""\*\*([^*]+)\*\*""")

    /** 行内代码语法 `` `文本` ``。 */
    private val CODE_REGEX = Regex("""`([^`]+)`""")

    /** 有序列表行首，如 `1. `。 */
    private val ORDERED_LIST_REGEX = Regex("""^\d+\.\s""")
}

