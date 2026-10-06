package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServer
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Lets the host's season detail page load for region-limited bangumi: when a PGC HTTP request is
 * refused for the viewer's region, the same request is replayed on the resolver server. By default
 * the replay is anonymous: the account key and account headers are removed and the query is signed
 * again. Only when the user opted in to sharing the account is the original request sent as it is.
 */
class SeaBangumiDetailHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var interceptorClass: Class<*>? = null
    private var httpUrlClass: Class<*>? = null

    /** Region that last served a path, asked first next time. */
    private val servedBy = java.util.concurrent.ConcurrentHashMap<String, ResolverRegion>()

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSeaBangumiDetailEnabled(prefs)) return
        val builderClass = classLoader.findClassOrNull(CLIENT_BUILDER)
        interceptorClass = classLoader.findClassOrNull(INTERCEPTOR)
        httpUrlClass = classLoader.findClassOrNull(HTTP_URL)
        val build = builderClass?.declaredMethods?.firstOrNull { it.name == "build" && it.parameterCount == 0 }
        if (build == null || interceptorClass == null || httpUrlClass == null) {
            log("SeaBangumiDetail: host OkHttp not found")
            return
        }
        val interceptor = createInterceptor(interceptorClass!!)
        env.hookBefore(build) { param ->
            val builder = param.thisObject ?: return@hookBefore
            val installed = builder.callMethod("interceptors") as? List<*> ?: return@hookBefore
            if (installed.any { isOurs(it) }) return@hookBefore
            builder.callMethod("addInterceptor", interceptor)
        }
        isInstalled = true
        log("SeaBangumiDetail: interceptor hook installed")
    }

    private fun isOurs(candidate: Any?): Boolean =
        candidate != null && Proxy.isProxyClass(candidate.javaClass) &&
            Proxy.getInvocationHandler(candidate) is DetailHandler

    private fun createInterceptor(type: Class<*>): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type), DetailHandler())

    private inner class DetailHandler : InvocationHandler {
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? {
            if (method.name != "intercept" || args?.size != 1) {
                return when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> "SeaBangumiDetailInterceptor"
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
        val request = chain.callMethod("request") ?: return proceed(chain, null)
        val original = proceed(chain, request)
        if (original == null) return null
        return runCatching { replayIfRefused(chain, request, original) ?: original }
            .getOrElse {
                log("SeaBangumiDetail: replay failed", it)
                original
            }
    }

    /** Calls Chain.proceed directly so an IOException reaches the host instead of becoming null. */
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

    private fun replayIfRefused(chain: Any, request: Any, original: Any): Any? {
        if (!ModuleSettings.isSeaBangumiDetailEnabled(prefs)) return null
        val url = request.callMethod("url") ?: return null
        val path = url.callMethod("encodedPath") as? String ?: return null
        if (!SeaBangumiDetailPolicy.isPgcPath(path)) return null
        val servers = ResolverServers.configured(prefs)
        if (servers.isEmpty()) return null
        val host = url.callMethod("host") as? String
        if (servers.any { parseUrl(it.baseUrl)?.callMethod("host") == host }) return null

        val prefix = original.callMethod("peekBody", SeaBangumiDetailPolicy.PEEK_BYTES)?.callMethod("string") as? String
            ?: return null
        if (!SeaBangumiDetailPolicy.isRegionRefusal(prefix)) return null

        val shareAccount = ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)
        val query = url.callMethod("encodedQuery") as? String
        val replayQuery = if (shareAccount) query else SeaBangumiDetailPolicy.anonymousQuery(query)
        if (replayQuery == null && !shareAccount) {
            log("SeaBangumiDetail: $path was signed with another app key, cannot replay without the account")
            return null
        }
        for (server in ResolverServers.preferring(servers, servedBy[path])) {
            val replay = replayOn(chain, request, server, path, replayQuery, anonymous = !shareAccount) ?: continue
            servedBy[path] = server.region
            original.callMethod("close")
            log("SeaBangumiDetail: ${server.region} served $path")
            return replay
        }
        log("SeaBangumiDetail: no resolver could serve $path")
        return null
    }

    /** The replay response if this server answered with data; otherwise it is closed and null returned. */
    private fun replayOn(
        chain: Any,
        request: Any,
        server: ResolverServer,
        path: String,
        query: String?,
        anonymous: Boolean,
    ): Any? {
        val target = parseUrl(SeaBangumiDetailPolicy.replayUrl(server.baseUrl, path, query)) ?: return null
        val replayRequest = request.callMethod("newBuilder")?.run {
            callMethod("url", target)
            if (anonymous) SeaBangumiDetailPolicy.ACCOUNT_HEADERS.forEach { callMethod("removeHeader", it) }
            callMethod("build")
        } ?: return null
        val replay = runCatching { proceed(chain, replayRequest) }
            .onFailure { log("SeaBangumiDetail: ${server.region} request failed", it) }
            .getOrNull() ?: return null
        val prefix = replay.callMethod("peekBody", SeaBangumiDetailPolicy.PEEK_BYTES)?.callMethod("string") as? String ?: ""
        val accepted = replay.callMethod("isSuccessful") == true &&
            !SeaBangumiDetailPolicy.isRegionRefusal(prefix) &&
            Regex("\"code\"\\s*:\\s*0\\b").containsMatchIn(prefix)
        if (!accepted) {
            replay.callMethod("close")
            return null
        }
        return replay
    }

    private fun parseUrl(value: String): Any? = httpUrlClass!!.callStaticMethod("parse", value)

    private companion object {
        const val CLIENT_BUILDER = "okhttp3.OkHttpClient\$Builder"
        const val INTERCEPTOR = "okhttp3.Interceptor"
        const val HTTP_URL = "okhttp3.HttpUrl"
    }
}
