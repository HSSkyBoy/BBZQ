package io.github.bbzq.feats.hook

import io.github.bbzq.ModuleSettings
import io.github.bbzq.ModuleSettingsBridge
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.replace

class LiveRoomVerticalSwipeHook(env: RoamingEnv) : BaseRoamingHook(env) {
    override fun startHook() {
        if (!ModuleSettings.isDisableLiveRoomVerticalSwipeEnabled(prefs)) {
            log("startHook: LiveRoomVerticalSwipe disabled, settings=${ModuleSettingsBridge.lastStatus}")
            return
        }

        val pagerClass = runCatching {
            classLoader.loadClass("com.bilibili.bililive.room.ui.roomv3.vertical.widget.LiveVerticalPagerView")
        }.getOrNull()
        val recyclerClass = runCatching {
            classLoader.loadClass("androidx.recyclerview.widget.RecyclerView")
        }.getOrNull()
        if (pagerClass == null || recyclerClass == null) {
            log("startHook: LiveRoomVerticalSwipe skipped, pager=$pagerClass recycler=$recyclerClass")
            return
        }

        // 翻页容器内部是 RecyclerView 子类，其 onInterceptTouchEvent/onTouchEvent 由“用户输入开关”控制；
        // 直接让它们恒为 false，与宿主自身禁止滑动时的状态一致，程序化翻页（smoothScrollToPosition）不受影响。
        val innerClass = pagerClass.declaredFields
            .map { it.type }
            .firstOrNull { it != recyclerClass && recyclerClass.isAssignableFrom(it) }
        if (innerClass == null) {
            log("startHook: LiveRoomVerticalSwipe skipped, inner pager RecyclerView not found")
            return
        }

        var hookCount = 0
        innerClass.declaredMethods
            .filter {
                (it.name == "onInterceptTouchEvent" || it.name == "onTouchEvent") &&
                    it.parameterCount == 1 &&
                    it.returnType == java.lang.Boolean.TYPE
            }
            .forEach { method ->
                env.replace(method) { false }
                hookCount++
            }
        log("startHook: LiveRoomVerticalSwipe hooked $hookCount methods on ${innerClass.name}")
    }
}
