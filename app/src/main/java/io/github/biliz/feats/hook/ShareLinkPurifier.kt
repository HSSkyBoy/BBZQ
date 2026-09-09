package io.github.biliz.feats.hook

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal object ShareLinkPurifier {
    fun purifyText(text: String, transformAv: Boolean): String =
        URL_REGEX.replace(text) { match ->
            val raw = match.value
            val suffix = raw.takeLastWhile { it in TRAILING_PUNCTUATION }
            val url = raw.dropLast(suffix.length)
            purifyLink(url, transformAv) + suffix
        }

    fun purifyLink(url: String, transformAv: Boolean): String {
        val normalized = url.trim()
        if (normalized.isEmpty()) return url
        val resolved = resolveShortLink(normalized)
        return transformUrl(resolved, transformAv)
    }

    private fun resolveShortLink(url: String): String {
        if (!url.isBilibiliShortLink()) return url
        val requestUrl = url.withoutQueryAndFragment()
        return runCatching {
            val conn = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = REDIRECT_TIMEOUT_MS
                readTimeout = REDIRECT_TIMEOUT_MS
            }
            try {
                conn.connect()
                val location = if (conn.responseCode in REDIRECT_STATUS_CODES) {
                    conn.getHeaderField("Location")
                } else {
                    null
                }
                location?.toAbsoluteUrl(conn.url)?.takeIf { it.isNotBlank() } ?: requestUrl
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(requestUrl)
    }

    private fun transformUrl(url: String, transformAv: Boolean): String {
        val target = runCatching { Uri.parse(url) }.getOrNull() ?: return url
        val host = target.host?.lowercase(Locale.ROOT).orEmpty()
        if (!host.isBilibiliHost()) return url

        val bv = target.pathSegments.firstOrNull { it.isBvId() }
        val av = bv?.takeIf { transformAv }?.let { "av${bv2av(it)}" }
        val builder = target.buildUpon()
            .clearQuery()
            .fragment(null)

        if (av != null) {
            builder.encodedPath(target.encodedPath.orEmpty().replace(bv, av))
        }

        appendAllowedQuery(target, builder)
        builder.appendQueryParameter(UNIQUE_KEY, BILIZ_UNIQUE_KEY)
        return builder.build().toString()
    }

    private fun appendAllowedQuery(source: Uri, target: Uri.Builder) {
        val appended = linkedSetOf<String>()
        KEEP_QUERY_KEYS.forEach { key ->
            source.getQueryParameter(key)
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    target.appendQueryParameter(key, value)
                    appended += key
                }
        }

        source.getQueryParameter(START_PROGRESS)
            ?.takeIf { it.isNotBlank() }
            ?.let { progress ->
                target.appendQueryParameter(START_PROGRESS, progress)
                if (TIME !in appended) {
                    progress.toLongOrNull()
                        ?.let { target.appendQueryParameter(TIME, (it / 1000L).toString()) }
                }
            }
    }

    private fun String.isBilibiliShortLink(): Boolean {
        val host = runCatching { Uri.parse(this).host?.lowercase(Locale.ROOT) }.getOrNull()
        return host == "b23.tv" || host == "bili2233.cn"
    }

    private fun String.withoutQueryAndFragment(): String =
        runCatching { Uri.parse(this).buildUpon().query(null).fragment(null).build().toString() }
            .getOrDefault(this)

    private fun String.toAbsoluteUrl(base: URL): String =
        runCatching { URL(base, this).toString() }.getOrDefault(this)

    private fun String.isBilibiliHost(): Boolean =
        this == "bilibili.com" || endsWith(".bilibili.com")

    private fun String.isBvId(): Boolean =
        length == BV_ID_LENGTH && startsWith("BV") && all { it.isLetterOrDigit() }

    private fun bv2av(bv: String): Long {
        var result = 0L
        BV_POSITIONS.forEachIndexed { index, position ->
            result += (BV_TABLE[bv[position]] ?: 0) * pow58(index)
        }
        return result.and(BV_MASK).xor(BV_XOR)
    }

    private fun pow58(index: Int): Long {
        var result = 1L
        repeat(index) { result *= 58L }
        return result
    }

    private const val REDIRECT_TIMEOUT_MS = 5000
    private const val BV_ID_LENGTH = 12
    private const val BV_MASK = 2251799813685247L
    private const val BV_XOR = 23442827791579L
    private const val UNIQUE_KEY = "unique_k"
    private const val BILIZ_UNIQUE_KEY = "2333"
    private const val START_PROGRESS = "start_progress"
    private const val TIME = "t"

    private val URL_REGEX = Regex("""https?://\S+""")
    private val KEEP_QUERY_KEYS = listOf("p", TIME)
    private val REDIRECT_STATUS_CODES = setOf(
        HttpURLConnection.HTTP_MOVED_PERM,
        HttpURLConnection.HTTP_MOVED_TEMP,
        HttpURLConnection.HTTP_SEE_OTHER,
        307,
        308,
    )
    private val TRAILING_PUNCTUATION = setOf(
        ')',
        ']',
        '}',
        '>',
        ',',
        '.',
        ';',
        ':',
        '!',
        '?',
        '，',
        '。',
        '；',
        '：',
        '！',
        '？',
        '、',
        '「',
        '」',
        '（',
        '）',
    )
    private val BV_POSITIONS = intArrayOf(11, 10, 3, 8, 4, 6, 5, 7, 9)
    private val BV_TABLE = HashMap<Char, Int>().apply {
        "FcwAPNKTMug3GV5Lj7EJnHpWsx4tb8haYeviqBz6rkCy12mUSDQX9RdoZf"
            .forEachIndexed { index, char -> this[char] = index }
    }
}

