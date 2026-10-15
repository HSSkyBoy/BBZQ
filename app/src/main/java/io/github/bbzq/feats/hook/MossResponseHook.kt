package io.github.bbzq.feats.hook

import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean

internal fun RoamingEnv.hookJavaMoss(
    mossName: String,
    rpc: String,
    requestName: String,
    replyName: String,
    enabled: () -> Boolean,
    filter: (Any) -> Any,
): Int {
    val owner = classLoader.findClassOrNull(mossName) ?: return 0
    val request = classLoader.findClassOrNull(requestName) ?: return 0
    val reply = classLoader.findClassOrNull(replyName) ?: return 0
    val failedLogged = AtomicBoolean(false)
    val failure: (Throwable) -> Unit = {
        if (failedLogged.compareAndSet(false, true)) log("Moss response unchanged after failure: $mossName.$rpc", it)
    }
    val transform: (Any) -> Any = { value ->
        if (enabled() && reply.isInstance(value)) filter(value) else value
    }
    var installed = 0
    val methods = owner.declaredMethods.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) }
    methods.singleOrNull {
        it.name == "execute${rpc.replaceFirstChar(Char::uppercaseChar)}" && it.returnType == reply &&
            it.parameterTypes.contentEquals(arrayOf(request))
    }?.let { method ->
        hookAfter(method) { param ->
            val value = param.result ?: return@hookAfter
            runCatching { transform(value) }.onSuccess { param.result = it }.onFailure(failure)
        }
        installed++
    }
    methods.singleOrNull {
        val types = it.parameterTypes
        it.name == rpc && it.returnType == Void.TYPE && types.size == 2 && types[0] == request &&
            types[1].name == "com.bilibili.lib.moss.api.MossResponseHandler" && types[1].isInterface
    }?.let { method ->
        if (hookBefore(method) { param ->
                if (!enabled()) return@hookBefore
                val delegate = param.args.getOrNull(1) ?: return@hookBefore
                KotlinMossBridge.wrap(method.parameterTypes[1], delegate, transform, failure)?.let { param.args[1] = it }
            }) installed++
    }
    return installed
}

internal fun newProtobufBuilder(reply: Any): Any {
    val builder = reply.javaClass.getMethod("newBuilder", reply.javaClass).apply { isAccessible = true }
        .invoke(null, reply) ?: error("Empty protobuf builder")
    check(builder !== reply) { "Protobuf builder reused the original message" }
    return builder
}

internal fun clearProtobufFields(reply: Any, vararg fields: String): Any {
    val builder = newProtobufBuilder(reply)
    fields.forEach { builder.javaClass.getMethod("clear$it").apply { isAccessible = true }.invoke(builder) }
    return builder.javaClass.getMethod("build").apply { isAccessible = true }.invoke(builder)
        ?.takeIf(reply.javaClass::isInstance) ?: error("Unexpected protobuf reply")
}

internal fun copyProtobufReply(reply: Any, edit: (Any) -> Unit): Any = KotlinMossBridge.raw {
    val bytes = reply.javaClass.getMethod("toByteArray").apply { isAccessible = true }.invoke(reply) as ByteArray
    val copy = reply.javaClass.getMethod("parseFrom", ByteArray::class.java).apply { isAccessible = true }.invoke(null, bytes)
        ?.takeIf(reply.javaClass::isInstance) ?: error("Unexpected protobuf copy")
    edit(copy)
    if (copy == reply) reply else copy
}
