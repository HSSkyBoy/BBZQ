package io.github.bbzq.feats.hook.player

import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.callMethod
import io.github.bbzq.feats.findClassOrNull
import io.github.bbzq.feats.hookAfterMethod

class PlayArcConfHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (!ModuleSettings.isPlayArcConfEnabled(prefs)) return

        val arcConfClass = classLoader.findClassOrNull(ARC_CONF_CLASS) ?: return
        val playArcConfClass = classLoader.findClassOrNull(PLAY_ARC_CONF_CLASS) ?: return

        val supportedArcConf = runCatching {
            arcConfClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance().apply {
                callMethod("setDisabled", false)
                callMethod("setIsSupport", true)
            }
        }.getOrNull() ?: return

        val arcConfs = runCatching {
            val defaultInstance = playArcConfClass.getMethod("getDefaultInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val map = defaultInstance?.callMethod("internalGetMutableArcConfs") as? MutableMap<Int, Any?>
            if (map != null) {
                ARC_CONF_KEYS.forEach { key -> map[key] = supportedArcConf }
            }
            defaultInstance
        }.getOrNull() ?: return

        var hookCount = 0

        classLoader.findClassOrNull(PLAYER_MOSS_CLASS)?.let { playerMoss ->
            val reqClass = classLoader.findClassOrNull(PLAY_VIEW_UNITE_REQ_CLASS)
            if (reqClass != null) {
                listOf("executePlayViewUnite", "playViewUnite").forEach { methodName ->
                    hookCount += env.hookAfterMethod(playerMoss, methodName, reqClass) { param ->
                        param.result?.callMethod("mergePlayArcConf", arcConfs)
                    }
                }
            }
        }

        classLoader.findClassOrNull(PLAY_URL_MOSS_CLASS)?.let { playUrlMoss ->
            val reqClass = classLoader.findClassOrNull(PLAY_VIEW_REQ_CLASS)
            if (reqClass != null) {
                listOf("executePlayView", "playView").forEach { methodName ->
                    hookCount += env.hookAfterMethod(playUrlMoss, methodName, reqClass) { param ->
                        val playArc = param.result?.callMethod("getPlayArc") ?: return@hookAfterMethod
                        listOf("getCastConf", "getBackgroundPlayConf", "getSmallWindowConf").forEach { getter ->
                            playArc.callMethod(getter)?.apply {
                                callMethod("setDisabled", false)
                                callMethod("setIsSupport", true)
                            }
                        }
                    }
                }
            }
        }

        log("PlayArcConfHook installed ($hookCount hooks)")
    }

    private companion object {
        private const val ARC_CONF_CLASS = "com.bapis.bilibili.playershared.ArcConf"
        private const val PLAY_ARC_CONF_CLASS = "com.bapis.bilibili.playershared.PlayArcConf"
        private const val PLAYER_MOSS_CLASS = "com.bapis.bilibili.app.playerunite.v1.PlayerMoss"
        private const val PLAY_VIEW_UNITE_REQ_CLASS = "com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq"
        private const val PLAY_URL_MOSS_CLASS = "com.bapis.bilibili.app.playurl.v1.PlayURLMoss"
        private const val PLAY_VIEW_REQ_CLASS = "com.bapis.bilibili.app.playurl.v1.PlayViewReq"

        // CASTCONF(2), BACKGROUNDPLAY(9), SMALLWINDOW(23), LISTEN(36)
        private val ARC_CONF_KEYS = intArrayOf(2, 9, 23, 36)
    }
}
