package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookBefore

class BlockUpdateHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName) return
        if (!ModuleSettings.isBlockUpdateEnabled(prefs)) return

        val symbols = env.symbols?.blockUpdate?.restore(classLoader) ?: run {
            log("startHook: BlockUpdate skipped because symbols are unavailable")
            return
        }

        env.hookBefore(symbols.checkMethod) { param ->
            val exception = createUpdateException()
            if (exception != null) {
                throw exception
            } else {
                param.result = null
            }
        }

        log("startHook: BlockUpdate, methods=1")
    }

    private fun createUpdateException(): Throwable? {
        val message = "哼，休想要我更新！<(￣︶￣)>"
        return runCatching {
            val type = classLoader.loadClass(UPDATE_EXCEPTION_CLASS)
            val ctor = type.getDeclaredConstructor(String::class.java).apply {
                isAccessible = true
            }
            ctor.newInstance(message) as Throwable
        }.getOrNull()
    }

    private companion object {
        private const val UPDATE_EXCEPTION_CLASS =
            "tv.danmaku.bili.update.internal.exception.LatestVersionException"
    }
}
