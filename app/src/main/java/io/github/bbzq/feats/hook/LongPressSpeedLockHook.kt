package io.github.bbzq.feats.hook

import android.content.res.Configuration
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Toast
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

class LongPressSpeedLockHook(env: RoamingEnv) : BaseRoamingHook(env) {
    private val states = Collections.synchronizedMap(WeakHashMap<Any, LockState>())
    private val installedListenerClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val theseus2States = Collections.synchronizedMap(WeakHashMap<Any, Theseus2LockState>())

    @Volatile
    private var activeTheseus2Service: WeakReference<Any>? = null

    override fun startHook() {
        if (env.processName != env.packageName) return

        val theseusInstalled = installLegacyTheseusLock()
        val theseus2Installed = installTheseus2Lock()
        if (theseusInstalled || theseus2Installed) {
            isInstalled = true
            log(
                "startHook: LongPressSpeedLock installed " +
                    "(theseus=$theseusInstalled, theseus2=$theseus2Installed, dynamic switch enabled)",
            )
        } else {
            log("startHook: LongPressSpeedLock skipped because neither theseus nor theseus2 targets are available")
        }
    }

    private fun installLegacyTheseusLock(): Boolean {
        val gestureServiceClass = classLoader.findClassOrNull(GESTURE_SERVICE_CLASS) ?: return false
        val longPressListenerClass = classLoader.findClassOrNull(LONG_PRESS_LISTENER_CLASS) ?: return false
        val registerMethod = gestureServiceClass.declaredMethods.firstOrNull {
            it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(longPressListenerClass, Int::class.javaPrimitiveType))
        } ?: return false
        val scrollRegistrar = findScrollRegistrar(gestureServiceClass) ?: return false
        return env.hookAfter(registerMethod) { param ->
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
    }

