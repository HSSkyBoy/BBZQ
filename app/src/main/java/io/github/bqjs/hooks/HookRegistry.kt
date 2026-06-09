package io.github.bqjs.hooks

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

object HookRegistry {
    private val hooks: List<AppHook> = listOf(
        PackageLoadLogHook("tv.danmaku.bili"),
        PackageLoadLogHook("com.bilibili.app.in"),
        PackageLoadLogHook("tv.danmaku.bilibilihd"),
        PackageLoadLogHook("com.bilibili.app.blue"),
    )

    private val hooksByPackageName: Map<String, List<AppHook>> = hooks.groupBy { it.targetPackageName }

    fun handlePackageReady(packageReady: PackageReadyParam, log: (String, Throwable?) -> Unit) {
        val matchingHooks = hooksByPackageName[packageReady.getPackageName()].orEmpty()
        if (matchingHooks.isEmpty()) return

        log("Installing ${matchingHooks.size} hook(s) for ${packageReady.getPackageName()}", null)
        matchingHooks.forEach { hook ->
            runCatching { hook.install(packageReady, log) }
                .onFailure { log("Hook failed for ${packageReady.getPackageName()}", it) }
        }
    }
}
