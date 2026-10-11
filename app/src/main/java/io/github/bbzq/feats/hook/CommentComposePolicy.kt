package io.github.bbzq.feats.hook

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

internal object CommentComposePolicy {
    fun followConstructor(model: Class<*>): Constructor<*>? {
        val decoration = model.declaredFields.map { it.type }.distinct().singleOrNull { type ->
            if (!type.name.startsWith(model.name + "$")) return@singleOrNull false
            val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            fields.map { it.name }.toSet() == setOf("a", "b", "c", "d") &&
                fields.all { !it.type.isPrimitive && it.type.name.startsWith(model.name + "$") }
        } ?: return null
        val follow = decoration.getDeclaredField("b").type
        val constructor = decoration.declaredConstructors.singleOrNull {
            it.parameterCount == 4 && it.parameterTypes[1] == follow && it.parameterTypes.none(Class<*>::isPrimitive)
        }?.apply { isAccessible = true } ?: return null
        return runCatching {
            val empty = constructor.newInstance(*arrayOfNulls<Any>(4))
            decoration.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.all {
                it.isAccessible = true
                it.get(empty) == null
            }
        }.getOrDefault(false).takeIf { it }?.let { constructor }
    }

    fun isFollow(value: Any?): Boolean = value?.toString()?.startsWith("Follow(mid=") == true

    fun quickReplyTypes(action: Class<*>): List<Class<*>> = action.declaredClasses.filter { type ->
        val fields = type.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        fields.size in 8..9 && fields.count { it.type == Long::class.javaPrimitiveType } == 5 &&
            fields.count { it.type == String::class.java } == 2
    }

    fun isQuickReply(value: Any, types: List<Class<*>>): Boolean =
        types.any { it.isInstance(value) } && value.toString().startsWith("ClickCard(")
}
