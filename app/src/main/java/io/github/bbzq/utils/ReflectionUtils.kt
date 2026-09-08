package io.github.bbzq.utils

import android.util.Log

object ReflectionUtils {
    private const val TAG = "ReflectionUtils"

    /**
     * Safely invoke a reflective [java.lang.reflect.Method] on a target object.
     * Returns null if invocation fails and logs the throwable.
     */
    fun safeInvoke(method: java.lang.reflect.Method, target: Any?, vararg args: Any?): Any? {
        return try {
            if (!method.isAccessible) method.isAccessible = true
            method.invoke(target, *args)
        } catch (e: Throwable) {
            Log.w(TAG, "Reflection invoke failed: ${method.name}", e)
            null
        }
    }

    /**
     * Safely get the value of a reflective [java.lang.reflect.Field].
     */
    fun safeGet(field: java.lang.reflect.Field, target: Any?): Any? {
        return try {
            if (!field.isAccessible) field.isAccessible = true
            field.get(target)
        } catch (e: Throwable) {
            Log.w(TAG, "Reflection get field failed: ${field.name}", e)
            null
        }
    }

    /**
     * Safely set the value of a reflective [java.lang.reflect.Field].
     */
    fun safeSet(field: java.lang.reflect.Field, target: Any?, value: Any?) {
        try {
            if (!field.isAccessible) field.isAccessible = true
            field.set(target, value)
        } catch (e: Throwable) {
            Log.w(TAG, "Reflection set field failed: ${field.name}", e)
        }
    }
}
