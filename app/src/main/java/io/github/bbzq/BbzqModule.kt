package io.github.bbzq

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.bbzq.feats.RoamingRuntime
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicBoolean

class BbzqModule : XposedModule() {
    private var packageName: String = ""
    private var processName: String = ""
    private val attachHookInstalled = AtomicBoolean(false)
    private val runtimeStarted = AtomicBoolean(false)
    private var environmentAbnormal = false

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.getProcessName()
        environmentAbnormal = isFrameworkEnvironmentAbnormal()
        if (environmentAbnormal) {
            log(
                Log.WARN,
                LOG_TAG,
                "Environment abnormal, module disabled: " +
                    "frameworkVersionCode=$frameworkVersionCode, frameworkVersion=$frameworkVersion",
            )
            return
        }
        log(
            Log.INFO,
            LOG_TAG,
            "Loaded in $processName on $frameworkName($frameworkVersionCode), api=$apiVersion",
        )
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (environmentAbnormal) return
        val packageName = param.getPackageName()
        if (packageName !in TARGET_PACKAGES) return
        if (!RoamingRuntime.isProcessSupported(packageName, processName)) {
            log(
                Log.INFO,
                LOG_TAG,
                "Skip unsupported process $processName for $packageName",
            )
            return
        }
        this.packageName = packageName

        if (attachHookInstalled.compareAndSet(false, true).not()) {
            maybeStartRuntime(packageName, processName, param.getDefaultClassLoader())
            return
        }

        val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
        attach.isAccessible = true
        hook(attach)
            .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH)
            .intercept { chain ->
                chain.proceed()
                val application = chain.getThisObject() as? Application ?: return@intercept null
                startRuntimeOnce(
                    packageName = packageName,
                    processName = processName,
                    application = application,
                    classLoader = application.javaClass.classLoader ?: param.getDefaultClassLoader(),
                )
            }

        maybeStartRuntime(packageName, processName, param.getDefaultClassLoader())
    }

    private fun startRuntimeOnce(
        packageName: String,
        processName: String,
        application: Context,
        classLoader: ClassLoader,
    ) {
        if (runtimeStarted.compareAndSet(false, true).not()) return
        startRuntime(
            packageName = packageName,
            processName = processName,
            application = application,
            classLoader = classLoader,
        )
    }

    private fun startRuntime(
        packageName: String,
        processName: String,
        application: Context,
        classLoader: ClassLoader,
    ) {
        RoamingRuntime.start(
            xposed = this,
            packageName = packageName,
            processName = processName,
            application = application,
            classLoader = classLoader,
        ) { message, throwable ->
            if (throwable == null) {
                log(Log.INFO, LOG_TAG, message)
            } else {
                log(Log.WARN, LOG_TAG, message, throwable)
            }
        }
    }

    private fun maybeStartRuntime(
        packageName: String,
        processName: String,
        classLoader: ClassLoader,
    ) {
        val application = resolveCurrentApplication() ?: return
        val resolvedClassLoader = application.javaClass.classLoader ?: classLoader
        startRuntimeOnce(
            packageName = packageName,
            processName = processName,
            application = application,
            classLoader = resolvedClassLoader,
        )
    }

    private fun resolveCurrentApplication(): Application? {
        return runCatching {
            currentApplicationMethod.invoke(null) as? Application
        }.getOrNull()
    }

    private fun isFrameworkEnvironmentAbnormal(): Boolean =
        LinkerGuard.isFrameworkEnvironmentAbnormal(frameworkVersionCode.toString(), frameworkVersion)

    private companion object {
        private const val LOG_TAG = "BBZQ"

        private val TARGET_PACKAGES = setOf(
            "tv.danmaku.bili",
            "com.bilibili.app.blue",
            "top.nkbe.npatch",
            "com.wo.voice2",
            "org.readera",
        )

        private val currentApplicationMethod: java.lang.reflect.Method by lazy(LazyThreadSafetyMode.NONE) {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
        }
    }
}
