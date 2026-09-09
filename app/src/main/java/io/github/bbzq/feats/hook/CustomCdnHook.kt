package io.github.bbzq.feats.hook

import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfter

/**
 * Rewrites UPos URLs at the protobuf boundary, before they reach the player or downloader.
 */
class CustomCdnHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        val methods = env.symbols?.tryFreeQuality?.restore(classLoader)?.playViewMethods.orEmpty()
        var installed = 0
        methods.forEach { method ->
            runCatching {
                env.hookAfter(method) { param ->
                    CustomCdnProcessor.rewriteResponse(param.result, prefs, ::log)
                }
                installed++
            }.onFailure { log("CustomCdn: failed to hook ${method.declaringClass.name}.${method.name}", it) }
        }
        if (installed == 0) {
            log("CustomCdn: no compatible PlayView method found")
        } else {
            log("CustomCdn: hooked $installed response method(s)")
        }
    }
}
