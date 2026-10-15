package io.github.bbzq.feats.hook

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

internal class KotlinMossBridge(
    private val encode: (Any) -> ByteArray,
    private val parse: (ByteArray) -> Any,
    private val serialize: (Any) -> ByteArray,
    private val decode: (ByteArray) -> Any,
) {
    fun transform(value: Any, filter: (Any) -> Any): Any = raw {
        val wire = encode(value)
        val bytes = serialize(parse(wire))
        val edited = filter(parse(wire))
        val updated = serialize(edited)
        if (bytes.contentEquals(updated)) value else decode(updated).also { decoded ->
            check(parse(encode(decoded)) == edited) { "Kotlin Moss reply changed during round trip" }
        }
    }

    companion object {
        const val SERIALIZER = "kotlinx.serialization.SerializationStrategy"
        const val DESERIALIZER = "kotlinx.serialization.DeserializationStrategy"
        const val PROTOBUF = "kotlinx.serialization.protobuf.ProtoBuf"
        private val rawDepth = ThreadLocal<Int>()

        fun isRaw(): Boolean = (rawDepth.get() ?: 0) > 0

        fun <T> raw(block: () -> T): T {
            val previous = rawDepth.get() ?: 0
            rawDepth.set(previous + 1)
            return try {
                block()
            } finally {
                if (previous == 0) rawDepth.remove() else rawDepth.set(previous)
            }
        }

        fun callbackEntry(type: Class<*>, rpc: String): Method? = type.declaredMethods.singleOrNull { method ->
            val args = method.parameterTypes
            method.name == rpc && !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE && args.size == 5 && args[0] == Any::class.java &&
                args[1].name == SERIALIZER && args[2].name == DESERIALIZER &&
                args[3].isInterface && args[4].name == PROTOBUF && hasOnNext(args[3])
        }?.apply { isAccessible = true }

        private fun hasOnNext(type: Class<*>): Boolean = type.methods.any {
            it.name == "onNext" && it.parameterTypes.contentEquals(arrayOf(Any::class.java)) && it.returnType == Void.TYPE
        }

        fun wrap(
            handlerType: Class<*>,
            delegate: Any,
            transform: (Any) -> Any,
            onFailure: (Throwable) -> Unit,
        ): Any? {
            if (!handlerType.isInterface || !handlerType.isInstance(delegate) || !hasOnNext(handlerType)) return null
            return Proxy.newProxyInstance(handlerType.classLoader, arrayOf(handlerType)) { proxy, method, args ->
                if (method.declaringClass == Any::class.java) {
                    when (method.name) {
                        "equals" -> proxy === args?.firstOrNull()
                        "hashCode" -> System.identityHashCode(proxy)
                        else -> "BBZQ Kotlin Moss handler"
                    }
                } else {
                    val forwarded = args?.copyOf() ?: emptyArray()
                    if (method.name == "onNext" && method.parameterCount == 1 && method.returnType == Void.TYPE) {
                        forwarded.firstOrNull()?.let { original ->
                            forwarded[0] = try {
                                transform(original)
                            } catch (failure: Throwable) {
                                runCatching { onFailure(failure) }
                                original
                            }
                        }
                    }
                    invoke { method.invoke(delegate, *forwarded) }
                }
            }
        }

        internal fun invoke(block: () -> Any?): Any? = try {
            block()
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }
}

internal class KotlinMossCodec private constructor(
    private val strategy: Class<*>,
    private val deserialization: Class<*>,
    private val protoBuf: Class<*>,
    private val encode: Method,
    private val decode: Method,
    private val parse: Method,
    private val serialize: Method,
) {
    fun bind(format: Any?, serializer: Any?): KotlinMossBridge? {
        if (!protoBuf.isInstance(format) || !strategy.isInstance(serializer) || !deserialization.isInstance(serializer)) {
            return null
        }
        return KotlinMossBridge(
            encode = { value -> KotlinMossBridge.invoke { encode.invoke(format, serializer, value) } as ByteArray },
            parse = { bytes -> KotlinMossBridge.invoke { parse.invoke(null, bytes) } ?: error("Empty Java reply") },
            serialize = { value -> KotlinMossBridge.invoke { serialize.invoke(value) } as ByteArray },
            decode = { bytes -> KotlinMossBridge.invoke { decode.invoke(format, serializer, bytes) }
                ?: error("Empty Kotlin reply") },
        )
    }

    companion object {
        fun resolve(loader: ClassLoader, replyType: Class<*>): KotlinMossCodec? = runCatching {
            val strategy = Class.forName(KotlinMossBridge.SERIALIZER, false, loader)
            val deserialization = Class.forName(KotlinMossBridge.DESERIALIZER, false, loader)
            val protoBuf = Class.forName(KotlinMossBridge.PROTOBUF, false, loader)
            val encode = protoBuf.getMethod("encodeToByteArray", strategy, Any::class.java)
            val decode = protoBuf.getMethod("decodeFromByteArray", deserialization, ByteArray::class.java)
            val parse = replyType.getMethod("parseFrom", ByteArray::class.java)
            val serialize = replyType.getMethod("toByteArray")
            if (Modifier.isStatic(encode.modifiers) || encode.returnType != ByteArray::class.java ||
                Modifier.isStatic(decode.modifiers) || decode.returnType == Void.TYPE ||
                !Modifier.isStatic(parse.modifiers) || !replyType.isAssignableFrom(parse.returnType) ||
                Modifier.isStatic(serialize.modifiers) || serialize.returnType != ByteArray::class.java
            ) return null
            KotlinMossCodec(strategy, deserialization, protoBuf, encode, decode, parse, serialize)
        }.getOrNull()
    }
}
