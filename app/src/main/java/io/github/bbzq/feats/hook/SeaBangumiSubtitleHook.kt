package io.github.bbzq.feats.hook

import android.os.Handler
import android.os.Looper
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.SeaResolverClient
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.Executors

/**
 * Adds the subtitles of a resolver-served episode to the danmaku view reply. The official request
 * for a region-limited episode comes back without any, because they belong to the other region.
 * Only runs for the episode the play hook just served, so ordinary videos never reach the resolver.
 */
class SeaBangumiSubtitleHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val executor by lazy { Executors.newCachedThreadPool() }
    private var replyClass: Class<*>? = null

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSeaBangumiSubtitleEnabled(prefs)) return
        val mossClass = classLoader.findClassOrNull(MOSS_CLASS)
        val requestClass = classLoader.findClassOrNull(REQUEST_CLASS)
        replyClass = classLoader.findClassOrNull(REPLY_CLASS)
        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)?.takeIf { it.isInterface }
        if (mossClass == null || requestClass == null || replyClass == null) {
            log("SeaBangumiSubtitle: DmMoss not found on this host")
            return
        }
        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == SYNC_METHOD && it.parameterTypes.contentEquals(arrayOf(requestClass)) && !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.hookAfter(sync) { param ->
                val reply = param.result ?: return@hookAfter
                val request = param.args.firstOrNull() ?: return@hookAfter
                if (Looper.myLooper() == Looper.getMainLooper()) return@hookAfter
                withSubtitles(request, reply)?.let { param.result = it }
            }
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
                    if (SeaBangumiSession.recentFor(request.callMethod("getOid").asLong()) == null) return@hookBefore
                    param.args[1] = wrapHandler(handlerClass, delegate, request)
                }
                installed++
            }
        }
        isInstalled = installed > 0
        log("SeaBangumiSubtitle: hooked $installed dm view method(s)")
    }

    private fun wrapHandler(handlerClass: Class<*>, delegate: Any, request: Any): Any {
        val gate = Gate()
        val callbackLooper = Looper.myLooper()
        return Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val arguments = args ?: emptyArray()
                if (gate.enqueueIfPending { MossCallbacks.invoke(delegate, method, arguments) }) {
                    return@InvocationHandler MossCallbacks.defaultValue(method)
                }
                val reply = arguments.takeIf { method.name == "onNext" && it.size == 1 }?.get(0)
                if (reply == null || !lacksSubtitles(reply)) {
                    MossCallbacks.invoke(delegate, method, arguments)
                } else {
                    gate.start()
                    executor.execute {
                        val replacement = runCatching { withSubtitles(request, reply) }
                            .onFailure { log("SeaBangumiSubtitle: lookup failed", it) }
                            .getOrNull()
                        val deliver = {
                            val result = replacement ?: reply
                            MossCallbacks.invokeByName(delegate, handlerClass, "onNext", ::log, result)
                            gate.finish()
                        }
                        if (callbackLooper != null) Handler(callbackLooper).post(deliver) else deliver()
                    }
                }
                MossCallbacks.defaultValue(method)
            },
        )
    }

    private fun lacksSubtitles(reply: Any): Boolean =
        ((reply.callMethod("getSubtitle")?.callMethod("getSubtitlesCount") as? Number)?.toInt() ?: 0) == 0

    /** [reply] with the resolver subtitles merged in, or null when there is nothing to add. */
    private fun withSubtitles(request: Any, reply: Any): Any? {
        if (!lacksSubtitles(reply)) return null
        val oid = request.callMethod("getOid").asLong()
        val served = SeaBangumiSession.recentFor(oid) ?: return null
        val pid = request.callMethod("getPid").asLong()
        val servers = ResolverServers.preferring(ResolverServers.configured(prefs), served.region)
        val params = mutableListOf(
            "aid" to pid.toString(),
            "cid" to (if (oid > 0) oid else served.cid).toString(),
            "ep_id" to served.epId.toString(),
        )
        if (ModuleSettings.isSeaResolverSendAccessKeyEnabled(prefs)) {
            prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)?.takeIf { it.isNotBlank() }
                ?.let { params += "access_key" to it }
        }
        for (server in servers) {
            val body = SeaResolverClient(server.baseUrl, ::log).get(PLAYER_PATH, params) ?: continue
            val entries = runCatching { SeaBangumiSubtitleCodec.parse(body) }.getOrDefault(emptyList())
            if (entries.isEmpty()) continue
            val subtitle = SeaBangumiSubtitleCodec.encodeSubtitle(entries) ?: continue
            val original = reply.callMethod("toByteArray") as? ByteArray ?: return null
            val bytes = SeaBangumiSubtitleCodec.replaceSubtitle(original, subtitle) ?: return null
            log("SeaBangumiSubtitle: ${server.region} supplied ${entries.size} subtitle track(s) for ep ${served.epId}")
            return replyClass!!.getMethod("parseFrom", ByteArray::class.java).invoke(null, bytes)
        }
        return null
    }

    private fun Any?.asLong(): Long = (this as? Number)?.toLong() ?: 0L

    private companion object {
        const val V1 = "com.bapis.bilibili.community.service.dm.v1."
        const val MOSS_CLASS = V1 + "DmMoss"
        const val REQUEST_CLASS = V1 + "DmViewReq"
        const val REPLY_CLASS = V1 + "DmViewReply"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val SYNC_METHOD = "executeDmView"
        const val ASYNC_METHOD = "dmView"
        const val PLAYER_PATH = "x/player/v2"
    }
}
