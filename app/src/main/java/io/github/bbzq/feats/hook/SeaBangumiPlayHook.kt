package io.github.bbzq.feats.hook

import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.SeaResolverClient
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.intercept
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

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

    private fun resolve(request: Any, original: Any?): Any? {
        val (epId, seasonId) = resolveTarget(request) ?: return null
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), servedBy[epId])
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
        val started = System.currentTimeMillis()
        for (server in servers) {
            if (System.currentTimeMillis() - started > TOTAL_BUDGET_MILLIS) {
                log("SeaBangumiPlay: out of time before asking ${server.region}")
                break
            }
            val body = SeaResolverClient(server.baseUrl, ::log)
                .get(PLAYURL_PATH, params, timeoutMillis = PLAY_TIMEOUT_MILLIS) ?: continue
            val reply = runCatching {
                val result = SeaBangumiPlayCodec.playableResult(body)
                if (result == null) {
                    // A code such as -10493 only means "not available in this region": try the next one.
                    log("SeaBangumiPlay: ${server.region} has no stream for ep $epId (code ${SeaBangumiPlayCodec.answerCode(body)})")
                    return@runCatching null
                }
                val originalBytes = (original?.callMethod("toByteArray") as? ByteArray) ?: ByteArray(0)
                val relax = ModuleSettings.isSeaRelaxPlayLimitsEnabled(prefs)
                val bytes = SeaBangumiPlayCodec.buildReply(originalBytes, result, relax) ?: return@runCatching null
                replyClass!!.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
            }.getOrElse {
                log("SeaBangumiPlay: could not build the substitute reply from ${server.region}", it)
                null
            }
            if (reply != null) {
                servedBy[epId] = server.region
                log("SeaBangumiPlay: ${server.region} served ep $epId season $seasonId")
                return reply
            }
        }
        return null
    }

    private fun resolveTarget(request: Any): Pair<Long, Long>? {
        val epId = request.callMethod("getEpId").asLong()
        if (epId <= 0) return null
        return epId to request.callMethod("getSeasonId").asLong()
    }

    private fun isRegionBlocked(reply: Any): Boolean {
        val streams = (reply.callMethod("getVideoInfo")?.callMethod("getStreamListCount") as? Number)?.toInt() ?: 0
        if (streams > 0) return false
        val dialog = reply.callMethod("getViewInfo")?.callMethod("getDialog")
        val code = dialog?.callMethod("getCode").asLong().toInt()
        val message = dialog?.callMethod("getMsg") as? String
        val blocked = SeaBangumiPlayCodec.isRegionBlocked(code, message)
        if (!blocked) log("SeaBangumiPlay: reply without streams is not a region refusal (dialog code=$code)")
        return blocked
    }

    private fun isRegionError(error: Throwable): Boolean {
        val code = ((error as Any).callMethod("getCode") as? Number)?.toInt() ?: 0
        val blocked = SeaBangumiPlayCodec.isRegionBlocked(code, error.message)
        if (!blocked) log("SeaBangumiPlay: play error code=$code is not a region refusal")
        return blocked
    }

    private fun invoke(target: Any, method: Method, args: Array<Any?>): Any? =
        try {
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }

    private fun invokeByName(target: Any, type: Class<*>, name: String, vararg args: Any?) {
        val method = type.methods.firstOrNull { it.name == name && it.parameterCount == args.size } ?: return
        runCatching { invoke(target, method, arrayOf(*args)) }
            .onFailure { log("SeaBangumiPlay: handler.$name failed", it) }
    }

    private fun defaultValue(method: Method): Any? = when (method.returnType) {
        java.lang.Long.TYPE -> 0L
        java.lang.Integer.TYPE -> 0
        java.lang.Boolean.TYPE -> false
        else -> null
    }

    private fun Any?.asLong(): Long = (this as? Number)?.toLong() ?: 0L

    private sealed interface Trigger {
        class Reply(val reply: Any) : Trigger
        class Error(val error: Any) : Trigger
    }

    /** Holds back callbacks that arrive while a resolver lookup is in flight. */
    private class Gate {
        private val queue = ArrayList<() -> Unit>()
        private var pending = false

        @Synchronized
        fun enqueueIfPending(task: () -> Unit): Boolean {
            if (!pending) return false
            queue += task
            return true
        }

        @Synchronized
        fun start() {
            pending = true
        }

        fun finish() {
            while (true) {
                val next = synchronized(this) {
                    if (queue.isEmpty()) {
                        pending = false
                        null
                    } else {
                        queue.removeAt(0)
                    }
                } ?: return
                runCatching(next)
            }
        }
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
        const val TOTAL_BUDGET_MILLIS = 15_000L
    }
}
