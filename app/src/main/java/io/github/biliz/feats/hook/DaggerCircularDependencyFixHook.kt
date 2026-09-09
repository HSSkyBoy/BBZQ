package io.github.biliz.feats.hook

import android.os.Bundle
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.allFields
import io.github.biliz.feats.findClassOrNull
import io.github.biliz.feats.getObjectField
import io.github.biliz.feats.getStaticObjectField
import io.github.biliz.feats.intercept
import io.github.biliz.feats.setObjectField
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Deep defensive hook that prevents Dagger 2 Scoped Provider circular dependency crashes:
 * "Scoped provider was invoked recursively returning different results: ... This is likely due to a circular dependency."
 *
 * When Dagger's DoubleCheck.get() detects reentrant calls on the same thread, an inner call
 * has ALREADY created a valid singleton instance and stored it into the instance field.
 * Dagger normally throws an IllegalStateException to alert developers of a circular graph.
 * This hook catches that exception, recovers the already-allocated valid singleton, and returns it,
 * allowing the Activity / Service lifecycle to proceed completely without crashing.
 */
class DaggerCircularDependencyFixHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) return

        val candidateClasses = listOf(
            "j11.b",
            "XR0.L",
            "dagger.internal.DoubleCheck",
        )

        val loaders = listOfNotNull(
            classLoader,
            env.hostContext.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()

        var installed = 0
        for (className in candidateClasses) {
            val clazz = loaders.firstNotNullOfOrNull { it.findClassOrNull(className) } ?: continue
            val getMethods = clazz.declaredMethods.filter {
                it.name == "get" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
            }
            for (getMethod in getMethods) {
                env.intercept(getMethod) { chain ->
                    try {
                        chain.proceed()
                    } catch (e: Throwable) {
                        val circularEx = e.findDaggerCircularException()
                        if (circularEx != null) {
                            val thisObj = chain.getThisObject()
                            if (thisObj != null) {
                                val recovered = resolveAllocatedInstance(thisObj)
                                if (recovered != null) {
                                    log("DaggerCircularFix: safely recovered from circular dependency in ${thisObj.javaClass.name}, reusing singleton: $recovered")
                                    return@intercept recovered
                                }
                            }
                        }
                        throw e
                    }
                }
                installed++
                log("startHook: DaggerCircularDependencyFix installed on $className.${getMethod.name}")
            }
        }

        installActivityCrashGuard(loaders)

        if (installed > 0) {
            isInstalled = true
        }
    }

    private fun installActivityCrashGuard(loaders: List<ClassLoader>) {
        val activityClass = loaders.firstNotNullOfOrNull {
            it.findClassOrNull("com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity")
        } ?: return

        val onCreate = activityClass.declaredMethods.firstOrNull {
            it.name == "onCreate" && it.parameterCount == 1 && it.parameterTypes[0] == Bundle::class.java
        } ?: return

        env.intercept(onCreate) { chain ->
            try {
                chain.proceed()
            } catch (t: Throwable) {
                val circularEx = t.findDaggerCircularException()
                if (circularEx != null) {
                    log("DaggerCircularFix: caught circular dependency exception in UnitedBizDetailsActivity.onCreate, preventing crash: ${circularEx.message}")
                    return@intercept null
                }
                throw t
            }
        }
        log("startHook: DaggerCircularDependencyFix activity guard installed on UnitedBizDetailsActivity.onCreate")
    }

    private fun Throwable.findDaggerCircularException(): IllegalStateException? {
        var cur: Throwable? = this
        while (cur != null) {
            if (cur is IllegalStateException && cur.message?.contains("Scoped provider was invoked recursively") == true) {
                return cur
            }
            val next = cur.cause ?: (cur as? InvocationTargetException)?.targetException
            if (next === cur) break
            cur = next
        }
        return null
    }

    private fun resolveAllocatedInstance(thisObj: Any): Any? {
        val clazz = thisObj.javaClass

        // 1. Direct standard field 'b' (instance in Bilibili's DoubleCheck) or 'instance'
        val directB = thisObj.getObjectField("b") ?: thisObj.getObjectField("instance")
        val sentinelC = clazz.getStaticObjectField("c") ?: clazz.getStaticObjectField("UNINITIALIZED")
        if (directB != null && directB !== sentinelC) {
            // Null out provider reference just like standard DoubleCheck does on success
            runCatching { thisObj.setObjectField("b", directB) }
            runCatching { thisObj.setObjectField("a", null) }
            runCatching { thisObj.setObjectField("provider", null) }
            return directB
        }

        // 2. Generic fallback: find any non-static field whose value is neither null nor equal to any static sentinel
        val sentinels = clazz.allFields()
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { runCatching { it.get(null) }.getOrNull() }
            .toSet()

        for (field in clazz.allFields()) {
            if (Modifier.isStatic(field.modifiers)) continue
            val value = runCatching { field.get(thisObj) }.getOrNull() ?: continue
            if (!sentinels.contains(value)) {
                // Ignore provider fields that might be non-null
                val typeName = field.type.name
                if (!typeName.contains("Provider") && !typeName.contains("DoubleCheck")) {
                    return value
                }
            }
        }

        return null
    }
}
