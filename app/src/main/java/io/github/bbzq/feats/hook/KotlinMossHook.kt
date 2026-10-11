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
    filter: (Any) -> Any,
): Int {
    val owner = classLoader.findClassOrNull(mossName) ?: return 0
    val replyType = classLoader.findClassOrNull(replyName) ?: return 0
    val entry = KotlinMossBridge.callbackEntry(owner, rpc) ?: return 0
    val codec = KotlinMossCodec.resolve(classLoader, replyType) ?: return 0
    val activeLogged = AtomicBoolean(false)
    val failedLogged = AtomicBoolean(false)
    val installed = hookBefore(entry) { param ->
        if (!enabled()) return@hookBefore
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
