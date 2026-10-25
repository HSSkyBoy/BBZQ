package io.github.bbzq.feats.symbol

import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

data class HomeFrameSymbols(
    val constructors: List<ConstructorDescriptor>,
    val lists: Map<String, FieldDescriptor>,
    val itemFields: Map<String, FieldDescriptor>,
    val evidence: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("constructors", JSONArray().apply { constructors.forEach { put(it.toJson()) } })
        .put("lists", JSONObject().apply { lists.forEach { (name, field) -> put(name, field.toJson()) } })
        .put("itemFields", JSONObject().apply { itemFields.forEach { (name, field) -> put(name, field.toJson()) } })
        .put("evidence", evidence)

    fun restore(loader: ClassLoader): RestoredHomeFrameSymbols? {
        if (constructors.isEmpty() || !lists.keys.containsAll(listOf("tab", "bottom")) ||
            !itemFields.keys.containsAll(listOf("id", "name", "uri", "default_selected"))) return null
        val restoredConstructors = constructors.map { descriptor ->
            val owner = loader.loadClassOrNull(descriptor.declaringClassName) ?: return null
            descriptor.restore(owner) ?: return null
        }
        fun restoreFields(fields: Map<String, FieldDescriptor>): Map<String, Field>? {
            return fields.mapValues { (_, descriptor) ->
                val owner = loader.loadClassOrNull(descriptor.declaringClassName) ?: return null
                descriptor.restore(owner) ?: return null
            }
        }
        return RestoredHomeFrameSymbols(
            restoredConstructors, restoreFields(lists) ?: return null, restoreFields(itemFields) ?: return null,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): HomeFrameSymbols {
            fun fields(key: String): Map<String, FieldDescriptor> {
                val values = obj.optJSONObject(key) ?: return emptyMap()
                return values.keys().asSequence().associateWith { FieldDescriptor.fromJson(values.getJSONObject(it)) }
            }
            val constructors = obj.optJSONArray("constructors") ?: JSONArray()
            return HomeFrameSymbols(
                (0 until constructors.length()).map { ConstructorDescriptor.fromJson(constructors.getJSONObject(it)) },
                fields("lists"), fields("itemFields"), obj.optString("evidence", "-"),
            )
        }
    }
}

data class RestoredHomeFrameSymbols(
    val constructors: List<Constructor<*>>,
    val lists: Map<String, Field>,
    val itemFields: Map<String, Field>,
)