    private fun installTheseus2Lock(): Boolean {
        val gestureServiceClass = classLoader.findClassOrNull(THESEUS2_GESTURE_SERVICE_CLASS) ?: return false
        val touchActionClass = classLoader.findClassOrNull(THESEUS2_TOUCH_ACTION_CLASS) ?: return false
        val tripleServiceClass = classLoader.findClassOrNull(THESEUS2_TRIPLE_SPEED_SERVICE_CLASS) ?: return false

        val touchMethod = gestureServiceClass.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        touchActionClass,
                        Long::class.javaPrimitiveType,
                        Long::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Long::class.javaPrimitiveType,
                    ),
                )
        } ?: return false

        val stopMethod = tripleServiceClass.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterCount == 0 &&
                method.returnType == Void.TYPE
        }?.apply { isAccessible = true } ?: return false

        val callbackClasses = buildList {
            classLoader.findClassOrNull("$THESEUS2_TRIPLE_SPEED_SERVICE_CLASS\$a")?.let(::add)
            tripleServiceClass.declaredClasses.forEach { if (it !in this) add(it) }
        }
        val startMethod = callbackClasses.asSequence()
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == Float::class.javaObjectType
            }?.apply { isAccessible = true } ?: return false

        val callbackClass = startMethod.declaringClass
        val callbackOwnerField = callbackClass.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == tripleServiceClass
        }?.apply { isAccessible = true } ?: return false

        val gestureBridgeField = gestureServiceClass.declaredFields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) &&
                runCatching { field.type.declaredFields.any { it.type == callbackClass } }.getOrDefault(false)
        }?.apply { isAccessible = true }
        val bridgeCallbackField = gestureBridgeField?.type?.declaredFields?.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == callbackClass
        }?.apply { isAccessible = true }

        fun resolveTripleServiceFromGesture(gestureService: Any): Any? {
            if (gestureBridgeField != null && bridgeCallbackField != null) {
                val bridge = runCatching { gestureBridgeField.get(gestureService) }.getOrNull()
                val callback = bridge?.let { runCatching { bridgeCallbackField.get(it) }.getOrNull() }
                val owner = callback?.let { runCatching { callbackOwnerField.get(it) }.getOrNull() }
                if (owner != null) return owner
            }
            return activeTheseus2Service?.get()
        }

        val jobField: Field? = tripleServiceClass.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type.name == "kotlinx.coroutines.Job"
        }?.apply { isAccessible = true }

        fun syncJobLiveness(service: Any, state: Theseus2LockState) {
            if (!state.locked && !state.active) return
            if (jobField != null && runCatching { jobField.get(service) == null }.getOrDefault(false)) {
                state.locked = false
                state.active = false
                state.suppressReleaseStopUntilMs = 0L
            }
        }

        env.hookBefore(startMethod) { param ->
            if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookBefore
            val callback = param.thisObject ?: return@hookBefore
            val service = runCatching { callbackOwnerField.get(callback) }.getOrNull() ?: return@hookBefore
            val state = theseus2States.getOrPut(service) { Theseus2LockState() }
            syncJobLiveness(service, state)
            state.sessionCrossed = false
            if (state.locked) {
                state.locked = false
                state.active = false
                state.suppressReleaseStopUntilMs = 0L
                Toast.makeText(env.hostContext, "已解除倍速锁定", Toast.LENGTH_SHORT).show()
                runCatching { stopMethod.invoke(service) }
                param.result = null
            }
        }

        env.hookAfter(startMethod) { param ->
            if (param.result == null) return@hookAfter
            val callback = param.thisObject ?: return@hookAfter
            val service = runCatching { callbackOwnerField.get(callback) }.getOrNull() ?: return@hookAfter
            val state = theseus2States.getOrPut(service) { Theseus2LockState() }
            state.active = true
            activeTheseus2Service = WeakReference(service)
        }

        env.hookBefore(stopMethod) { param ->
            if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookBefore
            val service = param.thisObject ?: return@hookBefore
            val state = theseus2States[service] ?: return@hookBefore
            if (state.locked && SystemClock.uptimeMillis() <= state.suppressReleaseStopUntilMs) {
                state.suppressReleaseStopUntilMs = 0L
                param.result = null
                return@hookBefore
            }
            state.locked = false
            state.active = false
            state.suppressReleaseStopUntilMs = 0L
        }

        return env.hookBefore(touchMethod) { param ->
            if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookBefore
            val gestureService = param.thisObject ?: return@hookBefore
            val actionName = (param.args.getOrNull(0) as? Enum<*>)?.name ?: return@hookBefore
            val packedOffset = param.args.getOrNull(1) as? Long ?: return@hookBefore
            val packedViewport = param.args.getOrNull(4) as? Long ?: 0L
            val x = Float.fromBits((packedOffset ushr 32).toInt())
            val y = Float.fromBits(packedOffset.toInt())

            val service = resolveTripleServiceFromGesture(gestureService) ?: return@hookBefore
            val state = theseus2States.getOrPut(service) { Theseus2LockState() }
            syncJobLiveness(service, state)

            when (actionName) {
                "Down" -> {
                    state.downX = x
                    state.downY = y
                    state.sessionCrossed = false
                }
                "Move" -> {
                    if (!state.active && !state.locked) return@hookBefore
                    if (state.sessionCrossed || !isLandscape()) return@hookBefore
                    val vertical = abs(y - state.downY) >= abs(x - state.downX)
                    if (!vertical) return@hookBefore
                    val viewportHeight = packedViewport.toInt()
                    val boundary = if (viewportHeight > 0) {
                        viewportHeight * LANDSCAPE_LOCK_START
                    } else {
                        lockBoundary()
                    }
                    if (y < boundary) return@hookBefore
                    state.sessionCrossed = true
                    state.locked = !state.locked
                    if (state.locked) {
                        Toast.makeText(env.hostContext, "松手锁定倍速", Toast.LENGTH_SHORT).show()
                    } else {
                        state.active = false
                        state.suppressReleaseStopUntilMs = 0L
                        Toast.makeText(env.hostContext, "已解除倍速锁定", Toast.LENGTH_SHORT).show()
                        runCatching { stopMethod.invoke(service) }
                    }
                }
                "Up", "Cancel" -> {
                    if (state.locked) {
                        state.suppressReleaseStopUntilMs = SystemClock.uptimeMillis() + RELEASE_STOP_SUPPRESS_WINDOW_MS
                    } else {
                        state.active = false
                    }
                }
            }
        }
    }

    private fun installListenerHooks(listenerClass: Class<*>) {
        if (!installedListenerClasses.add(listenerClass)) return
        listenerClass.declaredMethods.firstOrNull {
            it.name == "onLongPress" && it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.let { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookBefore
                val state = param.thisObject?.let(states::get) ?: return@hookBefore
                // A fresh physical long-press begins: reset the per-session crossing flag so the
                // boundary-drag can toggle again, regardless of any stale/recycled MotionEvent refs.
                state.sessionCrossed = false
                if (state.locked) param.result = true
            }
        }
        listenerClass.declaredMethods.firstOrNull {
            it.name != "onLongPress" &&
                it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
        }?.let { method ->
            env.hookBefore(method) { param ->
                if (!ModuleSettings.isPlayerLongPressSpeedLockEnabled(prefs)) return@hookBefore
                val state = param.thisObject?.let(states::get) ?: return@hookBefore
                state.sessionCrossed = false
                if (state.locked) param.result = null
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
                    val vertical = abs(move.y - down.y) >= abs(move.x - down.x)
                    if (!isLandscape() || !vertical) return@InvocationHandler false
                    if (move.y < lockBoundary()) return@InvocationHandler false
                    // One toggle per long-press session: the flag is cleared by the real
                    // onLongPress/onLongPressEnd hooks above, not by comparing MotionEvent
                    // references (those get recycled by the framework and can't be trusted).
                    if (state.sessionCrossed) return@InvocationHandler false
                    state.sessionCrossed = true
                    state.locked = !state.locked
                    if (state.locked) {
                        Toast.makeText(env.hostContext, "松手锁定倍速", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(env.hostContext, "已解除倍速锁定", Toast.LENGTH_SHORT).show()
                        listener.javaClass.findLongPressEndMethod()?.invoke(listener, move)
                    }
                    true
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

    private class LockState(
        var locked: Boolean = false,
        var installed: Boolean = false,
        var sessionCrossed: Boolean = false,
    )

    private class Theseus2LockState(
        var locked: Boolean = false,
        var active: Boolean = false,
        var sessionCrossed: Boolean = false,
        var downX: Float = 0f,
        var downY: Float = 0f,
        var suppressReleaseStopUntilMs: Long = 0L,
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
        private const val THESEUS2_GESTURE_SERVICE_CLASS =
            "com.bilibili.ship.theseus2.united.player.gesture.TheseusPlayerGestureService"
        private const val THESEUS2_TOUCH_ACTION_CLASS =
            "com.bilibili.ship.theseus2.united.player.gesture.TheseusPlayerTouchAction"
        private const val THESEUS2_TRIPLE_SPEED_SERVICE_CLASS =
            "com.bilibili.ship.theseus2.united.player.TripleSpeedService"
        private const val LOCK_SCROLL_PRIORITY = 3
        private const val LANDSCAPE_LOCK_START = 0.66f
        private const val RELEASE_STOP_SUPPRESS_WINDOW_MS = 500L
    }
}
