package io.github.biliz.feats

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

class MethodHookParam internal constructor(
    private val chain: XposedInterface.Chain,
    val executable: Executable,
    val thisObject: Any?,
    val args: MutableList<Any?>,
) {
    private var returnEarly = false
    private var resultValue: Any? = null

    var result: Any?
        get() = resultValue
        set(value) {
            resultValue = value
            returnEarly = true
        }

    internal fun setInitialResult(value: Any?) {
        resultValue = value
        returnEarly = false
    }

    internal fun shouldReturnEarly(): Boolean = returnEarly

    fun invokeOriginalMethod(): Any? = chain.proceed(args.toTypedArray())
}

typealias Hooker = (MethodHookParam) -> Unit
typealias Replacer = (MethodHookParam) -> Any?

fun safeCoerceResult(executable: Executable, value: Any?): Any? {
    if (executable !is Method) return value
    val returnType = executable.returnType
    if (returnType == java.lang.Void.TYPE || returnType == Void::class.java) {
        return null
    }
    if (value == null && returnType.isPrimitive) {
        return when (returnType) {
            java.lang.Boolean.TYPE -> java.lang.Boolean.FALSE
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0.0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
    return value
}

fun RoamingEnv.hookBefore(executable: Executable, hooker: Hooker) {
    runCatching {
        executable.isAccessible = true
        xposed.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                val param = MethodHookParam(
                    chain = chain,
                    executable = executable,
                    thisObject = chain.getThisObject(),
                    args = chain.getArgs().toMutableList(),
                )
                runCatching {
                    hooker(param)
                }.onFailure { throwable ->
                    log("Hook before failed at ${executable.declaringClass.name}.${executable.name}", throwable)
                }
                if (param.shouldReturnEarly()) {
                    safeCoerceResult(executable, param.result)
                } else {
                    chain.proceed(param.args.toTypedArray())
                }
            }
    }.onFailure { throwable ->
        log("Failed to register hookBefore at ${executable.declaringClass.name}.${executable.name}", throwable)
    }
}

fun RoamingEnv.hookAfter(executable: Executable, hooker: Hooker) {
    runCatching {
        executable.isAccessible = true
        xposed.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                val param = MethodHookParam(
                    chain = chain,
                    executable = executable,
                    thisObject = chain.getThisObject(),
                    args = chain.getArgs().toMutableList(),
                )
                val initial = chain.proceed(param.args.toTypedArray())
                param.setInitialResult(initial)
                runCatching {
                    hooker(param)
                }.onFailure { throwable ->
                    log("Hook after failed at ${executable.declaringClass.name}.${executable.name}", throwable)
                }
                safeCoerceResult(executable, param.result)
            }
    }.onFailure { throwable ->
        log("Failed to register hookAfter at ${executable.declaringClass.name}.${executable.name}", throwable)
    }
}

fun RoamingEnv.replace(executable: Executable, replacer: Replacer) {
    runCatching {
        executable.isAccessible = true
        xposed.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                val param = MethodHookParam(
                    chain = chain,
                    executable = executable,
                    thisObject = chain.getThisObject(),
                    args = chain.getArgs().toMutableList(),
                )
                val computed = runCatching {
                    replacer(param)
                }.getOrElse { throwable ->
                    log("Hook replace failed at ${executable.declaringClass.name}.${executable.name}, invoking original", throwable)
                    return@intercept chain.proceed(param.args.toTypedArray())
                }
                safeCoerceResult(executable, computed)
            }
    }.onFailure { throwable ->
        log("Failed to register replace at ${executable.declaringClass.name}.${executable.name}", throwable)
    }
}

fun RoamingEnv.intercept(executable: Executable, block: (XposedInterface.Chain) -> Any?) {
    runCatching {
        executable.isAccessible = true
        xposed.hook(executable)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                val res = runCatching {
                    block(chain)
                }.getOrElse { throwable ->
                    log("Hook intercept failed at ${executable.declaringClass.name}.${executable.name}", throwable)
                    chain.proceed(chain.getArgs().toTypedArray())
                }
                safeCoerceResult(executable, res)
            }
    }.onFailure { throwable ->
        log("Failed to register intercept at ${executable.declaringClass.name}.${executable.name}", throwable)
    }
}

fun RoamingEnv.hookBeforeMethod(
    type: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>,
    hooker: Hooker,
): Int {
    val method = type.findMethodOrNull(methodName, *parameterTypes) ?: return 0
    hookBefore(method, hooker)
    return 1
}

fun RoamingEnv.hookAfterMethod(
    type: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>,
    hooker: Hooker,
): Int {
    val method = type.findMethodOrNull(methodName, *parameterTypes) ?: return 0
    hookAfter(method, hooker)
    return 1
}

fun RoamingEnv.replaceMethod(
    type: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>,
    replacer: Replacer,
): Int {
    val method = type.findMethodOrNull(methodName, *parameterTypes) ?: return 0
    replace(method, replacer)
    return 1
}

fun RoamingEnv.hookBeforeAllMethods(type: Class<*>, methodName: String?, hooker: Hooker): Int {
    val methods = runCatching {
        type.allMethods()
            .filter { methodName == null || it.name == methodName }
            .distinctBy(Method::toGenericString)
            .toList()
    }.getOrElse { emptyList() }
    methods.forEach { hookBefore(it, hooker) }
    return methods.size
}

fun RoamingEnv.hookAfterAllMethods(type: Class<*>, methodName: String?, hooker: Hooker): Int {
    val methods = runCatching {
        type.allMethods()
            .filter { methodName == null || it.name == methodName }
            .distinctBy(Method::toGenericString)
            .toList()
    }.getOrElse { emptyList() }
    methods.forEach { hookAfter(it, hooker) }
    return methods.size
}

fun RoamingEnv.hookBeforeAllConstructors(type: Class<*>, hooker: Hooker): Int {
    val constructors = runCatching {
        type.declaredConstructors.onEach { it.isAccessible = true }
    }.getOrElse { emptyArray() }
    constructors.forEach { hookBefore(it, hooker) }
    return constructors.size
}

fun RoamingEnv.hookAfterAllConstructors(type: Class<*>, hooker: Hooker): Int {
    val constructors = runCatching {
        type.declaredConstructors.onEach { it.isAccessible = true }
    }.getOrElse { emptyArray() }
    constructors.forEach { hookAfter(it, hooker) }
    return constructors.size
}

fun RoamingEnv.hookBeforeConstructor(
    type: Class<*>,
    vararg parameterTypes: Class<*>,
    hooker: Hooker,
): Int {
    val constructor = runCatching {
        type.getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
    }.getOrNull() ?: return 0
    hookBefore(constructor, hooker)
    return 1
}

private fun Class<*>.findMethodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
    return runCatching {
        getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true }
    }.getOrNull() ?: methodsNamed(name).firstOrNull {
        it.name == name && it.parameterTypes.contentEquals(parameterTypes)
    }
}

