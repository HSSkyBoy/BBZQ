package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.WeakHashMap

internal fun RoamingEnv.hookComposeMineVip(): Boolean {
    val owner = classLoader.findClassOrNull("tv.danmaku.bili.ui.main2.mineV2.ui.MineVipModuleComposeKt") ?: return false
    val method = owner.declaredMethods.singleOrNull { method ->
        val parameters = method.parameterTypes.map { it.name }
        Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
            (parameters.size == 4 && parameters.take(3) == listOf("int", COMPOSER, COMPOSE_MODIFIER) &&
                parameters[3].startsWith(MINE_UI_PACKAGE) ||
                parameters.size in 4..5 && parameters[0] == COMPOSE_MODIFIER &&
                parameters[1].startsWith(MINE_UI_PACKAGE) && parameters[2] == COMPOSER &&
                parameters.drop(3).all { it == "int" })
    } ?: return false
    val layout = MineVipHiddenLayout.resolve(classLoader)
    val modifierIndex = method.parameterTypes.indexOfFirst { it.name == COMPOSE_MODIFIER }
    val installed = hookBefore(method) { param ->
        if (!ModuleSettings.isMineRemoveVipEnabled(prefs)) return@hookBefore
        if (!ModuleSettings.isMineKeepVipSpaceEnabled(prefs)) {
            param.result = null
            return@hookBefore
        }
        val hidden = layout ?: return@hookBefore
        param.args[modifierIndex] = hidden.wrap(param.args[modifierIndex])
        val changedIndex = if (modifierIndex == 2) 0 else 3
        val changed = param.args[changedIndex] as? Int ?: 0
        param.args[changedIndex] = (changed and 0b1110.inv()) or 0b0100
        if (method.parameterCount == 5) param.args[4] = (param.args[4] as? Int ?: 0) and 1.inv()
    }
    if (installed) log("MineProfile Compose VIP hook installed, keepSpaceAvailable=${layout != null}")
    return installed
}

private class MineVipHiddenLayout(
    private val modifier: Class<*>,
    private val empty: Any,
    private val factory: Method,
    private val block: Any,
) {
    private val decorated = WeakHashMap<Any, Boolean>()
    private val cache = WeakHashMap<Any, WeakReference<Any>>()

    @Synchronized
    fun wrap(original: Any?): Any {
        val source = original?.takeIf(modifier::isInstance) ?: empty
        if (decorated.containsKey(source)) return source
        return cache[source]?.get() ?: requireNotNull(nativeCall { factory.invoke(null, source, block) }).also {
            decorated[it] = true
            cache[source] = WeakReference(it)
        }
    }

    companion object {
        fun resolve(loader: ClassLoader): MineVipHiddenLayout? = runCatching {
            fun type(name: String): Class<*> = requireNotNull(loader.findClassOrNull(name))
            val modifier = type(COMPOSE_MODIFIER)
            val function1 = type("kotlin.jvm.functions.Function1")
            val function3 = type("kotlin.jvm.functions.Function3")
            val scope = type("androidx.compose.ui.layout.MeasureScope")
            val measurable = type("androidx.compose.ui.layout.Measurable")
            val placeable = type("androidx.compose.ui.layout.Placeable")
            val measure = measurable.methods.single {
                it.name.startsWith("measure-") && it.returnType == placeable &&
                    it.parameterTypes.contentEquals(arrayOf(Long::class.javaPrimitiveType))
            }.apply { isAccessible = true }
            val layout = scope.getMethod("layout", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                Map::class.java, function1)
            check(layout.returnType == type("androidx.compose.ui.layout.MeasureResult"))
            val width = placeable.getMethod("getWidth")
            val height = placeable.getMethod("getHeight")
            val unit = requireNotNull(type("kotlin.Unit").getField("INSTANCE").get(null))
            val placement = function(function1, "MineVipUnplaced") { unit }
            val block = function(function3, "MineVipMeasure") { args ->
                val receiver = requireNotNull(args?.getOrNull(0)).also { check(scope.isInstance(it)) }
                val child = requireNotNull(args?.getOrNull(1)).also { check(measurable.isInstance(it)) }
                val constraints = args?.getOrNull(2) as Long
                nativeCall {
                    val measured = requireNotNull(measure.invoke(child, constraints))
                    requireNotNull(layout.invoke(receiver, width.invoke(measured), height.invoke(measured),
                        emptyMap<Any, Any>(), placement))
                }
            }
            val factory = type("androidx.compose.ui.layout.LayoutModifierKt").getMethod("layout", modifier, function3)
            check(factory.returnType == modifier)
            MineVipHiddenLayout(modifier, requireNotNull(modifier.getField("Companion").get(null)), factory, block)
        }.getOrNull()

        private fun function(type: Class<*>, label: String, invoke: (Array<out Any?>?) -> Any?): Any =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "invoke" -> invoke(args)
                    "toString" -> label
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    else -> null
                }
            }
    }
}

private inline fun <T> nativeCall(block: () -> T): T = try {
    block()
} catch (failure: InvocationTargetException) {
    throw failure.targetException
}

private const val COMPOSER = "androidx.compose.runtime.Composer"
private const val COMPOSE_MODIFIER = "androidx.compose.ui.Modifier"
private const val MINE_UI_PACKAGE = "tv.danmaku.bili.ui.main2.mineV2.ui."
