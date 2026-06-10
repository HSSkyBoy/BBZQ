package io.github.bzzq.hooks

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

object HookRegistry {
    private val targetPackageNames = listOf(
        "tv.danmaku.bili",
        "com.bilibili.app.in",
        "tv.danmaku.bilibilihd",
        "com.bilibili.app.blue",
    )

    private val hooks: List<AppHook> = targetPackageNames.flatMap { packageName ->
        listOf(
            PackageLoadLogHook(packageName),
            GsonSplashAdHook(packageName),
            VideoFeatureUnlockHook(packageName),
            AutoLikeVideoDetailHook(packageName),
            BlockLiveReservationHook(packageName),
            BlockLiveRoomQoeHook(packageName),
            LiveQualityHook(packageName),
            StoryVideoAdHook(packageName),
            MiniGameRewardAdHook(packageName),
            BiliEntryHook(packageName),
            AccessKeyCaptureHook(packageName),
            FreeCopyHook(packageName),
            SelectableTextHook(packageName),
            SharePurifyHook(packageName),
        )
    }

    private val hooksByPackageName: Map<String, List<AppHook>> = hooks.groupBy { it.targetPackageName }

    fun handlePackageReady(
        xposed: XposedInterface,
        packageReady: PackageReadyParam,
        log: (String, Throwable?) -> Unit,
    ) {
        val matchingHooks = hooksByPackageName[packageReady.getPackageName()].orEmpty()
        if (matchingHooks.isEmpty()) return

        log("Installing ${matchingHooks.size} hook(s) for ${packageReady.getPackageName()}", null)
        matchingHooks.forEach { hook ->
            runCatching { hook.install(xposed, packageReady, log) }
                .onFailure { log("Hook failed for ${packageReady.getPackageName()}", it) }
        }
    }
}
