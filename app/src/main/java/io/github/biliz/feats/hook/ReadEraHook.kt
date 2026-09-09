package io.github.biliz.feats.hook

import io.github.biliz.feats.BaseRoamingHook
import io.github.biliz.feats.RoamingEnv
import io.github.biliz.feats.findClassOrNull
import io.github.biliz.feats.hookBefore
import io.github.biliz.feats.methodOrNull

class ReadEraHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (env.processName != env.packageName) {
            log("ReadEraHook: skip non-main process (${env.processName})")
            return
        }
        log("ReadEraHook: starting for ${env.packageName}...")

        val count = unlockBackgroundPlayback()

        log("ReadEraHook: installed $count hook(s)")
    }

    private fun unlockBackgroundPlayback(): Int {
        val cls = classLoader.findClassOrNull(SPEECH_SERVICE_CLASS)
            ?: return logSkip("$SPEECH_SERVICE_CLASS missing")

        var count = 0

        // 路径 1：退到后台即时暂停 SpeechService.C()（无参私有方法）
        val sleepPause = cls.methodOrNull(SLEEP_PAUSE_METHOD)
        if (sleepPause != null) {
            env.hookBefore(sleepPause) { param ->
                param.result = null
                logOnce("sleep", "Blocked background(SLEEP) TTS pause")
            }
            count++
        } else {
            log("skip: $SPEECH_SERVICE_CLASS.$SLEEP_PAUSE_METHOD() missing")
        }

        // 路径 2：后台看门狗 SpeechService.a(SpeechService)（静态，单个 SpeechService 参数）
        val watchdogPause = cls.methodOrNull(WATCHDOG_PAUSE_METHOD, cls)
        if (watchdogPause != null) {
            env.hookBefore(watchdogPause) { param ->
                param.result = null
                logOnce("watchdog", "Blocked background watchdog pause + premium upsell")
            }
            count++
        } else {
            log("skip: $SPEECH_SERVICE_CLASS.$WATCHDOG_PAUSE_METHOD(SpeechService) missing")
        }

        log("BackgroundPlayback: $count hook(s)")
        return count
    }

    private val logged = hashSetOf<String>()
    private fun logOnce(key: String, msg: String) {
        if (logged.add(key)) log(msg)
    }

    private fun logSkip(reason: String): Int {
        log("skip: $reason")
        return 0
    }

    private companion object {
        // org.readera.SpeechService 后台朗读前台服务
        private const val SPEECH_SERVICE_CLASS = "org.readera.SpeechService"
        // void C()：SLEEP 事件
        private const val SLEEP_PAUSE_METHOD = "C"
        // static void a(SpeechService)
        private const val WATCHDOG_PAUSE_METHOD = "a"
    }
}
