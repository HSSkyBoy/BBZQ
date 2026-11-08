package io.github.bbzq.feats.hook

import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.HostAccountCookies
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServer
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.SeaResolverClient
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.intercept
import okhttp3.Headers
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * When the official PGC play request is refused because of the viewer's region, asks the
 * resolver server for the same episode and substitutes its streams into the reply, so the
 * host player never sees the refusal. Other failures are passed through untouched.
 *
 * Regional priority: HK / TW (Hong Kong / Taiwan) > SEA (Southeast Asia) > CN.
 * Endpoints tried:
 * 1. `/pgc/player/api/playurl` (App endpoint with area parameter)
 * 2. `/pgc/player/web/playurl` (Web fallback endpoint)
 * 3. `/intl/gateway/v2/ogv/playurl` (for SEA region)
 */
class SeaBangumiPlayHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val executor by lazy { Executors.newCachedThreadPool() }
    private var replyClass: Class<*>? = null
    private var uniteReplyClass: Class<*>? = null

    /** Region that last served an episode, asked first next time. */
    private val servedBy = ConcurrentHashMap<Long, ResolverRegion>()

    override fun startHook() {
        if (!ModuleSettings.isSeaBangumiPlayEnabled(prefs)) return
        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)?.takeIf { it.isInterface }
        var installed = 0

        replyClass = classLoader.findClassOrNull(PGC_REPLY_CLASS)
        installed += hookMoss(PGC_MOSS_CLASS, PGC_REQUEST_CLASS, replyClass, PGC_SYNC_METHOD, PGC_ASYNC_METHOD, handlerClass, isUnite = false)
        uniteReplyClass = classLoader.findClassOrNull(UNITE_REPLY_CLASS)
        installed += hookMoss(UNITE_MOSS_CLASS, UNITE_REQUEST_CLASS, uniteReplyClass, UNITE_SYNC_METHOD, UNITE_ASYNC_METHOD, handlerClass, isUnite = true)

        installed += hookKmpMoss()

        isInstalled = installed > 0
        log("SeaBangumiPlay: hooked $installed play method(s)")
    }

    private fun hookMoss(
        mossName: String,
        requestName: String,
        reply: Class<*>?,
        syncName: String,
        asyncName: String,
        handlerClass: Class<*>?,
        isUnite: Boolean,
    ): Int {
        val mossClass = classLoader.findClassOrNull(mossName)
        val requestClass = classLoader.findClassOrNull(requestName)
        if (mossClass == null || requestClass == null || reply == null) return 0
        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == syncName && it.parameterTypes.contentEquals(arrayOf(requestClass)) && !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.intercept(sync) { chain -> proceedWithFallback(chain.getArgs().firstOrNull(), chain, isUnite) }
            installed++
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
                    if (resolveTarget(request, isUnite) == null) return@hookBefore
                    param.args[1] = wrapHandler(handlerClass, delegate, request, isUnite)
                }
                installed++
            }
        }
        return installed
    }

    private fun hookKmpMoss(): Int {
        val mossClientClass = classLoader.findClassOrNull(KMP_MOSS_CLIENT) ?: return 0
        val method = mossClientClass.declaredMethods.firstOrNull {
            it.name == "c" && it.parameterCount == 7 && !Modifier.isStatic(it.modifiers)
        } ?: return 0

        env.hookBefore(method) { param ->
            val endpointDesc = param.args[0]?.toString().orEmpty()
            if (!endpointDesc.contains("bilibili.app.playerunite.v1") || !endpointDesc.contains("PlayViewUnite")) {
                return@hookBefore
            }

            val reqObj = param.args[1] ?: return@hookBefore
            val reqSerializer = param.args[2] ?: return@hookBefore
            val respDeserializer = param.args[3] ?: return@hookBefore
            val callbackH = param.args[4] ?: return@hookBefore
            val protoBuf = param.args[6] ?: return@hookBefore

            val reqBytes = runCatching {
                protoBuf.callMethod("encodeToByteArray", reqSerializer, reqObj) as? ByteArray
            }.getOrNull() ?: return@hookBefore

            val decodedReq = SeaBangumiPlayCodec.decodePlayViewUniteReq(reqBytes) ?: return@hookBefore
            val (epId, seasonId, aid, cid) = resolveKmpTarget(decodedReq)

            val callbackInterface = callbackH.javaClass.interfaces.firstOrNull { it.name == KMP_MOSS_CALLBACK }
                ?: return@hookBefore

            param.args[4] = wrapKmpHandler(
                callbackInterface = callbackInterface,
                delegate = callbackH,
                protoBuf = protoBuf,
                respDeserializer = respDeserializer,
                reqBytes = reqBytes,
                epId = epId,
                seasonId = seasonId,
                aid = aid,
                cid = cid,
                qn = decodedReq.qn,
                fnval = decodedReq.fnval,
                fnver = decodedReq.fnver,
                fourk = decodedReq.fourk,
            )
        }
        return 1
    }

    private fun resolveKmpTarget(req: SeaBangumiPlayCodec.DecodedUniteReq): TargetMeta {
        var epId = req.epId
        var seasonId = req.seasonId
        var aid = req.aid
        var cid = req.cid

        if (epId <= 0L && cid > 0L) {
            SeaBangumiSession.episodeForCid(cid)?.let {
                epId = it.epId
                if (seasonId <= 0L) seasonId = it.seasonId
                if (aid <= 0L) aid = it.aid
            }
        }
        if (epId <= 0L && seasonId > 0L) {
            SeaBangumiSession.firstEpisodeForSeason(seasonId)?.let {
                epId = it.epId
                if (aid <= 0L) aid = it.aid
                if (cid <= 0L) cid = it.cid
            }
        }
        if (epId <= 0L && seasonId > 0L) {
            fetchAndCacheSeason(seasonId)?.let {
                SeaBangumiSession.firstEpisodeForSeason(seasonId)?.let {
                    epId = it.epId
                    if (aid <= 0L) aid = it.aid
                    if (cid <= 0L) cid = it.cid
                }
            }
        }
        return TargetMeta(epId, seasonId, aid, cid)
    }

    private data class TargetMeta(val epId: Long, val seasonId: Long, val aid: Long, val cid: Long)

    private fun wrapKmpHandler(
        callbackInterface: Class<*>,
        delegate: Any,
        protoBuf: Any,
        respDeserializer: Any,
        reqBytes: ByteArray,
        epId: Long,
        seasonId: Long,
        aid: Long,
        cid: Long,
        qn: Long,
        fnval: Long,
        fnver: Long,
        fourk: Boolean,
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
                var originalBytes = ByteArray(0)

                if (method.name == "onNext" && arguments.size == 1 && arguments[0] != null) {
                    val respBytes = runCatching {
                        protoBuf.callMethod("encodeToByteArray", respDeserializer, arguments[0]) as? ByteArray
                    }.getOrNull()
                    if (respBytes != null) {
                        originalBytes = respBytes
                        shouldIntercept = SeaBangumiPlayCodec.isUniteReplyBlocked(respBytes)
                    }
                } else if (method.name == "onError") {
                    shouldIntercept = (epId > 0L || seasonId > 0L)
                }

                if (!shouldIntercept || (epId <= 0L && seasonId <= 0L)) {
                    return@InvocationHandler MossCallbacks.invoke(delegate, method, arguments)
                }

                gate.start()
                executor.execute {
                    val targetEpId = if (epId > 0L) epId else {
                        val meta = resolveKmpTarget(SeaBangumiPlayCodec.DecodedUniteReq(aid, cid, qn, fnval, fnver, fourk, seasonId, 0L))
                        meta.epId
                    }
                    val replacementBytes = if (targetEpId > 0L) {
                        resolvePlayBytes(
                            epId = targetEpId,
                            seasonId = seasonId,
                            aid = aid,
                            cid = cid,
                            qn = qn,
                            fnval = fnval,
                            fnver = fnver,
                            fourk = fourk,
                            originalBytes = originalBytes,
                        )
                    } else null

                    val decodedReplacement = if (replacementBytes != null) {
                        runCatching {
                            protoBuf.callMethod("decodeFromByteArray", respDeserializer, replacementBytes)
                        }.getOrNull()
                    } else null

                    if (decodedReplacement != null) {
                        MossCallbacks.invokeByName(delegate, callbackInterface, "onNext", ::log, decodedReplacement)
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

    private fun proceedWithFallback(request: Any?, chain: io.github.libxposed.api.XposedInterface.Chain, isUnite: Boolean): Any? {
        val args = chain.getArgs().toTypedArray()
        val reply = try {
            chain.proceed(args)
        } catch (error: Throwable) {
            val cause = generateSequence(error) { it.cause }.firstOrNull { it.javaClass.name == BUSINESS_EXCEPTION }
            if (request != null && cause != null && isRegionError(cause)) {
                resolve(request, null, isUnite)?.let { return it }
            }
            throw error
        }
        if (request != null && reply != null && isRegionBlocked(reply, isUnite)) {
            resolve(request, reply, isUnite)?.let { return it }
        }
        return reply
    }

    private fun wrapHandler(handlerClass: Class<*>, delegate: Any, request: Any, isUnite: Boolean): Any {
        val gate = Gate()
        val callbackLooper = Looper.myLooper()
        return Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val arguments = args ?: emptyArray()
                if (gate.enqueueIfPending { invoke(delegate, method, arguments) }) {
                    return@InvocationHandler defaultValue(method)
                }
                val trigger = when {
                    method.name == "onNext" && arguments.size == 1 -> arguments[0]?.takeIf { isRegionBlocked(it, isUnite) }
                        ?.let { Trigger.Reply(it) }
                    method.name == "onError" && arguments.size == 1 -> arguments[0]
                        ?.takeIf { it.javaClass.name == BUSINESS_EXCEPTION && isRegionError(it as Throwable) }
                        ?.let { Trigger.Error(it) }
                    else -> null
                }
                if (trigger == null) {
                    invoke(delegate, method, arguments)
                } else {
                    gate.start()
                    executor.execute {
                        val replacement = runCatching {
                            resolve(request, (trigger as? Trigger.Reply)?.reply, isUnite)
                        }.onFailure { log("SeaBangumiPlay: fallback failed", it) }.getOrNull()
                        val deliver = {
                            if (replacement != null) {
                                invokeByName(delegate, handlerClass, "onNext", replacement)
                                if (trigger is Trigger.Error) invokeByName(delegate, handlerClass, "onCompleted")
                            } else {
                                invoke(delegate, method, arguments)
                            }
                            gate.finish()
                        }
                        if (callbackLooper != null) Handler(callbackLooper).post(deliver) else deliver()
                    }
                }
                defaultValue(method)
            },
        )
    }

    private fun resolve(request: Any, original: Any?, isUnite: Boolean): Any? {
        val (epId, seasonId, aid, cid) = resolveFullTarget(request, isUnite) ?: return null
        val originalBytes = (original?.callMethod("toByteArray") as? ByteArray) ?: ByteArray(0)
        val vod = if (isUnite) request.callMethod("getVod") else null
        val reqCid = (vod?.callMethod("getCid") ?: request.callMethod("getCid")).asLong()
        val qn = (vod?.callMethod("getQn") ?: request.callMethod("getQn")).asLong()
        val fnval = (vod?.callMethod("getFnval") ?: request.callMethod("getFnval")).asLong()
        val fnver = (vod?.callMethod("getFnver") ?: request.callMethod("getFnver")).asLong()
        val fourk = vod?.callMethod("getFourk") ?: request.callMethod("getFourk")
        val is4k = fourk == true || (fourk as? Number)?.toInt() == 1

        val finalCid = if (reqCid > 0L) reqCid else cid

        if (isUnite) {
            val bytes = resolvePlayBytes(
                epId = epId,
                seasonId = seasonId,
                aid = aid,
                cid = finalCid,
                qn = qn,
                fnval = fnval,
                fnver = fnver,
                fourk = is4k,
                originalBytes = originalBytes,
            ) ?: return null
            val targetClass = uniteReplyClass ?: return null
            return targetClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        } else {
            val bytes = resolveLegacyPlayBytes(
                epId = epId,
                seasonId = seasonId,
                cid = finalCid,
                qn = qn,
                fnval = fnval,
                fnver = fnver,
                fourk = is4k,
                originalBytes = originalBytes,
            ) ?: return null
            val targetClass = replyClass ?: return null
            return targetClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        }
    }

    private fun resolvePlayBytes(
        epId: Long,
        seasonId: Long,
        aid: Long,
        cid: Long,
        qn: Long,
        fnval: Long,
        fnver: Long,
        fourk: Boolean,
        originalBytes: ByteArray,
    ): ByteArray? {
        val preferred = servedBy[epId] ?: ModuleSettings.getResolverDefaultRegion(prefs)
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), preferred)
        if (servers.isEmpty()) return null

        val baseParams = mutableListOf(
            "ep_id" to epId.toString(),
            "qn" to (qn.takeIf { it > 0 } ?: DEFAULT_QN).toString(),
            "fnval" to (fnval.takeIf { it > 0 } ?: DEFAULT_FNVAL).toString(),
            "fnver" to fnver.toString(),
            "fourk" to (if (fourk) "1" else "0"),
            "platform" to "android",
            "mobi_app" to "android",
        )
        if (cid > 0) baseParams += "cid" to cid.toString()
        if (seasonId > 0) baseParams += "season_id" to seasonId.toString()
        if (ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)) {
            prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)?.takeIf { it.isNotBlank() }
                ?.let { baseParams += "access_key" to it }
        }
        val relax = ModuleSettings.isSeaRelaxPlayLimitsEnabled(prefs)
        val headers = HostAccountCookies.headers(env.hostContext, ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs))
        val completion = ExecutorCompletionService<Pair<ResolverServer, ByteArray?>>(executor)

        val tasks = servers.mapIndexed { index, server ->
            val delay = if (preferred != null && index > 0) STAGGER_MILLIS else 0L
            completion.submit {
                if (delay > 0) Thread.sleep(delay)
                val serverParams = baseParams.toMutableList()
                if (!serverParams.any { it.first == "area" }) {
                    serverParams += "area" to server.region.areaCode
                }
                val client = SeaResolverClient(server.baseUrl, ::log)

                // Try play paths: App playurl -> Web playurl -> SEA international playurl
                val candidatePaths = when (server.region) {
                    ResolverRegion.SEA -> listOf(PLAYURL_PATH, PLAYURL_INTL_PATH, PLAYURL_APP_PATH)
                    else -> listOf(PLAYURL_APP_PATH, PLAYURL_PATH)
                }

                var result: org.json.JSONObject? = null
                for (path in candidatePaths) {
                    val body = client.get(path, serverParams, headers, timeoutMillis = PLAY_TIMEOUT_MILLIS)
                    if (body != null) {
                        result = SeaBangumiPlayCodec.playableResult(body)
                        if (result != null) break
                    }
                }

                if (result == null) {
                    server to null
                } else {
                    val cdnHost = ModuleSettings.getResolverCdnHost(prefs, server.region)
                    val bytes = SeaBangumiPlayCodec.buildUniteReply(
                        original = originalBytes,
                        result = result,
                        relaxPlayLimits = relax,
                        cdnHost = cdnHost,
                        aid = aid,
                        cid = cid,
                        epId = epId,
                        seasonId = seasonId,
                    )
                    server to bytes
                }
            }
        }
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MILLIS
        try {
            repeat(servers.size) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                val finished = completion.poll(left, TimeUnit.MILLISECONDS) ?: return null
                val (server, bytes) = runCatching { finished.get() }.getOrNull() ?: return@repeat
                if (bytes != null) {
                    servedBy[epId] = server.region
                    SeaBangumiSession.record(epId, cid, server.region, seasonId, aid)
                    log("SeaBangumiPlay: ${server.region} served unite ep $epId season $seasonId")
                    return bytes
                }
            }
            return null
        } finally {
            tasks.forEach { it.cancel(true) }
        }
    }

    private fun resolveLegacyPlayBytes(
        epId: Long,
        seasonId: Long,
        cid: Long,
        qn: Long,
        fnval: Long,
        fnver: Long,
        fourk: Boolean,
        originalBytes: ByteArray,
    ): ByteArray? {
        val preferred = servedBy[epId] ?: ModuleSettings.getResolverDefaultRegion(prefs)
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), preferred)
        if (servers.isEmpty()) return null

        val baseParams = mutableListOf(
            "ep_id" to epId.toString(),
            "qn" to (qn.takeIf { it > 0 } ?: DEFAULT_QN).toString(),
            "fnval" to (fnval.takeIf { it > 0 } ?: DEFAULT_FNVAL).toString(),
            "fnver" to fnver.toString(),
            "fourk" to (if (fourk) "1" else "0"),
            "platform" to "android",
            "mobi_app" to "android",
        )
        if (cid > 0) baseParams += "cid" to cid.toString()
        if (seasonId > 0) baseParams += "season_id" to seasonId.toString()
        if (ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)) {
            prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)?.takeIf { it.isNotBlank() }
                ?.let { baseParams += "access_key" to it }
        }
        val relax = ModuleSettings.isSeaRelaxPlayLimitsEnabled(prefs)
        val headers = HostAccountCookies.headers(env.hostContext, ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs))
        val completion = ExecutorCompletionService<Pair<ResolverServer, ByteArray?>>(executor)

        val tasks = servers.mapIndexed { index, server ->
            val delay = if (preferred != null && index > 0) STAGGER_MILLIS else 0L
            completion.submit {
                if (delay > 0) Thread.sleep(delay)
                val serverParams = baseParams.toMutableList()
                if (!serverParams.any { it.first == "area" }) {
                    serverParams += "area" to server.region.areaCode
                }
                val client = SeaResolverClient(server.baseUrl, ::log)

                val candidatePaths = when (server.region) {
                    ResolverRegion.SEA -> listOf(PLAYURL_PATH, PLAYURL_INTL_PATH, PLAYURL_APP_PATH)
                    else -> listOf(PLAYURL_APP_PATH, PLAYURL_PATH)
                }

                var result: org.json.JSONObject? = null
                for (path in candidatePaths) {
                    val body = client.get(path, serverParams, headers, timeoutMillis = PLAY_TIMEOUT_MILLIS)
                    if (body != null) {
                        result = SeaBangumiPlayCodec.playableResult(body)
                        if (result != null) break
                    }
                }

                if (result == null) {
                    server to null
                } else {
                    val cdnHost = ModuleSettings.getResolverCdnHost(prefs, server.region)
                    val bytes = SeaBangumiPlayCodec.buildReply(
                        original = originalBytes,
                        result = result,
                        relaxPlayLimits = relax,
                        cdnHost = cdnHost,
                    )
                    server to bytes
                }
            }
        }
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MILLIS
        try {
            repeat(servers.size) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                val finished = completion.poll(left, TimeUnit.MILLISECONDS) ?: return null
                val (server, bytes) = runCatching { finished.get() }.getOrNull() ?: return@repeat
                if (bytes != null) {
                    servedBy[epId] = server.region
                    SeaBangumiSession.record(epId, cid, server.region, seasonId)
                    log("SeaBangumiPlay: ${server.region} served legacy ep $epId season $seasonId")
                    return bytes
                }
            }
            return null
        } finally {
            tasks.forEach { it.cancel(true) }
        }
    }

    private fun resolveTarget(request: Any, isUnite: Boolean): Pair<Long, Long>? {
        val meta = resolveFullTarget(request, isUnite) ?: return null
        return meta.epId to meta.seasonId
    }

    private fun resolveFullTarget(request: Any, isUnite: Boolean): TargetMeta? {
        if (!isUnite) {
            val epId = request.callMethod("getEpId").asLong()
            if (epId <= 0) return null
            val seasonId = request.callMethod("getSeasonId").asLong()
            val cid = request.callMethod("getCid").asLong()
            return TargetMeta(epId, seasonId, 0L, cid)
        }

        val extra = (request.callMethod("getExtraContentMap") as? Map<*, *>)
            ?: (request.callMethod("getExtraContent") as? Map<*, *>)
        var epId = (extra?.get("ep_id") as? String)?.toLongOrNull() ?: 0L
        var seasonId = (extra?.get("season_id") as? String)?.toLongOrNull() ?: 0L

        val vod = request.callMethod("getVod")
        var cid = (vod?.callMethod("getCid") ?: request.callMethod("getCid")).asLong()
        var aid = (vod?.callMethod("getAid") ?: request.callMethod("getAid")).asLong()

        if (epId <= 0L && cid > 0L) {
            SeaBangumiSession.episodeForCid(cid)?.let {
                epId = it.epId
                if (seasonId <= 0L) seasonId = it.seasonId
                if (aid <= 0L) aid = it.aid
            }
        }

        if (epId <= 0L && seasonId > 0L) {
            SeaBangumiSession.firstEpisodeForSeason(seasonId)?.let {
                epId = it.epId
                if (aid <= 0L) aid = it.aid
                if (cid <= 0L) cid = it.cid
            }
        }

        if (epId <= 0L && seasonId > 0L) {
            fetchAndCacheSeason(seasonId)?.let {
                SeaBangumiSession.firstEpisodeForSeason(seasonId)?.let {
                    epId = it.epId
                    if (aid <= 0L) aid = it.aid
                    if (cid <= 0L) cid = it.cid
                }
            }
        }

        if (epId <= 0L) return null
        return TargetMeta(epId, seasonId, aid, cid)
    }

    private fun fetchAndCacheSeason(seasonId: Long): String? {
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), ModuleSettings.getResolverDefaultRegion(prefs))
        if (servers.isEmpty()) return null
        val params = listOf("season_id" to seasonId.toString())
        for (server in servers) {
            val client = SeaResolverClient(server.baseUrl, ::log)
            val serverParams = params + ("area" to server.region.areaCode)
            val candidatePaths = when (server.region) {
                ResolverRegion.SEA -> listOf(SEASON_APP_PATH, SEASON_WEB_PATH, SEASON_INTL_PATH)
                else -> listOf(SEASON_APP_PATH, SEASON_WEB_PATH)
            }
            for (path in candidatePaths) {
                val body = client.get(path, serverParams) ?: continue
                val parsed = SeaBangumiViewCodec.parseSeasonJson(body, server.region)
                if (parsed != null) return body
            }
        }
        return null
    }

    private fun isRegionBlocked(reply: Any, isUnite: Boolean): Boolean {
        if (isUnite) {
            val vodInfo = reply.callMethod("getVodInfo")
            val streams = (vodInfo?.callMethod("getStreamListCount") as? Number)?.toInt() ?: 0
            val viewInfo = reply.callMethod("getViewInfo")
            val dialogMap = (viewInfo?.callMethod("getDialogMapMap") as? Map<*, *>)
                ?: (viewInfo?.callMethod("getDialogMap") as? Map<*, *>)

            val hasAreaLimitDialog = dialogMap?.any { (key, value) ->
                val k = key?.toString().orEmpty()
                if (k == SeaBangumiPlayCodec.AREA_LIMIT_DIALOG) return@any true
                val title = value?.callMethod("getTitle")?.callMethod("getText") as? String
                val subtitle = value?.callMethod("getSubtitle")?.callMethod("getText") as? String
                val msg = value?.callMethod("getMsg") as? String
                listOf(title, subtitle, msg).any { it != null && SeaBangumiPlayCodec.isRegionBlocked(0, it) }
            } == true

            val refused = streams <= 0 || hasAreaLimitDialog
            if (refused) {
                log("SeaBangumiPlay (Unite): reply refused (streams=$streams, hasAreaLimitDialog=$hasAreaLimitDialog)")
            }
            return refused
        }

        val streams = (reply.callMethod("getVideoInfo")?.callMethod("getStreamListCount") as? Number)?.toInt() ?: 0
        val viewInfo = reply.callMethod("getViewInfo")
        val dialogs = listOf(viewInfo?.callMethod("getDialog"), viewInfo?.callMethod("getEndPage")?.callMethod("getDialog"))
        val types = dialogs.map { it?.callMethod("getType") as? String }
        val refused = SeaBangumiPlayCodec.isRefusedReply(streams, types)
        if (refused) {
            val dialog = dialogs.firstOrNull { it != null }
            log(
                "SeaBangumiPlay: official reply refused (streams=$streams, dialogType=${types.firstOrNull()}, " +
                    "endPageDialogType=${types.getOrNull(1)}, code=${dialog?.callMethod("getCode")}, msg=${dialog?.callMethod("getMsg")})",
            )
        }
        return refused
    }

    private fun isRegionError(error: Throwable): Boolean {
        val code = ((error as Any).callMethod("getCode") as? Number)?.toInt() ?: 0
        log("SeaBangumiPlay: play call failed (code=$code, message=${error.message})")
        return true
    }

    private fun invoke(target: Any, method: Method, args: Array<Any?>): Any? = MossCallbacks.invoke(target, method, args)

    private fun invokeByName(target: Any, type: Class<*>, name: String, vararg args: Any?) =
        MossCallbacks.invokeByName(target, type, name, ::log, *args)

    private fun defaultValue(method: Method): Any? = MossCallbacks.defaultValue(method)

    private fun Any?.asLong(): Long = (this as? Number)?.toLong() ?: 0L

    private sealed interface Trigger {
        class Reply(val reply: Any) : Trigger
        class Error(val error: Any) : Trigger
    }

    private companion object {
        const val PGC_V2 = "com.bapis.bilibili.pgc.gateway.player.v2."
        const val PGC_MOSS_CLASS = PGC_V2 + "PlayURLMoss"
        const val PGC_REQUEST_CLASS = PGC_V2 + "PlayViewReq"
        const val PGC_REPLY_CLASS = PGC_V2 + "PlayViewReply"
        const val PGC_SYNC_METHOD = "executePlayView"
        const val PGC_ASYNC_METHOD = "playView"

        const val UNITE_V1 = "com.bapis.bilibili.app.playerunite.v1."
        const val UNITE_MOSS_CLASS = UNITE_V1 + "PlayerMoss"
        const val UNITE_REQUEST_CLASS = UNITE_V1 + "PlayViewUniteReq"
        const val UNITE_REPLY_CLASS = UNITE_V1 + "PlayViewUniteReply"
        const val UNITE_SYNC_METHOD = "executePlayViewUnite"
        const val UNITE_ASYNC_METHOD = "playViewUnite"

        const val KMP_MOSS_CLIENT = "kntr.base.moss.api.a"
        const val KMP_MOSS_CALLBACK = "Kh1.h"

        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val BUSINESS_EXCEPTION = "com.bilibili.lib.moss.api.BusinessException"

        const val PLAYURL_APP_PATH = "pgc/player/api/playurl"
        const val PLAYURL_PATH = "pgc/player/web/playurl"
        const val PLAYURL_INTL_PATH = "intl/gateway/v2/ogv/playurl"

        const val SEASON_APP_PATH = "pgc/view/v2/app/season"
        const val SEASON_WEB_PATH = "pgc/view/web/season"
        const val SEASON_INTL_PATH = "intl/gateway/v2/ogv/view/app/season"

        const val DEFAULT_QN = 80L
        const val DEFAULT_FNVAL = 4048L
        const val PLAY_TIMEOUT_MILLIS = 6_000L
        const val TOTAL_BUDGET_MILLIS = 10_000L
        const val STAGGER_MILLIS = 700L
    }
}
