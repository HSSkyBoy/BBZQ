package io.github.bbzq.feats.hook.player

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfter
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class TripleSpeedHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return

        var installedCount = 0
        val symbols = env.symbols?.tripleSpeed?.restore(classLoader)
        val readerMethods = symbols?.experimentReaderMethods ?: listOfNotNull(
            classLoader.findClassOrNull("F4.b")?.declaredMethods?.firstOrNull {
                it.name == "invoke" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
            },
        )
        if (readerMethods.isNotEmpty()) {
            // playerbizcommonv2 与 playerbizcommon3 各有一份同名枚举,按结果字段的实际类型取目标分组
            val targets = ConcurrentHashMap<Class<*>, Any>()
            readerMethods.forEach { readerMethod ->
                if (env.hookAfter(readerMethod) { param ->
                        if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookAfter
                        val result = param.result ?: return@hookAfter
                        val field = result.javaClass.declaredFields
                            .firstOrNull { it.type.isEnum && it.type.simpleName == LONG_PRESS_SPEED_EXPERIMENT } ?: return@hookAfter
                        val target = targets[field.type] ?: field.type.enumConstants
                            ?.firstOrNull { readEnumValue(field.type, it) == TARGET_GROUP_VALUE }
                            ?.also { targets[field.type] = it }
                            ?: return@hookAfter
                        field.isAccessible = true
                        if (field.get(result) !== target) field.set(result, target)
                    }
                ) {
                    installedCount++
                }
            }
            log("startHook: TripleSpeed, experiment readers=${readerMethods.joinToString { it.declaringClass.name }}")
        } else {
            log("startHook: TripleSpeed experimentReaderMethod unavailable")
        }

        symbols?.longPressSpeedMethod?.let { speedMethod ->
            if (env.hookBefore(speedMethod) { param ->
                    if (!ModuleSettings.isPlayerCustomLongPressSpeedEnabled(prefs)) return@hookBefore
                    param.result = ModuleSettings.getPlayerCustomLongPressSpeedValue(prefs)
                }
            ) {
                installedCount++
                log("startHook: TripleSpeed, custom long-press speed at ${speedMethod.declaringClass.name}.${speedMethod.name}")
            }
        }

        val theseus2CustomCount = installTheseus2CustomSpeed()
        installedCount += theseus2CustomCount
        if (symbols?.longPressSpeedMethod == null && theseus2CustomCount == 0) {
            log("startHook: TripleSpeed, long-press speed method unavailable; custom multiplier disabled")
        }

        symbols?.qualitySpeedResetMethod?.let { resetMethod ->
            if (env.hookBefore(resetMethod) { param ->
                    if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookBefore
                    param.result = null
                }
            ) {
                installedCount++
                log("startHook: TripleSpeed, disabled high-frame-rate speed reset")
            }
        }

        symbols?.highFrameRateSpeedGuardMethod?.let { guardMethod ->
            if (env.hookAfter(guardMethod) { param ->
                    if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookAfter
                    val fps = (param.args.firstOrNull() as? Number)?.toFloat() ?: return@hookAfter
                    if (fps >= HIGH_FRAME_RATE_THRESHOLD) param.result = true
                }
            ) {
                installedCount++
                log("startHook: TripleSpeed, enabled 3x for ${HIGH_FRAME_RATE_THRESHOLD.toInt()}fps+ streams")
            }
        }

        if (installedCount > 0) {
            isInstalled = true
            log("startHook: TripleSpeed hook installed (points=$installedCount, dynamic switch enabled)")
        } else {
            log("startHook: TripleSpeed skipped because no hook point is available")
        }
    }

    private fun installTheseus2CustomSpeed(): Int {
        var count = 0
        val beginLambdaClass = classLoader.findClassOrNull(THESEUS2_BEGIN_SPEED_LAMBDA_CLASS)
        beginLambdaClass?.declaredConstructors?.firstOrNull { ctor ->
            ctor.parameterTypes.any { it == Float::class.javaPrimitiveType }
        }?.let { ctor ->
            val speedIndex = ctor.parameterTypes.indexOfFirst { it == Float::class.javaPrimitiveType }
            if (speedIndex >= 0 && env.hookBefore(ctor) { param ->
                    if (!ModuleSettings.isPlayerCustomLongPressSpeedEnabled(prefs)) return@hookBefore
                    if (speedIndex < param.args.size) {
                        param.args[speedIndex] = ModuleSettings.getPlayerCustomLongPressSpeedValue(prefs)
                    }
                }
            ) {
                count++
            }
        }

        val serviceClass = classLoader.findClassOrNull(THESEUS2_TRIPLE_SPEED_SERVICE_CLASS)
        val callbackClasses = buildList {
            classLoader.findClassOrNull("$THESEUS2_TRIPLE_SPEED_SERVICE_CLASS\$a")?.let(::add)
            serviceClass?.declaredClasses?.forEach { if (it !in this) add(it) }
        }
        callbackClasses.asSequence()
            .flatMap { it.declaredMethods.asSequence() }
            .firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 0 &&
                    method.returnType == Float::class.javaObjectType
            }?.let { startMethod ->
                if (env.hookAfter(startMethod) { param ->
                        if (!ModuleSettings.isPlayerCustomLongPressSpeedEnabled(prefs)) return@hookAfter
                        if (param.result != null) {
                            param.result = ModuleSettings.getPlayerCustomLongPressSpeedValue(prefs)
                        }
                    }
                ) {
                    count++
                }
            }

        if (count > 0) {
            log("startHook: TripleSpeed, theseus2 custom long-press speed points=$count")
        }
        return count
    }

    private fun readEnumValue(experimentClass: Class<*>, constant: Any): Int? {
        val field = experimentClass.declaredFields.firstOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType
        } ?: return null
        field.isAccessible = true
        return runCatching { field.getInt(constant) }.getOrNull()
    }

    private companion object {
        private const val LONG_PRESS_SPEED_EXPERIMENT = "LongPressSpeedExperiment"
        private const val THESEUS2_TRIPLE_SPEED_SERVICE_CLASS =
            "com.bilibili.ship.theseus2.united.player.TripleSpeedService"
        private const val THESEUS2_BEGIN_SPEED_LAMBDA_CLASS =
            "com.bilibili.ship.theseus2.united.player.TripleSpeedService\$beginTemporarySpeed\$1"

        private const val TARGET_GROUP_VALUE = 5
        private const val HIGH_FRAME_RATE_THRESHOLD = 50f
    }
}
