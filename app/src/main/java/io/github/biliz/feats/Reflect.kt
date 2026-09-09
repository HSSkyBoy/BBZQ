package io.github.biliz.feats

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

fun ClassLoader.findClassOrNull(name: String): Class<*>? =
    runCatching { Class.forName(name, false, this) }.getOrNull()

fun String.from(classLoader: ClassLoader): Class<*>? =
    classLoader.findClassOrNull(this)

fun Class<*>.allFields(): Sequence<Field> = sequence {
    var current: Class<*>? = this@allFields
    while (current != null) {
        current.declaredFields.forEach { field ->
            field.isAccessible = true
            yield(field)
        }
        current = current.superclass
    }
}

fun Class<*>.allMethods(): Sequence<Method> = sequence {
    val seen = mutableSetOf<String>()
    var current: Class<*>? = this@allMethods
    while (current != null) {
        current.declaredMethods.forEach { method ->
            val signature = method.name + method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.name }
            if (seen.add(signature)) {
                method.isAccessible = true
                yield(method)
            }
        }
        current = current.superclass
    }
}

fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? =
    runCatching { getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }.getOrNull()
        ?: allMethods().firstOrNull { it.name == name && it.parameterTypes.contentEquals(parameterTypes) }

fun Class<*>.methodsNamed(name: String?): Sequence<Method> =
    allMethods().filter { name == null || it.name == name }

fun Class<*>.fieldOrNull(name: String?): Field? {
    if (name == null) return null
    return allFields().firstOrNull { it.name == name }
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
    val method = javaClass.allMethods().firstOrNull { candidate ->
        candidate.name == name &&
            candidate.parameterCount == args.size &&
            candidate.parameterTypes.indices.all { index ->
                candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
            }
    } ?: return null
    return runCatching { method.invoke(this, *args) }.getOrNull()
}

fun Class<*>.callStaticMethod(name: String?, vararg args: Any?): Any? {
    if (name == null) return null
    val method = allMethods().firstOrNull { candidate ->
        Modifier.isStatic(candidate.modifiers) &&
            candidate.name == name &&
            candidate.parameterCount == args.size &&
            candidate.parameterTypes.indices.all { index ->
                candidate.parameterTypes[index].isAssignableFromBoxed(args[index])
            }
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

