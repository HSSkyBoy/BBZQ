package io.github.bbzq.feats

import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Plain GET access to the user-configured resolver server; never sends account data on its own. */
class SeaResolverClient(
    baseUrl: String,
    private val log: (String, Throwable?) -> Unit,
) {
    private val base = baseUrl.toHttpUrl()

    /** Response body of a successful GET, or null on any failure or non-2xx status. */
    fun get(
        path: String,
        params: List<Pair<String, String>>,
        headers: Headers = Headers.Builder().build(),
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        encodedQuery: String? = null,
    ): String? {
        val url = base.newBuilder().addPathSegments(path.trimStart('/')).apply {
            if (encodedQuery != null) {
                encodedQuery(encodedQuery)
            } else {
                params.forEach { (name, value) -> addQueryParameter(name, value) }
            }
        }.build()
        val request = Request.Builder().url(url).headers(headers).get().build()
        return runCatching {
            httpClient.newBuilder().callTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
                .newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        log("SeaResolver: ${url.encodedPath} answered HTTP ${response.code}", null)
                        null
                    } else {
                        response.body.string()
                    }
                }
        }.getOrElse {
            log("SeaResolver: ${url.encodedPath} request failed", it)
            null
        }
    }

    /**
     * Round-trip time in milliseconds, or null if unreachable. Some servers do not expose the health
     * endpoint, so a tiny playurl request is the fallback.
     */
    fun ping(): Long? {
        val started = System.nanoTime()
        val answered = get("healthz", emptyList(), timeoutMillis = PING_TIMEOUT_MILLIS) != null ||
            get(
                "pgc/player/web/playurl",
                listOf("ep_id" to PING_EPISODE, "qn" to "16", "fnval" to "1", "fnver" to "0"),
                timeoutMillis = PING_TIMEOUT_MILLIS,
            ) != null
        return if (answered) (System.nanoTime() - started) / 1_000_000 else null
    }

    private companion object {
        const val PING_TIMEOUT_MILLIS = 8_000L
        const val PING_EPISODE = "733316"
        const val DEFAULT_TIMEOUT_MILLIS = 6_000L

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
