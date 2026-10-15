package io.github.bbzq.feats.hook

import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.util.concurrent.atomic.AtomicBoolean

internal fun RoamingEnv.hookKotlinMoss(
    mossName: String,
    rpc: String,
    replyName: String,
    enabled: () -> Boolean,
    requestName: String? = null,
    requestFilter: ((Any) -> Any)? = null,
    filter: (Any) -> Any,
): Int {
    val owner = classLoader.findClassOrNull(mossName) ?: return 0
    val replyType = classLoader.findClassOrNull(replyName) ?: return 0
    val entry = KotlinMossBridge.callbackEntry(owner, rpc) ?: return 0
    val codec = KotlinMossCodec.resolve(classLoader, replyType) ?: return 0
    val requestCodec = requestName?.let(classLoader::findClassOrNull)?.let { KotlinMossCodec.resolve(classLoader, it) }
    val activeLogged = AtomicBoolean(false)
    val failedLogged = AtomicBoolean(false)
    val requestFailedLogged = AtomicBoolean(false)
    val installed = hookBefore(entry) { param ->
        if (!enabled()) return@hookBefore
        if (requestFilter != null && requestCodec != null) {
            val request = param.args.getOrNull(0)
            val requestBridge = requestCodec.bind(param.args.getOrNull(4), param.args.getOrNull(1))
            if (request != null && requestBridge != null) {
                runCatching { requestBridge.transform(request, requestFilter) }
                    .onSuccess { param.args[0] = it }
                    .onFailure { failure ->
                        if (requestFailedLogged.compareAndSet(false, true)) log("Kotlin Moss request unchanged after failure: $mossName.$rpc", failure)
                    }
            }
        }
        val delegate = param.args.getOrNull(3) ?: return@hookBefore
        val bridge = codec.bind(param.args.getOrNull(4), param.args.getOrNull(2)) ?: return@hookBefore
        val proxy = KotlinMossBridge.wrap(entry.parameterTypes[3], delegate, { value ->
            if (!enabled()) value else bridge.transform(value, filter).also {
                if (activeLogged.compareAndSet(false, true)) log("Kotlin Moss response active: $mossName.$rpc")
            }
        }, { failure ->
            if (failedLogged.compareAndSet(false, true)) log("Kotlin Moss response unchanged after failure: $mossName.$rpc", failure)
        }) ?: return@hookBefore
        param.args[3] = proxy
    }
    if (installed) log("Kotlin Moss hook installed: $mossName.$rpc")
    return if (installed) 1 else 0
}
