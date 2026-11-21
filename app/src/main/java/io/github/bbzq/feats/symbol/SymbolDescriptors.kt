package io.github.bbzq.feats.symbol

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.json.JSONArray
import org.json.JSONObject

data class NamedClassSymbol(
    val id: String,
    val className: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("className", className)

    companion object {
        fun fromJson(obj: JSONObject): NamedClassSymbol = NamedClassSymbol(
            id = obj.optString("id"),
            className = obj.optString("className"),
        )
    }
}

data class NamedMethodGroup(
    val id: String,
    val methods: List<MethodDescriptor>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("methods", methods.toJsonArray { it.toJson() })

    companion object {
        fun fromJson(obj: JSONObject): NamedMethodGroup = NamedMethodGroup(
            id = obj.optString("id"),
            methods = obj.optJSONArray("methods").toList { MethodDescriptor.fromJson(it) },
        )
    }
}

data class ConstructorDescriptor(
    val declaringClassName: String,
    val parameterTypeNames: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("parameterTypeNames", parameterTypeNames.toJsonArray())

    fun restore(owner: Class<*>): Constructor<*>? {
        if (owner.name != declaringClassName) return null
        return owner.declaredConstructors.firstOrNull { constructor ->
            constructor.parameterTypes.map { it.name } == parameterTypeNames
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(constructor: Constructor<*>): ConstructorDescriptor = ConstructorDescriptor(
            declaringClassName = constructor.declaringClass.name,
            parameterTypeNames = constructor.parameterTypes.map { it.name },
        )

        fun fromJson(obj: JSONObject): ConstructorDescriptor = ConstructorDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            parameterTypeNames = obj.optJSONArray("parameterTypeNames").toStringList(),
        )
    }
}

data class MethodDescriptor(
    val declaringClassName: String,
    val name: String,
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("name", name)
        .put("returnTypeName", returnTypeName)
        .put("parameterTypeNames", parameterTypeNames.toJsonArray())

    fun restore(owner: Class<*>): Method? {
        if (owner.name != declaringClassName) return null
        return owner.declaredMethods.firstOrNull { method ->
            method.name == name &&
                method.returnType.name == returnTypeName &&
                method.parameterTypes.map { it.name } == parameterTypeNames
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(method: Method): MethodDescriptor = MethodDescriptor(
            declaringClassName = method.declaringClass.name,
            name = method.name,
            returnTypeName = method.returnType.name,
            parameterTypeNames = method.parameterTypes.map { it.name },
        )

        fun fromJson(obj: JSONObject): MethodDescriptor = MethodDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            name = obj.optString("name"),
            returnTypeName = obj.optString("returnTypeName"),
            parameterTypeNames = obj.optJSONArray("parameterTypeNames").toStringList(),
        )
    }
}

data class FieldDescriptor(
    val declaringClassName: String,
    val name: String,
    val typeName: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("declaringClassName", declaringClassName)
        .put("name", name)
        .put("typeName", typeName)

    fun restore(owner: Class<*>): Field? {
        if (owner.name != declaringClassName) return null
        return owner.declaredFields.firstOrNull { field ->
            field.name == name && field.type.name == typeName
        }?.apply { isAccessible = true }
    }

    companion object {
        fun of(field: Field): FieldDescriptor = FieldDescriptor(
            declaringClassName = field.declaringClass.name,
            name = field.name,
            typeName = field.type.name,
        )

        fun fromJson(obj: JSONObject): FieldDescriptor = FieldDescriptor(
            declaringClassName = obj.optString("declaringClassName"),
            name = obj.optString("name"),
            typeName = obj.optString("typeName"),
        )
    }
}

internal fun ClassLoader.loadClassOrNull(name: String): Class<*>? =
    runCatching { Class.forName(name, false, this) }.getOrNull()

internal fun MethodDescriptor?.restoreOptional(classLoader: ClassLoader): Method? {
    val descriptor = this ?: return null
    val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return null
    return descriptor.restore(owner)
}

internal fun FieldDescriptor?.restoreOptional(classLoader: ClassLoader): Field? {
    val descriptor = this ?: return null
    val owner = classLoader.loadClassOrNull(descriptor.declaringClassName) ?: return null
    return descriptor.restore(owner)
}

internal fun List<MethodDescriptor>.restoreAll(classLoader: ClassLoader): List<Method>? {
    val methods = mapNotNull { it.restoreOptional(classLoader) }
    return methods.takeIf { it.size == size }
}

internal fun List<MethodDescriptor>.restoreAvailable(classLoader: ClassLoader): List<Method> =
    mapNotNull { it.restoreOptional(classLoader) }

internal fun Method.hasParameterTypes(vararg names: String): Boolean =
    parameterTypes.map { it.name } == names.toList()

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        optString(i).takeIf { it.isNotBlank() }?.let(out::add)
    }
    return out
}

internal fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(mapper(it)) }
    }
    return out
}

internal fun Iterable<String>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach(array::put) }

internal fun <T> Iterable<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { array.put(mapper(it)) } }
