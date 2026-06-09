package io.github.bqjs.hooks

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class PackageLoadLogHook(
    override val targetPackageName: String,
) : AppHook {
    override fun install(packageReady: PackageReadyParam, log: (String, Throwable?) -> Unit) {
        log("Observed ${packageReady.getPackageName()} after classloader became ready", null)
    }
}
