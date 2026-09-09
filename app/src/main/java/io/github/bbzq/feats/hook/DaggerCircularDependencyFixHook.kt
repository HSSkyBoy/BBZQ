package io.github.bbzq.feats.hook

import android.os.Bundle
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.allFields
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.getObjectField
import io.github.bbzq.feats.getStaticObjectField
import io.github.bbzq.feats.intercept
import io.github.bbzq.feats.setObjectField
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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

        val directB = thisObj.getObjectField("b") ?: thisObj.getObjectField("instance")
        val sentinelC = clazz.getStaticObjectField("c") ?: clazz.getStaticObjectField("UNINITIALIZED")
        if (directB != null && directB !== sentinelC) {
            runCatching { thisObj.setObjectField("b", directB) }
            runCatching { thisObj.setObjectField("a", null) }
            runCatching { thisObj.setObjectField("provider", null) }
            return directB
        }

        val sentinels = clazz.allFields()
            .filter { Modifier.isStatic(it.modifiers) }
            .mapNotNull { runCatching { it.get(null) }.getOrNull() }
            .toSet()

        for (field in clazz.allFields()) {
            if (Modifier.isStatic(field.modifiers)) continue
            val value = runCatching { field.get(thisObj) }.getOrNull() ?: continue
            if (!sentinels.contains(value)) {
                val typeName = field.type.name
                if (!typeName.contains("Provider") && !typeName.contains("DoubleCheck")) {
                    return value
                }
            }
        }

        return null
    }
}
