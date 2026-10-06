package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
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
 * refused for the viewer's region, the very same request (including the host's own signature) is
 * replayed on the resolver server. That replay necessarily carries the account key the host puts
 * on the request, so it only runs when the user opted in to sharing it.
 */
class SeaBangumiDetailHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private var interceptorClass: Class<*>? = null
    private var httpUrlClass: Class<*>? = null

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
        val baseUrl = ModuleSettings.getSeaResolverBaseUrl(prefs) ?: return null
        val resolverHost = httpUrlClass!!.callStaticMethod("parse", baseUrl)?.callMethod("host") as? String
        if (url.callMethod("host") == resolverHost) return null

        val prefix = original.callMethod("peekBody", SeaBangumiDetailPolicy.PEEK_BYTES)?.callMethod("string") as? String
            ?: return null
        if (!SeaBangumiDetailPolicy.isRegionRefusal(prefix)) return null

        val query = url.callMethod("encodedQuery") as? String
        val target = httpUrlClass!!.callStaticMethod("parse", SeaBangumiDetailPolicy.replayUrl(baseUrl, path, query))
            ?: return null
        val replayRequest = request.callMethod("newBuilder")?.run {
            callMethod("url", target)
            callMethod("build")
        } ?: return null
        val replay = proceed(chain, replayRequest) ?: return null
        val accepted = replay.callMethod("isSuccessful") == true &&
            !SeaBangumiDetailPolicy.isRegionRefusal(
                replay.callMethod("peekBody", SeaBangumiDetailPolicy.PEEK_BYTES)?.callMethod("string") as? String ?: "",
            ) && !looksLikeErrorEnvelope(replay)
        if (!accepted) {
            replay.callMethod("close")
            log("SeaBangumiDetail: resolver could not serve $path")
            return null
        }
        original.callMethod("close")
        log("SeaBangumiDetail: served $path through the resolver")
        return replay
    }

    /** A non-zero `code` on the replay means the resolver relayed another failure, not data. */
    private fun looksLikeErrorEnvelope(response: Any): Boolean {
        val prefix = response.callMethod("peekBody", SeaBangumiDetailPolicy.PEEK_BYTES)?.callMethod("string") as? String
            ?: return true
        return !Regex("\"code\"\\s*:\\s*0\\b").containsMatchIn(prefix)
    }

    private companion object {
        const val CLIENT_BUILDER = "okhttp3.OkHttpClient\$Builder"
        const val INTERCEPTOR = "okhttp3.Interceptor"
        const val HTTP_URL = "okhttp3.HttpUrl"
    }
}
