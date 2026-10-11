package io.github.bbzq.feats.hook

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object QualityRangePolicy {
    fun matchesUpdate(method: Method): Boolean {
        val types = method.parameterTypes
        return Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE && types.size == 11 &&
            types[0] == Boolean::class.javaPrimitiveType &&
            types[1].name == "kotlinx.coroutines.flow.MutableStateFlow" && types[2] == types[1] &&
            types[3].name == "kotlin.jvm.internal.Ref\$BooleanRef" &&
            types[4].name == "kotlin.jvm.internal.Ref\$ObjectRef" &&
            types[5].name.startsWith("com.bilibili.ship.theseus.united.player.mediaplay.") &&
            types[6].name.startsWith("com.bilibili.ship.theseus.united.page.playingarea.") &&
            types[7].name.startsWith("com.bilibili.ship.theseus.united.page.screenstate.") &&
            types[8] == Float::class.javaPrimitiveType &&
            types[9] == Boolean::class.javaPrimitiveType && types[10] == types[9]
    }

    fun overrideRanges(halfFlow: Any, autoFlow: Any, fullScreen: Boolean, halfQn: Int, fullQn: Int): Int {
        val target = if (fullScreen) fullQn else halfQn
        if (target <= 0) return 0
        val flows = if (fullScreen || halfFlow === autoFlow) listOf(autoFlow) else listOf(halfFlow, autoFlow)
        val updates = flows.map { flow ->
            val getter = flow.javaClass.getMethod("getValue").apply { isAccessible = true }
            val setter = flow.javaClass.getMethod("setValue", Any::class.java).apply { isAccessible = true }
            val original = getter.invoke(flow) ?: return 0
            val range = fixedRange(original, target) ?: return 0
            Triple(flow, setter, original to range)
        }
        return try {
            updates.count { (flow, setter, values) ->
                if (values.first == values.second) false else {
                    setter.invoke(flow, values.second)
                    true
                }
            }
        } catch (failure: Throwable) {
            updates.forEach { (flow, setter, values) -> runCatching { setter.invoke(flow, values.first) } }
            throw failure
        }
    }

    private fun fixedRange(original: Any, qn: Int): Any? {
        val start = original.javaClass.getMethod("getStart").apply { isAccessible = true }.invoke(original) ?: return null
        val end = original.javaClass.getMethod("getEndInclusive").apply { isAccessible = true }.invoke(original) ?: return null
        if (start.javaClass != end.javaClass) return null
        val quality = start.javaClass.getDeclaredConstructor(Int::class.javaPrimitiveType).apply { isAccessible = true }
            .newInstance(qn)
        val constructor = original.javaClass.declaredConstructors.singleOrNull { ctor ->
            ctor.parameterCount == 2 && ctor.parameterTypes.all { it.isInstance(quality) }
        }?.apply { isAccessible = true } ?: return null
        return constructor.newInstance(quality, quality)
    }
}
