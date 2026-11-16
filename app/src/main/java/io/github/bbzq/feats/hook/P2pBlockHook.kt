package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookBefore
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Keeps the client from acting as a P2P/PCDN node.
 *
 * Video: `P2P.getInstance(Context, String, Provider)` answers null, which is how the host itself
 * reports "P2P disabled by online config"; every caller handles it.
 * Live: `P2PType.create/createTo` map a type code to a P2P vendor or `UNUSED`. Only the Dolby code is
 * kept (it shares the enum, but is not P2P), everything else becomes `UNUSED`.
 */
class P2pBlockHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (env.processName != env.packageName || isInstalled) return
        if (!ModuleSettings.isBlockP2pEnabled(prefs)) return

        var hooked = 0
        classLoader.findClassOrNull(P2P)?.declaredMethods
            ?.firstOrNull { it.name == "getInstance" && it.parameterCount == 3 && Modifier.isStatic(it.modifiers) }
            ?.let { method ->
                if (env.hookBefore(method) { param -> if (enabled()) param.result = null }) hooked++
            }

        classLoader.findClassOrNull(P2P_TYPE)?.declaredMethods?.forEach { method ->
            when {
                method.isIntFactory("create") -> hooked += hookTypeCode(method) { it and DOLBY_BIT }
                method.isIntFactory("createTo") -> hooked += hookTypeCode(method) { if (it == DOLBY_CODE) it else 0 }
            }
        }

        isInstalled = hooked > 0
        log("P2pBlock: hooked $hooked method(s)")
    }

    private fun hookTypeCode(method: Method, keep: (Int) -> Int): Int {
        val ok = env.hookBefore(method) { param ->
            if (!enabled()) return@hookBefore
            (param.args[0] as? Int)?.let { param.args[0] = keep(it) }
        }
        return if (ok) 1 else 0
    }

    private fun enabled() = ModuleSettings.isBlockP2pEnabled(prefs)

    private fun Method.isIntFactory(name: String) =
        this.name == name && Modifier.isStatic(modifiers) && parameterTypes.singleOrNull() == Int::class.javaPrimitiveType

    private companion object {
        const val P2P = "tv.danmaku.ijk.media.player.P2P"
        const val P2P_TYPE = "com.bilibili.bililive.playercore.p2p.P2PType"
        const val DOLBY_BIT = 0x1000
        const val DOLBY_CODE = 13
    }
}
