package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.replace

class LiveReservationHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isBlockLiveReservationEnabled(prefs)) {
            log("startHook: LiveReservation disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val managerClass = runCatching {
            classLoader.loadClass("com.bilibili.bililive.room.biz.reverse.manager.LiveReservePopWindowManager")
        }.getOrNull()
        if (managerClass == null) {
            log("startHook: LiveReservation skipped, manager class not found")
            return
        }

        val reserveTypes = setOf(
            "com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveInfo",
            "com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveCalendarInfo",
        )
        var hookCount = 0
        managerClass.declaredMethods
            .filter { method ->
                method.returnType == Void.TYPE && method.parameterTypes.any { it.name in reserveTypes }
            }
            .forEach { method ->
                env.replace(method) { null }
                hookCount++
            }
        log("startHook: LiveReservation hooked $hookCount methods")
    }
}
