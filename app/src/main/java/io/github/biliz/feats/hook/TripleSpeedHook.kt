package io.github.biliz.feats.hook

import io.github.biliz.ModuleSettings
import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.findClassOrNull
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.hookAfter
import java.lang.reflect.Modifier

/**
 * Unlocks the player 3x playback speed that Bilibili gates behind a server A/B experiment.
 *
 * The host reads the int preference "sp_play_speed_experiment" and maps it onto the
 * [LONG_PRESS_SPEED_EXPERIMENT] enum inside a synthetic Function0. Only experiment groups whose
 * enum value is 4 (SmartLongPressAnd3x) or 5 (Speed2And3x) make the speed menu offer 3.0x. We force
 * the mapped group to value 5 (Speed2And3x): long-press stays 2x while the menu gains the 3.0x entry.
 * The player also disables 3x for streams at 50fps or above; we lift that guard and its quality
 * switch reset when this feature is enabled.
 */
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
            // invoke() is shared across the synthetic Function0's cases; only the speed config case exposes a field typed as the experiment enum, so other cases fall through untouched.
            val field = result.javaClass.declaredFields
                .firstOrNull { it.type == experimentClass } ?: return@hookAfter
            field.isAccessible = true
            if (field.get(result) !== target) field.set(result, target)
        }

        symbols?.qualitySpeedResetMethod?.let { resetMethod ->
            env.hookBefore(resetMethod) { param ->
                if (!ModuleSettings.isPlayerTripleSpeedEnabled(prefs)) return@hookBefore
                // StoryQualityService.p(int) only resets an active 3x speed after a quality
                // switch when the selected stream is high-frame-rate. Skip that reset so the
                // 3x speed remains active after switching quality.
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

        // Speed2And3x: long-press remains 2x, the speed selection menu gains the 3.0x option.
        private const val TARGET_GROUP_VALUE = 5
        private const val HIGH_FRAME_RATE_THRESHOLD = 50f
    }
}
