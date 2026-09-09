package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import io.github.bbzq.feats.hookAfter
import java.lang.reflect.Modifier

class TripleSpeedHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return

        val symbols = env.symbols?.tripleSpeed?.restore(classLoader)
        val readerMethod = symbols?.experimentReaderMethod ?: run {
            classLoader.findClassOrNull("F4.b")?.declaredMethods?.firstOrNull {
                it.name == "invoke" && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers)
            }
        }
        if (readerMethod == null) {
            log("startHook: TripleSpeed skipped because experimentReaderMethod is unavailable")
            return
        }

        val experimentClass = classLoader.findClassOrNull(LONG_PRESS_SPEED_EXPERIMENT) ?: run {
            log("startHook: TripleSpeed skipped because $LONG_PRESS_SPEED_EXPERIMENT is unavailable")
            return
        }
        val target = experimentClass.enumConstants?.firstOrNull { readEnumValue(experimentClass, it) == TARGET_GROUP_VALUE }
            ?: run {
                log("startHook: TripleSpeed skipped because group value=$TARGET_GROUP_VALUE was not found")
                return
            }

        env.hookAfter(readerMethod) { param ->
            if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookAfter
            val result = param.result ?: return@hookAfter
            val field = result.javaClass.declaredFields
                .firstOrNull { it.type == experimentClass } ?: return@hookAfter
            field.isAccessible = true
            if (field.get(result) !== target) field.set(result, target)
        }

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
        private const val LONG_PRESS_SPEED_EXPERIMENT =
            "com.bilibili.playerbizcommonv2.utils.LongPressSpeedExperiment"

        private const val TARGET_GROUP_VALUE = 5
        private const val HIGH_FRAME_RATE_THRESHOLD = 50f
    }
}
