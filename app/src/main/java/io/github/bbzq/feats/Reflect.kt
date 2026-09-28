package io.github.bbzq.feats

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

fun ClassLoader.findClassOrNull(name: String): Class<*>? =
    runCatching { Class.forName(name, false, this) }.getOrNull()

fun String.from(classLoader: ClassLoader): Class<*>? =
    classLoader.findClassOrNull(this)

private class ReflectSentinel {
    @JvmField var dummyField: Int = 0
    fun dummyMethod() {}
}

private val NULL_FIELD: Field = ReflectSentinel::class.java.getDeclaredField("dummyField")
private val NULL_METHOD: Method = ReflectSentinel::class.java.getDeclaredMethod("dummyMethod")

private val classFieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()
private val classMethodsCache = ConcurrentHashMap<Class<*>, List<Method>>()

private data class FieldKey(val clazz: Class<*>, val name: String)
private val fieldCache = ConcurrentHashMap<FieldKey, Field>()

private data class MethodCallKey(
    val clazz: Class<*>,
    val name: String,
    val argCount: Int,
    val argTypes: List<Class<*>?>,
)
private val methodCallCache = ConcurrentHashMap<MethodCallKey, Method>()

private data class MethodExactKey(
    val clazz: Class<*>,
    val name: String,
    val paramTypes: List<Class<*>>,
)
private val methodExactCache = ConcurrentHashMap<MethodExactKey, Method>()

fun Class<*>.allFieldsList(): List<Field> =
    classFieldsCache.getOrPut(this) {
        val fields = mutableListOf<Field>()
        var current: Class<*>? = this
        while (current != null && current != Any::class.java) {
            current.declaredFields.forEach { field ->
                runCatching { field.isAccessible = true }
                fields.add(field)
            }
            current = current.superclass
        }
        fields
    }

fun Class<*>.allFields(): Sequence<Field> = allFieldsList().asSequence()

fun Class<*>.allMethodsList(): List<Method> =
    classMethodsCache.getOrPut(this) {
        val methods = mutableListOf<Method>()
        val seen = mutableSetOf<String>()
        var current: Class<*>? = this
        while (current != null && current != Any::class.java) {
            current.declaredMethods.forEach { method ->
                val signature = method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
                if (seen.add(signature)) {
                    runCatching { method.isAccessible = true }
                    methods.add(method)
                }
            }
            current = current.superclass
        }
        methods
    }

fun Class<*>.allMethods(): Sequence<Method> = allMethodsList().asSequence()

fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
    val key = MethodExactKey(this, name, if (parameterTypes.isEmpty()) emptyList() else parameterTypes.toList())
    val cached = methodExactCache[key]
    if (cached != null) {
        return if (cached === NULL_METHOD) null else cached
    }
    val method = runCatching { getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()
        ?: allMethodsList().firstOrNull { it.name == name && it.parameterTypes.contentEquals(parameterTypes) }
    methodExactCache[key] = method ?: NULL_METHOD
    return method
}

fun Class<*>.methodsNamed(name: String?): Sequence<Method> =
    allMethods().filter { name == null || it.name == name }

fun Class<*>.fieldOrNull(name: String?): Field? {
    if (name == null) return null
    val key = FieldKey(this, name)
    val cached = fieldCache[key]
    if (cached != null) {
        return if (cached === NULL_FIELD) null else cached
    }
    val field = allFieldsList().firstOrNull { it.name == name }
    fieldCache[key] = field ?: NULL_FIELD
    return field
}

fun Any.getObjectField(name: String?): Any? =
    javaClass.fieldOrNull(name)?.let { runCatching { it.get(this) }.getOrNull() }

fun Class<*>.getStaticObjectField(name: String?): Any? =
    fieldOrNull(name)?.let { runCatching { it.get(null) }.getOrNull() }

fun Any.setObjectField(name: String?, value: Any?): Boolean {
    val field = javaClass.fieldOrNull(name) ?: return false
    if (!field.type.isAssignableFromBoxed(value)) return false
    return runCatching {
        field.set(this, value)
        true
    }.getOrDefault(false)
}

fun Any.setBooleanField(name: String?, value: Boolean): Boolean {
    val field = javaClass.fieldOrNull(name) ?: return false
    if (field.type != Boolean::class.javaPrimitiveType && field.type != Boolean::class.javaObjectType) return false
    return runCatching {
        field.set(this, value)
        true
    }.getOrDefault(false)
}

fun Any.setIntField(name: String?, value: Int): Boolean {
    val field = javaClass.fieldOrNull(name) ?: return false
    if (field.type != Int::class.javaPrimitiveType && field.type != Int::class.javaObjectType) return false
    return runCatching {
        field.set(this, value)
        true
    }.getOrDefault(false)
}

fun Any.callMethod(name: String?, vararg args: Any?): Any? {
    if (name == null) return null
    val targetClass = javaClass
    val argCount = args.size
    val argTypes = if (argCount == 0) emptyList() else args.map { it?.javaClass }
    val key = MethodCallKey(targetClass, name, argCount, argTypes)
    val cached = methodCallCache[key]
    val method = if (cached != null) {
        if (cached === NULL_METHOD) null else cached
    } else {
        val resolved = targetClass.allMethodsList().firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterCount == argCount &&
                candidate.parameterTypes.indices.all { index ->
                    candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
                }
        }
        methodCallCache[key] = resolved ?: NULL_METHOD
        resolved
    } ?: return null
    return runCatching { method.invoke(this, *args) }.getOrNull()
}

fun Class<*>.callStaticMethod(name: String?, vararg args: Any?): Any? {
    if (name == null) return null
    val argCount = args.size
    val argTypes = if (argCount == 0) emptyList() else args.map { it?.javaClass }
    val key = MethodCallKey(this, name, argCount, argTypes)
    val cached = methodCallCache[key]
    val method = if (cached != null) {
        if (cached === NULL_METHOD) null else cached
    } else {
        val resolved = allMethodsList().firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.name == name &&
                candidate.parameterCount == argCount &&
                candidate.parameterTypes.indices.all { index ->
                    candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
                }
        }
        methodCallCache[key] = resolved ?: NULL_METHOD
        resolved
    } ?: return null
    return runCatching { method.invoke(null, *args) }.getOrNull()
}

fun Class<*>.newInstanceOrNull(vararg args: Any?): Any? {
    val constructor = declaredConstructors.firstOrNull { candidate ->
        candidate.parameterCount == args.size &&
            candidate.parameterTypes.indices.all { index ->
                candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
            }
    } ?: return null
    return runCatching {
        constructor.isAccessible = true
        constructor.newInstance(*args)
    }.getOrNull()
}

fun Class<*>.isAssignableFromBoxed(value: Any?): Boolean {
    if (value == null) return !isPrimitive
    if (isInstance(value)) return true
    return primitiveWrapper()?.isInstance(value) == true
}

private fun Class<*>.primitiveWrapper(): Class<*>? = when (this) {
    Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
    Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
    Short::class.javaPrimitiveType -> Short::class.javaObjectType
    Int::class.javaPrimitiveType -> Int::class.javaObjectType
    Long::class.javaPrimitiveType -> Long::class.javaObjectType
    Float::class.javaPrimitiveType -> Float::class.javaObjectType
    Double::class.javaPrimitiveType -> Double::class.javaObjectType
    Char::class.javaPrimitiveType -> Char::class.javaObjectType
    else -> null
}
