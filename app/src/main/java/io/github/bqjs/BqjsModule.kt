package io.github.bqjs

import android.util.Log
import io.github.bqjs.hooks.HookRegistry
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class BqjsModule : XposedModule() {
    private var isNpatchFramework = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val frameworkName = getFrameworkName()
        isNpatchFramework = frameworkName.equals(NPATCH_FRAMEWORK_NAME, ignoreCase = true)
        val status = if (isNpatchFramework) "enabled" else "disabled"
        log(Log.INFO, LOG_TAG, "Loaded in ${param.getProcessName()} on $frameworkName; bqjs is $status")
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!isNpatchFramework) return

        HookRegistry.handlePackageReady(param) { message, throwable ->
            if (throwable == null) {
                log(Log.INFO, LOG_TAG, message)
            } else {
                log(Log.WARN, LOG_TAG, message, throwable)
            }
        }
    }

    private companion object {
        private const val LOG_TAG = "bqjs"
        private const val NPATCH_FRAMEWORK_NAME = "NPatch"
    }
}
