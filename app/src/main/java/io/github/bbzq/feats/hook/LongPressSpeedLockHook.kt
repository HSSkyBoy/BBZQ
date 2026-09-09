package io.github.bbzq.feats.hook

import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

class LongPressSpeedLockHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val states = Collections.synchronizedMap(WeakHashMap<Any, LockState>())
    private val installedListenerClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())

    override fun startHook() {
        if (env.processName != env.packageName) return

        val gestureServiceClass = classLoader.findClassOrNull(GESTURE_SERVICE_CLASS) ?: return logSkip("GestureService")
        val longPressListenerClass = classLoader.findClassOrNull(LONG_PRESS_LISTENER_CLASS) ?: return logSkip("OnLongPressListener")
        val registerMethod = gestureServiceClass.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(longPressListenerClass, Int::class.javaPrimitiveType))
        } ?: return logSkip("GestureService long-press registration")
        val scrollRegistrar = findScrollRegistrar(gestureServiceClass)
            ?: return logSkip("GestureService long-press scroll registration")
        env.hookAfter(registerMethod) { param ->
            if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookAfter
            val service = param.thisObject ?: return@hookAfter
            val listener = param.args.firstOrNull() ?: return@hookAfter
            if (!isSpeedListener(listener)) return@hookAfter
            installListenerHooks(listener.javaClass)
            val state = states.getOrPut(listener) { LockState() }
            if (state.installed) return@hookAfter
            val proxy = createScrollProxy(scrollRegistrar.listenerType, listener, state)
            runCatching { scrollRegistrar.register(service, proxy) }
                .onFailure { log("LongPressSpeedLock: failed to register scroll listener", it) }
                .onSuccess { state.installed = true }
        }
        isInstalled = true
        log("startHook: LongPressSpeedLock installed (dynamic switch enabled)")
    }

    private fun installListenerHooks(listenerClass: Class<*>) {
        if (!installedListenerClasses.add(listenerClass)) return
        listenerClass.declaredMethods.firstOrNull {
            it.name == "onLongPress" && it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.let { method ->
            env.hookBefore(method) { param ->
                if (ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs) && param.thisObject?.let(states::get)?.locked == true) param.result = true
            }
        }
        listenerClass.declaredMethods.firstOrNull {
            it.name != "onLongPress" &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.let { method ->
            env.hookBefore(method) { param ->
                if (ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs) && param.thisObject?.let(states::get)?.locked == true) param.result = null
            }
        }
    }

    private fun createScrollProxy(type: Class<*>, listener: Any, state: LockState): Any =
        Proxy.newProxyInstance(classLoader, arrayOf(type), InvocationHandler { proxy, method, args ->
            when (method.name) {
                "toString" -> "BBZQLongPressScrollListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "onScroll" -> {
                    val down = args?.getOrNull(0) as? MotionEvent ?: return@InvocationHandler false
                    val move = args.getOrNull(1) as? MotionEvent ?: return@InvocationHandler false
                    val vertical = kotlin.math.abs(move.y - down.y) >= kotlin.math.abs(move.x - down.x)
                    if (!isLandscape() || !vertical) return@InvocationHandler false
                    val boundary = lockBoundary()
                    if (state.handledDownPress === down) {
                        if (state.locked && move.y < boundary) state.locked = false
                        return@InvocationHandler false
                    }
                    if (move.y < boundary) return@InvocationHandler false
                    state.handledDownPress = down
                    if (!state.locked) {
                        state.locked = true
                        Toast.makeText(env.hostContext, "松手锁定倍速", Toast.LENGTH_SHORT).show()
                        true
                    } else {
                        state.locked = false
                        listener.javaClass.findLongPressEndMethod()?.invoke(listener, move)
                        true
                    }
                }
                else -> defaultValue(method)
            }
        })

    private fun findScrollRegistrar(gestureServiceClass: Class<*>): ScrollRegistrar? {
        gestureServiceClass.declaredMethods.firstOrNull { method ->
            method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.name.contains("LongPressScroll", ignoreCase = true)
        }?.let { method ->
            method.isAccessible = true
            return ScrollRegistrar(method.parameterTypes[0]) { service, listener ->
                method.invoke(service, listener, LOCK_SCROLL_PRIORITY)
            }
        }

        val listenerType = classLoader.findClassOrNull(LONG_PRESS_SCROLL_LISTENER_CLASS) ?: return null
        val processorField = gestureServiceClass.declaredFields.firstOrNull {
            it.genericType.typeName.contains(LONG_PRESS_SCROLL_LISTENER_CLASS)
        } ?: return null
        val addMethod = processorField.type.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Any::class.java))
        } ?: return null
        processorField.isAccessible = true
        addMethod.isAccessible = true
        return ScrollRegistrar(listenerType) { service, listener ->
            processorField.get(service)?.let { processor ->
                addMethod.invoke(processor, LOCK_SCROLL_PRIORITY, listener)
            }
        }
    }

    private fun isLandscape(): Boolean =
        env.hostContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun lockBoundary(): Float {
        val metrics = env.hostContext.resources.displayMetrics
        return metrics.heightPixels * LANDSCAPE_LOCK_START
    }

    private fun isSpeedListener(listener: Any): Boolean =
        listener.javaClass.name.startsWith(TRIPLE_SPEED_LISTENER_PREFIX)

    private fun Class<*>.findLongPressEndMethod(): Method? =
        declaredMethods.firstOrNull {
            it.name != "onLongPress" &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.apply { isAccessible = true }

    private fun defaultValue(method: Method): Any? = when (method.returnType) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0.0f
        Double::class.javaPrimitiveType -> 0.0
        Byte::class.javaPrimitiveType -> 0.toByte()
        Short::class.javaPrimitiveType -> 0.toShort()
        Char::class.javaPrimitiveType -> '\u0000'
        else -> null
    }

    private fun logSkip(missing: String) = log("startHook: LongPressSpeedLock skipped because $missing is unavailable")

    private class LockState(
        var locked: Boolean = false,
        var installed: Boolean = false,
        var handledDownPress: MotionEvent? = null,
    )

    private class ScrollRegistrar(
        val listenerType: Class<*>,
        val register: (service: Any, listener: Any) -> Unit,
    )

    private companion object {
        private const val GESTURE_SERVICE_CLASS = "com.bilibili.playerbizcommon.gesture.GestureService"
        private const val LONG_PRESS_LISTENER_CLASS = "com.bilibili.playerbizcommon.gesture.OnLongPressListener"
        private const val LONG_PRESS_SCROLL_LISTENER_CLASS = "com.bilibili.playerbizcommon.gesture.OnLongPressScrollListener"
        private const val TRIPLE_SPEED_LISTENER_PREFIX = "com.bilibili.ship.theseus.united.player.TripleSpeedService$"
        private const val LOCK_SCROLL_PRIORITY = 3
        private const val LANDSCAPE_LOCK_START = 0.66f
    }
}
