package io.github.bbzq.feats

import java.security.MessageDigest

/**
 * Query signing of the Android client's app API. The key pair is the publicly documented one for
 * the main client; the resolver forwards the signed request to Bilibili unchanged.
 */
object AppSignature {
    const val APP_KEY = "1d8b6e7d45233436"
    private const val APP_SECRET = "560c52ccd288fed045859ed18bffd973"

    /**
     * Returns the signed query string: parameters sorted by key and percent-encoded (RFC 3986),
     * followed by `sign`, the MD5 of that exact string plus the secret. [params] must not contain
     * `appkey` or `sign`; the key is added here.
     */
    fun signedQuery(params: Map<String, String>): String {
        val query = (params + ("appkey" to APP_KEY)).toSortedMap().entries
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return "$query&sign=${md5(query + APP_SECRET)}"
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt() and 0xFF
            if (c.toChar() in UNRESERVED) append(c.toChar()) else append('%').append("%02X".format(c))
        }
    }

    private val UNRESERVED: Set<Char> =
        (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_', '.', '~')).toSet()
}
