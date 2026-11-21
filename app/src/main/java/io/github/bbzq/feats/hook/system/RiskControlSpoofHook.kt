package io.github.bbzq.feats.hook.system

import android.content.Context
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import org.json.JSONArray

/**
 * 伪造 `com.bilibili.gripper.riskcontrol.RiskCollect` 风控指纹采集结果，使设备上报告为正常状态：
 * 1. 将 `RiskCollect` 静态收集器 Map 中的 36 项检测函数（Root / Xposed / Frida / 代理 / VPN / 模拟器等）
 *    包裹为返回 `false` 的代理，使原生的 `RiskCollect$a.c` 自动计算出合法的 BUVID 高 4 位校验码；
 * 2. Hook 风险应用安装列表检测方法 `(Context, String) -> String`，跳过包名扫描并按每 60 项一组
 *    调用原生 `(Int, Int) -> Long` 校验函数生成合法的“0 命中”无符号十进制串。
 */
class RiskControlSpoofHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        val riskCollectClass = classLoader.findClassOrNull(RISK_COLLECT_CLASS)
        if (riskCollectClass == null) {
            log("RiskControlSpoofHook: $RISK_COLLECT_CLASS not found, skipped")
            return
        }

        val wrappedCollectors = wrapCollectorMap(riskCollectClass)
        val hookedAppList = hookInstalledAppCheck(riskCollectClass)
        isInstalled = wrappedCollectors > 0 || hookedAppList
        log(
            "RiskControlSpoofHook: wrapped $wrappedCollectors collector(s), " +
                "appListHooked=$hookedAppList",
        )
    }

    private fun wrapCollectorMap(riskCollectClass: Class<*>): Int {
        val function2Class = classLoader.findClassOrNull(KOTLIN_FUNCTION2_CLASS) ?: return 0
        val mapField = riskCollectClass.declaredFields.firstOrNull { field ->
            Modifier.isStatic(field.modifiers) && Map::class.java.isAssignableFrom(field.type)
        } ?: return 0

        return runCatching {
            mapField.isAccessible = true
            val rawMap = mapField.get(null) as? Map<*, *> ?: return 0
            if (rawMap.isEmpty()) return 0

            val wrappedEntries = LinkedHashMap<Any?, Any?>(rawMap.size)
            var count = 0
            for ((key, value) in rawMap.entries) {
                if (value != null && function2Class.isInstance(value)) {
                    wrappedEntries[key] = createSpoofedFunction2Proxy(function2Class, value)
                    count++
                } else {
                    wrappedEntries[key] = value
                }
            }
            if (count == 0) return 0

            val updatedInPlace = runCatching {
                @Suppress("UNCHECKED_CAST")
                val mutable = rawMap as MutableMap<Any?, Any?>
                mutable.putAll(wrappedEntries)
                true
            }.getOrDefault(false)

            if (!updatedInPlace) {
                mapField.set(null, wrappedEntries)
            }
            count
        }.onFailure {
            log("RiskControlSpoofHook: failed to wrap RiskCollect map", it)
        }.getOrDefault(0)
    }

    private fun createSpoofedFunction2Proxy(function2Class: Class<*>, original: Any): Any {
        return Proxy.newProxyInstance(
            function2Class.classLoader,
            arrayOf(function2Class),
        ) { proxy, method, args ->
            when (method.name) {
                "invoke" -> {
                    if (ModuleSettings.isRiskControlSpoofEnabled(prefs)) {
                        java.lang.Boolean.FALSE
                    } else {
                        val safeArgs = args ?: emptyArray()
                        method.invoke(original, *safeArgs)
                    }
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "BbzqRiskCollectCleanProxy"
                else -> method.invoke(original, *(args ?: emptyArray()))
            }
        }
    }

    private fun hookInstalledAppCheck(riskCollectClass: Class<*>): Boolean {
        val candidateClasses = buildList {
            add(riskCollectClass)
            runCatching { addAll(riskCollectClass.declaredClasses) }
            classLoader.findClassOrNull("$RISK_COLLECT_CLASS\$a")?.let { if (it !in this) add(it) }
            classLoader.findClassOrNull("$RISK_COLLECT_CLASS\$Companion")?.let { if (it !in this) add(it) }
        }

        for (holderClass in candidateClasses) {
            val methods = runCatching { holderClass.declaredMethods.toList() }.getOrDefault(emptyList())
            val appCheckMethod = methods.firstOrNull { method ->
                method.returnType == String::class.java &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == Context::class.java &&
                    method.parameterTypes[1] == String::class.java
            } ?: continue

            val checksumMethod = methods.firstOrNull { method ->
                method.returnType == Long::class.javaPrimitiveType &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType
            } ?: continue

            checksumMethod.isAccessible = true
            env.hookBefore(appCheckMethod) { param ->
                if (!ModuleSettings.isRiskControlSpoofEnabled(prefs)) return@hookBefore
                val rawJson = param.args.getOrNull(1) as? String
                val receiver = if (Modifier.isStatic(checksumMethod.modifiers)) null else param.thisObject
                val spoofed = buildCleanAppListPayload(rawJson, checksumMethod, receiver)
                if (spoofed != null) {
                    param.result = spoofed
                }
            }
            return true
        }
        return false
    }

    private fun buildCleanAppListPayload(
        rawJson: String?,
        checksumMethod: Method,
        receiver: Any?,
    ): String? {
        if (rawJson.isNullOrEmpty()) return "0"
        val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return "0"
        val length = array.length()
        if (length == 0) return ""

        val chunkCount = (length + CHUNK_SIZE - 1) / CHUNK_SIZE
        val builder = StringBuilder(chunkCount * 20)
        for (chunkIndex in 0 until chunkCount) {
            val checksum = runCatching {
                checksumMethod.invoke(receiver, 0, chunkIndex) as? Long
            }.getOrNull() ?: return null
            if (builder.isNotEmpty()) builder.append(',')
            builder.append(java.lang.Long.toUnsignedString(checksum))
        }
        return builder.toString()
    }

    private companion object {
        private const val RISK_COLLECT_CLASS = "com.bilibili.gripper.riskcontrol.RiskCollect"
        private const val KOTLIN_FUNCTION2_CLASS = "kotlin.jvm.functions.Function2"
        private const val CHUNK_SIZE = 60
    }
}
