package io.github.bbzq.feats.hook

import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServer
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.SeaResolverClient
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.callStaticMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.intercept
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Lets the host's season detail page load for region-limited bangumi:
 * 1. OkHttp: when a PGC HTTP request is refused for the viewer's region, replayed on the resolver.
 *    Supports HK / TW / SEA with `area` query param and fallback to `/pgc/view/web/season`.
 * 2. Java Moss: `com.bapis.bilibili.app.viewunite.v1.ViewMoss` (executeView / view)
 * 3. Java Moss: `com.bapis.bilibili.pgc.gateway.view.v1.ViewMoss` (executeSeasonSectionsForCache / seasonSectionsForCache)
 * 4. KMP Moss: `kntr.base.moss.api.a.c(...)` for:
 *    - `bilibili.app.viewunite.v1.View/View`
 *    - `bilibili.pgc.gateway.view.v1.View/SeasonSectionsForCache`
 */
class SeaBangumiDetailHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val executor by lazy { Executors.newCachedThreadPool() }
    private var interceptorClass: Class<*>? = null
    private var httpUrlClass: Class<*>? = null

    private val loggedPaths = ConcurrentHashMap.newKeySet<String>()

    /** Region that last served a path, asked first next time. */
    private val servedBy = ConcurrentHashMap<String, ResolverRegion>()

    private var viewReplyClass: Class<*>? = null
    private var seasonSectionsReplyClass: Class<*>? = null

    override fun startHook() {
        if (!ModuleSettings.isSeaBangumiDetailEnabled(prefs)) return
        var installed = 0

        // 1. OkHttp Interceptor
        val builderClass = classLoader.findClassOrNull(CLIENT_BUILDER)
        interceptorClass = classLoader.findClassOrNull(INTERCEPTOR)
        httpUrlClass = classLoader.findClassOrNull(HTTP_URL)
        val build = builderClass?.declaredMethods?.firstOrNull { it.name == "build" && it.parameterCount == 0 }
        if (build != null && interceptorClass != null && httpUrlClass != null) {
            val interceptor = createInterceptor(interceptorClass!!)
            env.hookBefore(build) { param ->
                val builder = param.thisObject ?: return@hookBefore
                val installedInterceptors = builder.callMethod("interceptors") as? List<*> ?: return@hookBefore
                if (installedInterceptors.any { isOurs(it) }) return@hookBefore
                builder.callMethod("addInterceptor", interceptor)
            }
            installed++
            log("SeaBangumiDetail: OkHttp interceptor installed")
        }

        // 2. Java Moss: ViewMoss (viewunite)
        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)?.takeIf { it.isInterface }
        viewReplyClass = classLoader.findClassOrNull(VIEW_REPLY_CLASS)
        installed += hookViewMoss(
            mossName = VIEW_MOSS_CLASS,
            reqName = VIEW_REQ_CLASS,
            reply = viewReplyClass,
            syncName = VIEW_SYNC_METHOD,
            asyncName = VIEW_ASYNC_METHOD,
            handlerClass = handlerClass,
            isSeasonSections = false,
        )

        // 3. Java Moss: ViewMoss (seasonSectionsForCache)
        seasonSectionsReplyClass = classLoader.findClassOrNull(SEASON_SECTIONS_REPLY_CLASS)
        installed += hookViewMoss(
            mossName = PGC_VIEW_MOSS_CLASS,
            reqName = SEASON_SECTIONS_REQ_CLASS,
            reply = seasonSectionsReplyClass,
            syncName = SEASON_SECTIONS_SYNC_METHOD,
            asyncName = SEASON_SECTIONS_ASYNC_METHOD,
            handlerClass = handlerClass,
            isSeasonSections = true,
        )

        // 4. KMP Moss: bilibili.app.viewunite.v1.View/View & SeasonSectionsForCache
        installed += hookKmpViewMoss()

        isInstalled = installed > 0
        log("SeaBangumiDetail: hooked $installed detail method(s)")
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

        val prefix = SeaBangumiDetailPolicy.peekPrefix(original)
        if (prefix.isEmpty()) return null
        if (!SeaBangumiDetailPolicy.isRegionRefusal(prefix)) {
            val code = SeaBangumiDetailPolicy.answerCode(prefix)
            if (code != null && code != 0 && loggedPaths.add(path)) {
                log("SeaBangumiDetail: $path answered code=$code, not treated as a region refusal")
            }
            return null
        }

        val shareAccount = ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)
        val query = url.callMethod("encodedQuery") as? String

        for (server in ResolverServers.preferring(servers, servedBy[path] ?: ModuleSettings.getResolverDefaultRegion(prefs))) {
            val replayQuery = SeaBangumiDetailPolicy.buildReplayQuery(query, server.region, anonymous = !shareAccount)
            if (replayQuery == null && !shareAccount) continue

            // Try standard path first, then fallback to web path if applicable
            val candidatePaths = when (path) {
                SEASON_APP_PATH -> listOf(SEASON_APP_PATH, SEASON_WEB_PATH)
                else -> listOf(path)
            }

            for (candPath in candidatePaths) {
                val replay = replayOn(chain, request, server, candPath, replayQuery, anonymous = !shareAccount) ?: continue
                servedBy[path] = server.region
                original.callMethod("close")
                log("SeaBangumiDetail: ${server.region} served $candPath (origin $path)")
                return replay
            }
        }
        log("SeaBangumiDetail: no resolver could serve $path")
        return null
    }

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
            callMethod("header", "Referer", "https://www.bilibili.com/")
            if (anonymous) SeaBangumiDetailPolicy.ACCOUNT_HEADERS.forEach { callMethod("removeHeader", it) }
            callMethod("build")
        } ?: return null
        val replay = runCatching { proceed(chain, replayRequest) }
            .onFailure { log("SeaBangumiDetail: ${server.region} request failed", it) }
            .getOrNull() ?: return null
        val prefix = SeaBangumiDetailPolicy.peekPrefix(replay)
        val accepted = replay.callMethod("isSuccessful") == true &&
            !SeaBangumiDetailPolicy.isRegionRefusal(prefix) &&
            Regex("\"code\"\\s*:\\s*0\\b").containsMatchIn(prefix)
        if (!accepted) {
            replay.callMethod("close")
            return null
        }
        return replay
    }

    private fun hookViewMoss(
        mossName: String,
        reqName: String,
        reply: Class<*>?,
        syncName: String,
        asyncName: String,
        handlerClass: Class<*>?,
        isSeasonSections: Boolean,
    ): Int {
        val mossClass = classLoader.findClassOrNull(mossName)
        val requestClass = classLoader.findClassOrNull(reqName)
        if (mossClass == null || requestClass == null || reply == null) return 0
        var count = 0

        mossClass.declaredMethods.firstOrNull {
            it.name == syncName && it.parameterTypes.contentEquals(arrayOf(requestClass)) && !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.intercept(sync) { chain ->
                val request = chain.getArgs().firstOrNull()
                val args = chain.getArgs().toTypedArray()
                val res = try {
                    chain.proceed(args)
                } catch (t: Throwable) {
                    if (request != null) {
                        resolveJavaView(request, isSeasonSections)?.let { return@intercept it }
                    }
                    throw t
                }
                if (request != null && res != null && isJavaBlocked(res, isSeasonSections)) {
                    resolveJavaView(request, isSeasonSections)?.let { return@intercept it }
                }
                res
            }
            count++
        }

        if (handlerClass != null) {
            mossClass.declaredMethods.firstOrNull {
                it.name == asyncName &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val request = param.args.firstOrNull() ?: return@hookBefore
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    val (seasonId, epId) = resolveJavaTarget(request, isSeasonSections)
                    if (seasonId <= 0L && epId <= 0L) return@hookBefore

                    param.args[1] = wrapJavaHandler(handlerClass, delegate, request, isSeasonSections)
                }
                count++
            }
        }
        return count
    }

    private fun isJavaBlocked(reply: Any, isSeasonSections: Boolean): Boolean {
        val bytes = (reply.callMethod("toByteArray") as? ByteArray) ?: return false
        return if (isSeasonSections) {
            SeaBangumiViewCodec.isSeasonSectionsBlocked(bytes)
        } else {
            SeaBangumiViewCodec.isViewReplyBlocked(bytes)
        }
    }

    private fun resolveJavaTarget(request: Any, isSeasonSections: Boolean): Pair<Long, Long> {
        val bytes = (request.callMethod("toByteArray") as? ByteArray) ?: ByteArray(0)
        return if (isSeasonSections) {
            val decoded = SeaBangumiViewCodec.decodeSeasonSectionsReq(bytes)
            (decoded?.seasonId ?: 0L) to (decoded?.epId ?: 0L)
        } else {
            val decoded = SeaBangumiViewCodec.decodeViewReq(bytes)
            (decoded?.seasonId ?: 0L) to (decoded?.epId ?: 0L)
        }
    }

    private fun resolveJavaView(request: Any, isSeasonSections: Boolean): Any? {
        val bytes = (request.callMethod("toByteArray") as? ByteArray) ?: return null
        val targetClass = if (isSeasonSections) seasonSectionsReplyClass else viewReplyClass
        if (targetClass == null) return null

        val (seasonId, epId) = resolveJavaTarget(request, isSeasonSections)
        val repBytes = resolveViewReplyBytes(seasonId, epId, isSeasonSections) ?: return null
        return targetClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, repBytes)
    }

    private fun wrapJavaHandler(handlerClass: Class<*>, delegate: Any, request: Any, isSeasonSections: Boolean): Any {
        val gate = Gate()
        val callbackLooper = Looper.myLooper()
        val targetClass = if (isSeasonSections) seasonSectionsReplyClass else viewReplyClass
        return Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val arguments = args ?: emptyArray()
                if (gate.enqueueIfPending { MossCallbacks.invoke(delegate, method, arguments) }) {
                    return@InvocationHandler MossCallbacks.defaultValue(method)
                }

                val trigger = when {
                    method.name == "onNext" && arguments.size == 1 -> arguments[0]?.takeIf { isJavaBlocked(it, isSeasonSections) }
                    method.name == "onError" -> arguments.getOrNull(0)
                    else -> null
                }

                if (trigger == null || targetClass == null) {
                    MossCallbacks.invoke(delegate, method, arguments)
                } else {
                    gate.start()
                    executor.execute {
                        val (seasonId, epId) = resolveJavaTarget(request, isSeasonSections)
                        val replacementBytes = resolveViewReplyBytes(seasonId, epId, isSeasonSections)
                        val replacement = replacementBytes?.let {
                            targetClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, it)
                        }
                        val deliver = {
                            if (replacement != null) {
                                MossCallbacks.invokeByName(delegate, handlerClass, "onNext", ::log, replacement)
                                if (method.name == "onError") {
                                    MossCallbacks.invokeByName(delegate, handlerClass, "onCompleted", ::log)
                                }
                            } else {
                                MossCallbacks.invoke(delegate, method, arguments)
                            }
                            gate.finish()
                        }
                        if (callbackLooper != null) Handler(callbackLooper).post(deliver) else deliver()
                    }
                }
                MossCallbacks.defaultValue(method)
            },
        )
    }

    private fun hookKmpViewMoss(): Int {
        val mossClientClass = classLoader.findClassOrNull(KMP_MOSS_CLIENT) ?: return 0
        val method = mossClientClass.declaredMethods.firstOrNull {
            it.name == "c" && it.parameterCount == 7 && !Modifier.isStatic(it.modifiers)
        } ?: return 0

        env.hookBefore(method) { param ->
            val endpointDesc = param.args[0]?.toString().orEmpty()
            val isViewUnite = endpointDesc.contains("bilibili.app.viewunite.v1.View") && endpointDesc.contains("View")
            val isSections = endpointDesc.contains("bilibili.pgc.gateway.view.v1.View") &&
                endpointDesc.contains("SeasonSectionsForCache")
            if (!isViewUnite && !isSections) return@hookBefore

            val reqObj = param.args[1] ?: return@hookBefore
            val reqSerializer = param.args[2] ?: return@hookBefore
            val respDeserializer = param.args[3] ?: return@hookBefore
            val callbackH = param.args[4] ?: return@hookBefore
            val protoBuf = param.args[6] ?: return@hookBefore

            val reqBytes = runCatching {
                protoBuf.callMethod("encodeToByteArray", reqSerializer, reqObj) as? ByteArray
            }.getOrNull() ?: return@hookBefore

            val (seasonId, epId) = if (isSections) {
                val decoded = SeaBangumiViewCodec.decodeSeasonSectionsReq(reqBytes)
                (decoded?.seasonId ?: 0L) to (decoded?.epId ?: 0L)
            } else {
                val decoded = SeaBangumiViewCodec.decodeViewReq(reqBytes)
                (decoded?.seasonId ?: 0L) to (decoded?.epId ?: 0L)
            }

            val callbackInterface = callbackH.javaClass.interfaces.firstOrNull { it.name == KMP_MOSS_CALLBACK }
                ?: return@hookBefore

            param.args[4] = wrapKmpViewHandler(
                callbackInterface = callbackInterface,
                delegate = callbackH,
                protoBuf = protoBuf,
                respDeserializer = respDeserializer,
                seasonId = seasonId,
                epId = epId,
                isSeasonSections = isSections,
            )
        }
        return 1
    }

    private fun wrapKmpViewHandler(
        callbackInterface: Class<*>,
        delegate: Any,
        protoBuf: Any,
        respDeserializer: Any,
        seasonId: Long,
        epId: Long,
        isSeasonSections: Boolean,
    ): Any {
        val gate = Gate()
        return Proxy.newProxyInstance(
            callbackInterface.classLoader,
            arrayOf(callbackInterface),
            InvocationHandler { _, method, args ->
                val arguments = args ?: emptyArray()
                if (gate.enqueueIfPending { MossCallbacks.invoke(delegate, method, arguments) }) {
                    return@InvocationHandler MossCallbacks.defaultValue(method)
                }

                var shouldIntercept = false
                if (method.name == "onNext" && arguments.size == 1 && arguments[0] != null) {
                    val respBytes = runCatching {
                        protoBuf.callMethod("encodeToByteArray", respDeserializer, arguments[0]) as? ByteArray
                    }.getOrNull()
                    if (respBytes != null) {
                        shouldIntercept = if (isSeasonSections) {
                            SeaBangumiViewCodec.isSeasonSectionsBlocked(respBytes)
                        } else {
                            SeaBangumiViewCodec.isViewReplyBlocked(respBytes)
                        }
                    }
                } else if (method.name == "onError") {
                    shouldIntercept = (seasonId > 0L || epId > 0L)
                }

                if (!shouldIntercept || (seasonId <= 0L && epId <= 0L)) {
                    return@InvocationHandler MossCallbacks.invoke(delegate, method, arguments)
                }

                gate.start()
                executor.execute {
                    val repBytes = resolveViewReplyBytes(seasonId, epId, isSeasonSections)
                    val decoded = if (repBytes != null) {
                        runCatching {
                            protoBuf.callMethod("decodeFromByteArray", respDeserializer, repBytes)
                        }.getOrNull()
                    } else null

                    if (decoded != null) {
                        MossCallbacks.invokeByName(delegate, callbackInterface, "onNext", ::log, decoded)
                        if (method.name == "onError") {
                            MossCallbacks.invokeByName(delegate, callbackInterface, "onCompleted", ::log)
                        }
                    } else {
                        MossCallbacks.invoke(delegate, method, arguments)
                    }
                    gate.finish()
                }
                MossCallbacks.defaultValue(method)
            },
        )
    }

    private fun resolveViewReplyBytes(seasonId: Long, epId: Long, isSeasonSections: Boolean): ByteArray? {
        var sid = seasonId
        if (sid <= 0L && epId > 0L) {
            sid = SeaBangumiSession.episodeForEpId(epId)?.seasonId ?: 0L
        }
        val cached = if (sid > 0L) SeaBangumiSession.cachedSeason(sid) else null
        val seasonJson = if (cached != null) {
            SeaBangumiViewCodec.parseSeasonJson(cached.jsonBody, cached.region)
        } else {
            fetchSeasonJson(sid, epId)
        } ?: return null

        val relax = ModuleSettings.isSeaRelaxPlayLimitsEnabled(prefs)
        return if (isSeasonSections) {
            SeaBangumiViewCodec.buildSeasonSectionsReply(seasonJson, relaxLimits = relax)
        } else {
            SeaBangumiViewCodec.buildViewReply(seasonJson, targetEpId = epId, relaxLimits = relax)
        }
    }

    private fun fetchSeasonJson(seasonId: Long, epId: Long): org.json.JSONObject? {
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), ModuleSettings.getResolverDefaultRegion(prefs))
        if (servers.isEmpty()) return null

        val baseParams = mutableListOf<Pair<String, String>>()
        if (seasonId > 0L) baseParams += "season_id" to seasonId.toString()
        if (epId > 0L) baseParams += "ep_id" to epId.toString()

        for (server in servers) {
            val client = SeaResolverClient(server.baseUrl, ::log)
            val serverParams = baseParams + ("area" to server.region.areaCode)
            val candidatePaths = when (server.region) {
                ResolverRegion.SEA -> listOf(SEASON_APP_PATH, SEASON_WEB_PATH, SEASON_INTL_PATH)
                else -> listOf(SEASON_APP_PATH, SEASON_WEB_PATH)
            }
            for (path in candidatePaths) {
                val body = client.get(path, serverParams) ?: continue
                val parsed = SeaBangumiViewCodec.parseSeasonJson(body, server.region)
                if (parsed != null) {
                    log("SeaBangumiDetail: ${server.region} supplied season JSON via $path for season $seasonId / ep $epId")
                    return parsed
                }
            }
        }
        return null
    }

    private fun parseUrl(value: String): Any? = httpUrlClass!!.callStaticMethod("parse", value)

    private companion object {
        const val CLIENT_BUILDER = "okhttp3.OkHttpClient\$Builder"
        const val INTERCEPTOR = "okhttp3.Interceptor"
        const val HTTP_URL = "okhttp3.HttpUrl"

        const val VIEW_MOSS_CLASS = "com.bapis.bilibili.app.viewunite.v1.ViewMoss"
        const val VIEW_REQ_CLASS = "com.bapis.bilibili.app.viewunite.v1.ViewReq"
        const val VIEW_REPLY_CLASS = "com.bapis.bilibili.app.viewunite.v1.ViewReply"
        const val VIEW_SYNC_METHOD = "executeView"
        const val VIEW_ASYNC_METHOD = "view"

        const val PGC_VIEW_MOSS_CLASS = "com.bapis.bilibili.pgc.gateway.view.v1.ViewMoss"
        const val SEASON_SECTIONS_REQ_CLASS = "com.bapis.bilibili.pgc.gateway.view.v1.SeasonSectionsReq"
        const val SEASON_SECTIONS_REPLY_CLASS = "com.bapis.bilibili.pgc.gateway.view.v1.SeasonSectionsReply"
        const val SEASON_SECTIONS_SYNC_METHOD = "executeSeasonSectionsForCache"
        const val SEASON_SECTIONS_ASYNC_METHOD = "seasonSectionsForCache"

        const val KMP_MOSS_CLIENT = "kntr.base.moss.api.a"
        const val KMP_MOSS_CALLBACK = "Kh1.h"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"

        const val SEASON_APP_PATH = "pgc/view/v2/app/season"
        const val SEASON_WEB_PATH = "pgc/view/web/season"
        const val SEASON_INTL_PATH = "intl/gateway/v2/ogv/view/app/season"
    }
}
