package io.github.bbzq.feats.hook

import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.AppSignature
import io.github.bbzq.feats.ResolverRegion
import io.github.bbzq.feats.ResolverServer
import io.github.bbzq.feats.ResolverServers
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.SeaResolverClient
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.getStaticObjectField
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import okhttp3.Headers
import org.json.JSONObject
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Adds Southeast Asia bangumi hits from a resolver server to the first page of the
 * comprehensive search and the bangumi tab. The resolver query starts together with the
 * official request, so the wait only covers whatever the resolver takes beyond it.
 */
class SeaBangumiSearchHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val executor by lazy { Executors.newCachedThreadPool() }

    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isSeaBangumiSearchEnabled(prefs)) return
        val mossClass = classLoader.findClassOrNull(MOSS_CLASS) ?: run {
            log("SeaBangumiSearch: SearchMoss not found on this host")
            return
        }
        val installed = installSearch(mossClass, "executeSearchAll", "searchAll", ALL_REQUEST, ALL_ITEM_FIELD, "getItemList") { request ->
            firstPageKeyword(request)
        } + installSearch(mossClass, "executeSearchByType", "searchByType", BY_TYPE_REQUEST, BY_TYPE_ITEM_FIELD, "getItemsList") { request ->
            firstPageKeyword(request)?.takeIf { request.callMethod("getType") == BANGUMI_SEARCH_TYPE }
        }
        isInstalled = installed > 0
        log("SeaBangumiSearch: hooked $installed search method(s)")
    }

    private fun installSearch(
        mossClass: Class<*>,
        syncName: String,
        asyncName: String,
        requestClassName: String,
        itemField: Int,
        itemsGetter: String,
        keywordOf: (Any) -> String?,
    ): Int {
        val requestClass = classLoader.findClassOrNull(requestClassName) ?: return 0
        var installed = 0
        mossClass.declaredMethods.firstOrNull {
            it.name == syncName &&
                it.parameterTypes.contentEquals(arrayOf(requestClass)) &&
                !Modifier.isStatic(it.modifiers)
        }?.let { sync ->
            env.hookAfter(sync) { param ->
                val response = param.result ?: return@hookAfter
                val keyword = param.args.firstOrNull()?.let(keywordOf) ?: return@hookAfter
                val pending = query(keyword) ?: return@hookAfter
                merge(response, itemField, itemsGetter, pending)?.let { param.result = it }
            }
            installed++
        }

        val handlerClass = classLoader.findClassOrNull(MOSS_HANDLER)
        if (handlerClass != null && handlerClass.isInterface) {
            mossClass.declaredMethods.firstOrNull {
                it.name == asyncName &&
                    it.parameterTypes.contentEquals(arrayOf(requestClass, handlerClass)) &&
                    it.returnType == Void.TYPE &&
                    !Modifier.isStatic(it.modifiers)
            }?.let { async ->
                env.hookBefore(async) { param ->
                    val delegate = param.args.getOrNull(1) ?: return@hookBefore
                    val keyword = param.args.firstOrNull()?.let(keywordOf) ?: return@hookBefore
                    val pending = query(keyword) ?: return@hookBefore
                    param.args[1] = wrapHandler(handlerClass, delegate) { merge(it, itemField, itemsGetter, pending) }
                }
                installed++
            }
        }
        return installed
    }

    private fun firstPageKeyword(request: Any): String? {
        val next = request.callMethod("getPagination")?.callMethod("getNext") as? String
        if (!next.isNullOrEmpty()) return null
        return (request.callMethod("getKeyword") as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun query(keyword: String): Pending? {
        val servers = ResolverServers.configured(prefs)
        if (servers.isEmpty()) return null
        val host = readHostIdentity()
        val deadline = SystemClock.uptimeMillis() + WAIT_MILLIS
        val tasks = servers.map { server ->
            executor.submit<List<JSONObject>> { fetch(server, keyword, host) }
        }
        val combined = FutureTask {
            val seen = HashSet<Long>()
            tasks.flatMap { task ->
                val left = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
                runCatching { task.get(left, TimeUnit.MILLISECONDS) }.getOrElse {
                    task.cancel(true)
                    emptyList()
                }
            }.filter { seen.add(it.optLong("season_id")) }
        }
        executor.execute(combined)
        return Pending(combined, deadline)
    }

    /** One region's hits through the app's signed search API; the SEA server also gets its region hint. */
    private fun fetch(server: ResolverServer, keyword: String, host: HostIdentity?): List<JSONObject> {
        val params = mutableMapOf(
            "build" to (host?.identity?.build ?: 0).toString(),
            "mobi_app" to (host?.identity?.mobiApp ?: "android"),
            "platform" to "android",
            "ts" to (System.currentTimeMillis() / 1000).toString(),
            "keyword" to keyword,
            "search_type" to "media_bangumi",
            "type" to BANGUMI_SEARCH_TYPE.toString(),
            "page" to "1",
            "pagesize" to "20",
        )
        host?.identity?.buvid?.takeIf { it.isNotEmpty() }?.let { params["buvid"] = it }
        val headers = identityHeaders(host).newBuilder().apply {
            if (server.region == ResolverRegion.SEA) add("x-resolver-mode", ModuleSettings.getSeaResolverMode(prefs))
        }.build()
        val body = SeaResolverClient(server.baseUrl, ::log).get(
            SEARCH_PATH,
            emptyList(),
            headers,
            WAIT_MILLIS,
            encodedQuery = AppSignature.signedQuery(params),
        ) ?: return emptyList()
        return runCatching { SeaBangumiSearchCodec.parseMatches(body) }.getOrElse {
            log("SeaBangumiSearch: ${server.region} answer was not valid JSON", it)
            emptyList()
        }
    }

    private class HostIdentity(
        val identity: SeaBangumiSearchCodec.ClientIdentity,
        val network: ByteArray,
        val userAgent: String?,
    )

    private fun readHostIdentity(): HostIdentity? = runCatching {
        val helper = classLoader.findClassOrNull(RUNTIME_HELPER)?.getStaticObjectField("INSTANCE") ?: return@runCatching null
        HostIdentity(
            identity = SeaBangumiSearchCodec.ClientIdentity(
                appId = helper.callMethod("appId") as? Int ?: 1,
                build = helper.callMethod("build") as? Int ?: 0,
                buvid = helper.callMethod("buvid") as? String ?: "",
                mobiApp = helper.callMethod("mobiApp") as? String ?: "android",
                device = helper.callMethod("device") as? String ?: "phone",
                channel = helper.callMethod("channel") as? String ?: "",
                versionName = helper.callMethod("versionName") as? String ?: "",
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                osver = Build.VERSION.RELEASE.orEmpty(),
            ),
            network = helper.callMethod("reqNetwork")?.callMethod("toByteArray") as? ByteArray
                ?: SeaBangumiSearchCodec.encodeNetwork(helper.callMethod("net") as? Int ?: 0),
            userAgent = (helper.callMethod("ua") as? String)?.takeIf { it.isNotBlank() },
        )
    }.onFailure { log("SeaBangumiSearch: could not read host identity", it) }.getOrNull()

    /** Mirrors the identity headers the host's own Moss requests carry, minus the access key. */
    private fun identityHeaders(host: HostIdentity?): Headers {
        val builder = Headers.Builder()
        if (host != null) {
            builder.add("x-bili-device-bin", encode(SeaBangumiSearchCodec.encodeDevice(host.identity)))
            builder.add("x-bili-metadata-bin", encode(SeaBangumiSearchCodec.encodeMetadata(host.identity)))
            builder.add("x-bili-network-bin", encode(host.network))
            if (host.identity.buvid.isNotEmpty()) builder.addUnsafeNonAscii("buvid", host.identity.buvid)
            host.userAgent?.let { builder.addUnsafeNonAscii("User-Agent", it) }
        }
        return builder.build()
    }

    private fun merge(response: Any, itemField: Int, itemsGetter: String, pending: Pending): Any? {
        val remaining = pending.deadline - SystemClock.uptimeMillis()
        if (!pending.task.isDone && (remaining <= 0 || Looper.myLooper() == Looper.getMainLooper())) {
            pending.task.cancel(true)
            log("SeaBangumiSearch: resolver too slow, skipped")
            return null
        }
        val entries = runCatching { pending.task.get(remaining.coerceAtLeast(0), TimeUnit.MILLISECONDS) }
            .getOrElse {
                pending.task.cancel(true)
                log("SeaBangumiSearch: resolver result unavailable", it)
                return null
            }
        if (entries.isEmpty()) return null
        val known = existingSeasonIds(response, itemsGetter)
        val fresh = entries.filter { it.optLong("season_id") !in known }
        if (fresh.isEmpty()) return null
        return runCatching {
            val original = response.callMethod("toByteArray") as ByteArray
            // Repeated fields keep wire order, so leading occurrences become the first items.
            val merged = SeaBangumiSearchCodec.encodeItems(itemField, fresh) + original
            response.javaClass.getMethod("parseFrom", ByteArray::class.java).invoke(null, merged)
        }.onSuccess {
            log("SeaBangumiSearch: added ${fresh.size} item(s)")
        }.onFailure {
            log("SeaBangumiSearch: could not merge resolver items", it)
        }.getOrNull()
    }

    private fun existingSeasonIds(response: Any, itemsGetter: String): Set<Long> =
        (response.callMethod(itemsGetter) as? List<*>).orEmpty().mapNotNullTo(HashSet()) { item ->
            if (item?.callMethod("getCardItemCase")?.toString() != "BANGUMI") return@mapNotNullTo null
            item.callMethod("getBangumi")?.callMethod("getSeasonId") as? Long
        }

    private fun wrapHandler(handlerClass: Class<*>, delegate: Any, transform: (Any) -> Any?): Any =
        Proxy.newProxyInstance(
            handlerClass.classLoader,
            arrayOf(handlerClass),
            InvocationHandler { _, method, args ->
                val forwarded = arrayOfNulls<Any?>(args?.size ?: 0)
                args?.forEachIndexed { index, value -> forwarded[index] = value }
                if (method.name == "onNext" && forwarded.size == 1) {
                    forwarded[0]?.let { value ->
                        runCatching { transform(value) }
                            .onFailure { log("SeaBangumiSearch async transform failed", it) }
                            .getOrNull()
                            ?.let { forwarded[0] = it }
                    }
                }
                try {
                    method.invoke(delegate, *forwarded)
                } catch (e: InvocationTargetException) {
                    throw e.targetException ?: e
                }
            },
        )

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private class Pending(val task: Future<List<JSONObject>>, val deadline: Long)

    private companion object {
        const val V1 = "com.bapis.bilibili.polymer.app.search.v1."
        const val MOSS_CLASS = V1 + "SearchMoss"
        const val ALL_REQUEST = V1 + "SearchAllRequest"
        const val BY_TYPE_REQUEST = V1 + "SearchByTypeRequest"
        const val MOSS_HANDLER = "com.bilibili.lib.moss.api.MossResponseHandler"
        const val RUNTIME_HELPER = "com.bilibili.lib.moss.utils.RuntimeHelper"
        const val ALL_ITEM_FIELD = 4
        const val BY_TYPE_ITEM_FIELD = 6
        const val BANGUMI_SEARCH_TYPE = 7
        const val SEARCH_PATH = "x/v2/search/type"
        const val WAIT_MILLIS = 6_000L
    }
}
