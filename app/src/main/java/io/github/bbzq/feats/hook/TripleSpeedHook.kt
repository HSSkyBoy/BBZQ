package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

class TripleSpeedHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return

        val symbols = env.symbols?.tripleSpeed?.restore(classLoader)
        val readerMethods = symbols?.experimentReaderMethods ?: listOfNotNull(
            classLoader.findClassOrNull("F4.b")?.declaredMethods?.firstOrNull {
                it.name == "invoke" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
            },
        )
        if (readerMethods.isEmpty()) {
            log("startHook: TripleSpeed skipped because experimentReaderMethod is unavailable")
            return
        }

        // playerbizcommonv2 与 playerbizcommon3 各有一份同名枚举,按结果字段的实际类型取目标分组
        val targets = ConcurrentHashMap<Class<*>, Any>()
        readerMethods.forEach { readerMethod ->
            env.hookAfter(readerMethod) { param ->
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
        }
        log("startHook: TripleSpeed, experiment readers=${readerMethods.joinToString { it.declaringClass.name }}")

        symbols?.longPressSpeedMethod?.let { speedMethod ->
            env.hookBefore(speedMethod) { param ->
                if (!ModuleSettings.isPlayerCustomLongPressSpeedEnabled(prefs)) return@hookBefore
                param.result = ModuleSettings.getPlayerCustomLongPressSpeedValue(prefs)
            }
            log("startHook: TripleSpeed, custom long-press speed at ${speedMethod.declaringClass.name}.${speedMethod.name}")
        } ?: log("startHook: TripleSpeed, long-press speed method unavailable; custom multiplier disabled")

        symbols?.qualitySpeedResetMethod?.let { resetMethod ->
            env.hookBefore(resetMethod) { param ->
                if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookBefore
                param.result = null
            }
            log("startHook: TripleSpeed, disabled high-frame-rate speed reset")
        }

        symbols?.highFrameRateSpeedGuardMethod?.let { guardMethod ->
            env.hookAfter(guardMethod) { param ->
                if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookAfter
                val fps = (param.args.firstOrNull() as? Number)?.toFloat() ?: return@hookAfter
                if (fps >= HIGH_FRAME_RATE_THRESHOLD) param.result = true
            }
            log("startHook: TripleSpeed, enabled 3x for ${HIGH_FRAME_RATE_THRESHOLD.toInt()}fps+ streams")
        }

        isInstalled = true
        log("startHook: TripleSpeed hook installed (dynamic switch enabled)")
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

        private const val TARGET_GROUP_VALUE = 5
        private const val HIGH_FRAME_RATE_THRESHOLD = 50f
    }
}