internal fun locateHomeFrameSymbols(loader: ClassLoader): HomeFrameSymbols? {
    val viewModel = loader.loadClassOrNull("tv.danmaku.bili.khome.vm.HomeFrameViewModel") ?: return null
    val serializer = loader.loadClassOrNull("kotlinx.serialization.KSerializer") ?: return null
    val descriptor = loader.loadClassOrNull("kotlinx.serialization.descriptors.SerialDescriptor") ?: return null
    val getDescriptor = serializer.getMethod("getDescriptor")
    val getSerialName = descriptor.getMethod("getSerialName")
    val getCount = descriptor.getMethod("getElementsCount")
    val getElementName = descriptor.getMethod("getElementName", Int::class.javaPrimitiveType)
    fun shape(owner: Class<*>): Pair<String, List<String>>? = runCatching {
        val serializers = owner.declaredFields.filter { Modifier.isStatic(it.modifiers) }.mapNotNull { field ->
            val factory = field.type.declaredMethods.singleOrNull {
                !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && serializer.isAssignableFrom(it.returnType)
            } ?: return@mapNotNull null
            field.isAccessible = true
            factory.isAccessible = true
            field.get(null)?.let { factory.invoke(it) }
        }
        val value = getDescriptor.invoke(serializers.singleOrNull() ?: return@runCatching null)
        val count = getCount.invoke(value) as Int
        if (count !in 1..64) return@runCatching null
        (getSerialName.invoke(value) as String) to (0 until count).map { getElementName.invoke(value, it) as String }
    }.getOrNull()

    val first = linkedSetOf<Class<*>>()
    viewModel.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach {
        collectHomeFrameTypes(it.genericType, first, 0)
    }
    val candidates = LinkedHashSet(first)
    first.filter(::isHomeFrameHostClass).forEach { owner ->
        owner.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach { candidates += it.type }
    }
    val data = candidates.filter(::isHomeFrameHostClass).mapNotNull { owner ->
        shape(owner)?.takeIf { it.first == "tv.danmaku.bili.khomeapi.frame.HomeTabData" }?.let { owner to it.second }
    }.singleOrNull() ?: return null
    val dataFields = homeFrameFields(data.first, data.second) ?: return null
    val lists = listOf("tab", "bottom").associateWith { name ->
        dataFields[name]?.takeIf { List::class.java.isAssignableFrom(it.type) } ?: return null
    }.toMutableMap()
    val item = lists.values.map { homeFrameListItem(it) }.distinct().singleOrNull() ?: return null
    dataFields["top"]?.takeIf {
        List::class.java.isAssignableFrom(it.type) && homeFrameListItem(it) == item
    }?.let { lists["top"] = it }
    val itemShape = shape(item)?.takeIf { it.first == "tv.danmaku.bili.khomeapi.frame.HomeTabItemData" } ?: return null
    val allItemFields = homeFrameFields(item, itemShape.second) ?: return null
    val itemFields = listOf("id", "name", "uri", "tab_id").mapNotNull { name ->
        allItemFields[name]?.let { field ->
            if (field.type != String::class.java) return null
            name to field
        }
    }.toMap().toMutableMap()
    if (listOf("id", "name", "uri").any { it !in itemFields }) return null
    itemFields["default_selected"] = allItemFields["default_selected"]
        ?.takeIf { it.type == Int::class.javaPrimitiveType } ?: return null
    val constructors = data.first.declaredConstructors.toList().takeIf { it.isNotEmpty() } ?: return null
    return HomeFrameSymbols(
        constructors.map(ConstructorDescriptor::of), lists.mapValues { FieldDescriptor.of(it.value) },
        itemFields.mapValues { FieldDescriptor.of(it.value) },
        "data=${data.first.name},item=${item.name},lists=${lists.keys.joinToString()},constructors=${constructors.size}",
    )
}

private fun homeFrameFields(owner: Class<*>, elements: List<String>): Map<String, Field>? {
    val fields = owner.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        .sortedWith(compareBy<Field>({ it.name }, { it.type.name }))
    if (fields.size != elements.size || elements.distinct().size != elements.size) return null
    val types = fields.map { it.type }
    if (owner.declaredConstructors.none { constructor ->
            val parameters = constructor.parameterTypes.toList()
            parameters == types || (parameters.size >= types.size + 1 &&
                parameters[0] == Int::class.javaPrimitiveType && parameters.subList(1, types.size + 1) == types)
        }) return null
    return elements.zip(fields).toMap()
}

private fun homeFrameListItem(field: Field): Class<*>? =
    ((field.genericType as? ParameterizedType)?.actualTypeArguments?.singleOrNull())?.let {
        when (it) {
            is Class<*> -> it
            is WildcardType -> it.upperBounds.singleOrNull() as? Class<*>
            else -> null
        }
    }

private fun collectHomeFrameTypes(type: Type, sink: MutableSet<Class<*>>, depth: Int) {
    if (depth > 4) return
    when (type) {
        is Class<*> -> sink += type
        is ParameterizedType -> {
            collectHomeFrameTypes(type.rawType, sink, depth + 1)
            type.actualTypeArguments.forEach { collectHomeFrameTypes(it, sink, depth + 1) }
        }
        is WildcardType -> type.upperBounds.forEach { collectHomeFrameTypes(it, sink, depth + 1) }
        is GenericArrayType -> collectHomeFrameTypes(type.genericComponentType, sink, depth + 1)
    }
}

private fun isHomeFrameHostClass(type: Class<*>): Boolean =
    !type.isPrimitive && !type.isArray && !type.isInterface &&
        listOf("java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.").none { type.name.startsWith(it) }
