package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostUserAgent
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Lets the host open the space page of an account it refuses (deregistered, purged or banned).
 * When `/x/v2/space` comes back without usable data, the page is rebuilt from the public web card,
 * live-room and relation endpoints, which are queried by uid only and carry no account information.
 * The host loads the page through OkHttp in both its native and its local-H5 form, so one
 * interceptor covers both.
 */
class SpaceFixHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var responseBodyClass: Class<*>? = null
    private var mediaTypeClass: Class<*>? = null
    private val recent = ConcurrentHashMap<Long, Rebuilt>()
    private val userAgent by lazy { HostUserAgent.of(classLoader) }

    override fun startHook() {
        if (!ModuleSettings.isSpaceFixEnabled(prefs)) return
        val builderClass = classLoader.findClassOrNull(CLIENT_BUILDER)
        val interceptorClass = classLoader.findClassOrNull(INTERCEPTOR)
        responseBodyClass = classLoader.findClassOrNull(RESPONSE_BODY)
        mediaTypeClass = classLoader.findClassOrNull(MEDIA_TYPE)
        val build = builderClass?.declaredMethods?.firstOrNull { it.name == "build" && it.parameterCount == 0 }
        if (build == null || interceptorClass == null || responseBodyClass == null || mediaTypeClass == null) {
            log("SpaceFix: OkHttp classes not found, hook skipped")
            return
        }
        val interceptor = Proxy.newProxyInstance(interceptorClass.classLoader, arrayOf(interceptorClass), SpaceHandler())
        env.hookBefore(build) { param ->
            val builder = param.thisObject ?: return@hookBefore
            val installed = builder.callMethod("interceptors") as? List<*> ?: return@hookBefore
            if (installed.any { isOurs(it) }) return@hookBefore
            builder.callMethod("addInterceptor", interceptor)
        }
        isInstalled = true
        log("SpaceFix: OkHttp interceptor installed")
    }

    private fun isOurs(candidate: Any?): Boolean =
        candidate != null && Proxy.isProxyClass(candidate.javaClass) &&
            Proxy.getInvocationHandler(candidate) is SpaceHandler

    private inner class SpaceHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            if (method.name != "intercept" || args?.size != 1) {
                return when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> "SpaceFixInterceptor"
                }
            }
            val chain = args[0] ?: return null
            return try {
                intercept(chain)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
        }
    }

    private fun intercept(chain: Any): Any? {
        val request = chain.callMethod("request")
        val url = request?.callMethod("url")
        val path = url?.callMethod("encodedPath") as? String
        if (request == null || url == null || path == null || !SpaceFixPolicy.isSpacePath(path)) {
            return proceed(chain, request)
        }
        val original = proceed(chain, request) ?: return null
        return runCatching { rebuildIfBroken(url, original) ?: original }
            .getOrElse {
                log("SpaceFix: rebuild failed", it)
                original
            }
    }

    private fun proceed(chain: Any, request: Any?): Any? {
        val method = chain.javaClass.methods.firstOrNull { it.name == "proceed" && it.parameterCount == 1 }
            ?: error("Chain.proceed not found")
        method.isAccessible = true
        return try {
            method.invoke(chain, request)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun rebuildIfBroken(url: Any, original: Any): Any? {
        if (!ModuleSettings.isSpaceFixEnabled(prefs)) return null
        val mid = SpaceFixPolicy.parseMid(url.callMethod("queryParameter", "vmid") as? String) ?: return null
        val peeked = (original.callMethod("peekBody", PEEK_BYTES)?.callMethod("bytes") as? ByteArray) ?: return null
        val text = SeaBangumiDetailPolicy.decodePrefixBytes(peeked, PEEK_BYTES.toInt())
        if (!SpaceFixPolicy.needsFix(text)) return null

        val answer = rebuiltAnswer(mid)
        if (answer == null) {
            log("SpaceFix: no source knows $mid")
            return null
        }
        val mediaType = mediaTypeClass?.let {
            it.callStaticMethod("get", JSON_TYPE) ?: it.callStaticMethod("parse", JSON_TYPE)
        } ?: return null
        val body = responseBodyClass?.callStaticMethod("create", mediaType, answer) ?: return null
        val builder = original.callMethod("newBuilder") ?: return null
        builder.callMethod("code", 200)
        builder.callMethod("message", "OK")
        builder.callMethod("body", body)
        val rebuilt = builder.callMethod("build") ?: return null
        original.callMethod("close")
        log("SpaceFix: rebuilt space page of $mid")
        return rebuilt
    }

    /**
     * The rebuilt answer for [mid], or null when no source knows the account. The host asks again on
     * every tab refresh, so a recent answer is reused instead of querying the web endpoints again.
     */
    private fun rebuiltAnswer(mid: Long): String? {
        val now = System.currentTimeMillis()
        recent[mid]?.takeIf { now - it.builtAt < CACHE_MILLIS }?.let { return it.answer }

        val card = SpaceFixPolicy.dataOf(httpGet("${SpaceFixPolicy.CARD_URL}?mid=$mid"))
        val space = if (card?.optJSONObject("card") != null) {
            SpaceFixPolicy.buildSpace(mid, card, null, null)
        } else {
            // The card knows nothing: ask the two fallback sources at the same time.
            val live = CompletableFuture.supplyAsync { httpGet("${SpaceFixPolicy.LIVE_MASTER_URL}?uid=$mid") }
            val stat = httpGet("${SpaceFixPolicy.RELATION_STAT_URL}?vmid=$mid")
            SpaceFixPolicy.buildSpace(mid, card, SpaceFixPolicy.dataOf(live.join()), SpaceFixPolicy.dataOf(stat))
        } ?: return null

        val answer = SpaceFixPolicy.envelope(space)
        if (recent.size >= CACHE_ENTRIES) recent.clear()
        recent[mid] = Rebuilt(answer, now)
        return answer
    }

    private class Rebuilt(val answer: String, val builtAt: Long)

    private fun httpGet(address: String): String? = runCatching {
        val conn = URL(address).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Referer", "https://www.bilibili.com/")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.onFailure { log("SpaceFix: request failed for ${address.substringBefore('?')}", it) }.getOrNull()

    private companion object {
        const val CLIENT_BUILDER = "okhttp3.OkHttpClient\$Builder"
        const val INTERCEPTOR = "okhttp3.Interceptor"
        const val RESPONSE_BODY = "okhttp3.ResponseBody"
        const val MEDIA_TYPE = "okhttp3.MediaType"
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val PEEK_BYTES = 1_048_576L
        const val TIMEOUT_MS = 10_000
        const val CACHE_MILLIS = 60_000L
        const val CACHE_ENTRIES = 16
    }
}
