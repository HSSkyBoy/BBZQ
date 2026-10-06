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
 */
class SeaBangumiPlayHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val executor by lazy { Executors.newCachedThreadPool() }
    private var replyClass: Class<*>? = null

    /** Region that last served an episode, asked first next time. */
    private val servedBy = ConcurrentHashMap<Long, ResolverRegion>()

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSeaBangumiPlayEnabled(prefs)) return
        val mossClass = classLoader.findClassOrNull(MOSS_CLASS)
        val requestClass = classLoader.findClassOrNull(REQUEST_CLASS)
        replyClass = classLoader.findClassOrNull(REPLY_CLASS)
        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)?.takeIf { it.isInterface }
        if (mossClass == null || requestClass == null || replyClass == null) {
            log("SeaBangumiPlay: PGC PlayURLMoss not found on this host")
            return
        }
        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == SYNC_METHOD && it.parameterTypes.contentEquals(arrayOf(requestClass)) && !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.intercept(sync) { chain -> proceedWithFallback(chain.getArgs().firstOrNull(), chain) }
            installed++
        }
        if (handlerClass != null) {
            mossClass.declaredMethods.firstOrNull {
                it.name == ASYNC_METHOD &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val request = param.args.firstOrNull() ?: return@hookBefore
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    if (resolveTarget(request) == null) return@hookBefore
                    param.args[1] = wrapHandler(handlerClass, delegate, request)
                }
                installed++
            }
        }
        isInstalled = installed > 0
        log("SeaBangumiPlay: hooked $installed play method(s)")
    }

    private fun proceedWithFallback(request: Any?, chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
        val args = chain.getArgs().toTypedArray()
        val reply = try {
            chain.proceed(args)
        } catch (error: Throwable) {
            val cause = generateSequence(error) { it.cause }.firstOrNull { it.javaClass.name == BUSINESS_EXCEPTION }
            if (request != null && cause != null && isRegionError(cause)) {
                resolve(request, null)?.let { return it }
            }
            throw error
        }
        if (request != null && reply != null && isRegionBlocked(reply)) {
            resolve(request, reply)?.let { return it }
        }
        return reply
    }

    /** Defers the host callbacks that follow a region refusal until the resolver has answered. */
    private fun wrapHandler(handlerClass: Class<*>, delegate: Any, request: Any): Any {
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
                    method.name == "onNext" && arguments.size == 1 -> arguments[0]?.takeIf(::isRegionBlocked)
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
                            resolve(request, (trigger as? Trigger.Reply)?.reply)
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

    /**
     * Asks the configured regions at the same time and takes the first one that can play the
     * episode. When a region is already known (served before, or the user's default) it goes first
     * and the others follow a moment later, so a quick answer from it spares the rest.
     */
    private fun resolve(request: Any, original: Any?): Any? {
        val (epId, seasonId) = resolveTarget(request) ?: return null
        val preferred = servedBy[epId] ?: ModuleSettings.getResolverDefaultRegion(prefs)
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), preferred)
        if (servers.isEmpty()) return null
        val params = mutableListOf(
            "ep_id" to epId.toString(),
            "qn" to (request.callMethod("getQn").asLong().takeIf { it > 0 } ?: DEFAULT_QN).toString(),
            "fnval" to (request.callMethod("getFnval").asLong().takeIf { it > 0 } ?: DEFAULT_FNVAL).toString(),
            "fnver" to request.callMethod("getFnver").asLong().toString(),
            "fourk" to request.callMethod("getFourk").let { if (it == true || (it as? Number)?.toInt() == 1) "1" else "0" },
        )
        if (ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)) {
            prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)?.takeIf { it.isNotBlank() }
                ?.let { params += "access_key" to it }
        }
        val originalBytes = (original?.callMethod("toByteArray") as? ByteArray) ?: ByteArray(0)
        val relax = ModuleSettings.isSeaRelaxPlayLimitsEnabled(prefs)
        val headers = HostAccountCookies.headers(env.hostContext, ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs))
        val completion = ExecutorCompletionService<Pair<ResolverServer, Any?>>(executor)
        val tasks = servers.mapIndexed { index, server ->
            val delay = if (preferred != null && index > 0) STAGGER_MILLIS else 0L
            completion.submit { server to fetchReply(server, params, headers, originalBytes, relax, epId, delay) }
        }
        val deadline = System.currentTimeMillis() + TOTAL_BUDGET_MILLIS
        try {
            repeat(servers.size) {
                val left = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                val finished = completion.poll(left, TimeUnit.MILLISECONDS) ?: run {
                    log("SeaBangumiPlay: out of time waiting for the resolvers")
                    return null
                }
                val (server, reply) = runCatching { finished.get() }.getOrNull() ?: return@repeat
                if (reply != null) {
                    servedBy[epId] = server.region
                    SeaBangumiSession.record(epId, request.callMethod("getCid").asLong(), server.region)
                    log("SeaBangumiPlay: ${server.region} served ep $epId season $seasonId")
                    return reply
                }
            }
            return null
        } finally {
            tasks.forEach { it.cancel(true) }
        }
    }

    /** One region's reply for the episode, or null when it has no playable stream. */
    private fun fetchReply(
        server: ResolverServer,
        params: List<Pair<String, String>>,
        headers: Headers,
        originalBytes: ByteArray,
        relaxPlayLimits: Boolean,
        epId: Long,
        delayMillis: Long,
    ): Any? {
        if (delayMillis > 0) Thread.sleep(delayMillis)
        val body = SeaResolverClient(server.baseUrl, ::log)
            .get(PLAYURL_PATH, params, headers, timeoutMillis = PLAY_TIMEOUT_MILLIS) ?: return null
        return runCatching {
            val result = SeaBangumiPlayCodec.playableResult(body)
            if (result == null) {
                // A code such as -10493 only means "not available in this region".
                log("SeaBangumiPlay: ${server.region} has no stream for ep $epId (code ${SeaBangumiPlayCodec.answerCode(body)})")
                return@runCatching null
            }
            val cdnHost = ModuleSettings.getResolverCdnHost(prefs, server.region)
            val bytes = SeaBangumiPlayCodec.buildReply(originalBytes, result, relaxPlayLimits, cdnHost)
                ?: return@runCatching null
            replyClass!!.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        }.getOrElse {
            log("SeaBangumiPlay: could not build the substitute reply from ${server.region}", it)
            null
        }
    }

    private fun resolveTarget(request: Any): Pair<Long, Long>? {
        val epId = request.callMethod("getEpId").asLong()
        if (epId <= 0) return null
        return epId to request.callMethod("getSeasonId").asLong()
    }

    private fun isRegionBlocked(reply: Any): Boolean {
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

    /** Any business error of the play call may be a refusal; network failures are not. */
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
        const val V2 = "com.bapis.bilibili.pgc.gateway.player.v2."
        const val MOSS_CLASS = V2 + "PlayURLMoss"
        const val REQUEST_CLASS = V2 + "PlayViewReq"
        const val REPLY_CLASS = V2 + "PlayViewReply"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val BUSINESS_EXCEPTION = "com.bilibili.lib.moss.api.BusinessException"
        const val SYNC_METHOD = "executePlayView"
        const val ASYNC_METHOD = "playView"
        const val PLAYURL_PATH = "pgc/player/web/playurl"
        const val DEFAULT_QN = 80L
        const val DEFAULT_FNVAL = 4048L
        const val PLAY_TIMEOUT_MILLIS = 6_000L
        const val TOTAL_BUDGET_MILLIS = 10_000L
        const val STAGGER_MILLIS = 700L
    }
}
