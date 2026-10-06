package io.github.bbzq.feats.hook

/** Pure decisions for replaying a refused PGC HTTP request on the resolver server. */
internal object SeaBangumiDetailPolicy {
    /** PGC endpoints the host calls over HTTP for season detail and related data. */
    private val PGC_PREFIXES = listOf("/pgc/view/", "/pgc/season/", "/pgc/player/")

    private val CODE = Regex("\"code\"\\s*:\\s*(-?\\d+)")
    private val MESSAGE = Regex("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Bytes of a response worth peeking: a refusal is a tiny JSON object, a season is megabytes. */
    const val PEEK_BYTES = 2048L

    fun isPgcPath(encodedPath: String): Boolean = PGC_PREFIXES.any { encodedPath.startsWith(it) }

    /** True when the start of a JSON response is a region refusal rather than data. */
    fun isRegionRefusal(prefix: String): Boolean {
        val code = CODE.find(prefix)?.groupValues?.get(1)?.toIntOrNull() ?: return false
        if (code == 0) return false
        val message = MESSAGE.find(prefix)?.groupValues?.get(1)?.let(::unescape)
        return SeaBangumiPlayCodec.isRegionBlocked(code, message)
    }

    /** The same path and query on the resolver, so the host's own signature stays valid. */
    fun replayUrl(baseUrl: String, encodedPath: String, encodedQuery: String?): String =
        buildString {
            append(baseUrl.trimEnd('/'))
            append(encodedPath)
            if (!encodedQuery.isNullOrEmpty()) append('?').append(encodedQuery)
        }

    private fun unescape(raw: String): String =
        Regex("\\\\u([0-9a-fA-F]{4})").replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }
}
