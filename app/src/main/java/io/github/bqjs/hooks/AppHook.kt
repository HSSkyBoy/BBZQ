package io.github.bqjs.hooks

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

interface AppHook {
    val targetPackageName: String

    fun install(packageReady: PackageReadyParam, log: (String, Throwable?) -> Unit)
}
