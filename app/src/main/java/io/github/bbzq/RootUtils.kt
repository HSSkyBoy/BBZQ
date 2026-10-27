package io.github.bbzq

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.bbzq.utils.ReflectionUtils
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku
import rikka.sui.Sui

object RootUtils {
    private const val SHIZUKU_REQUEST_CODE = 0xBB29

    private val executor = Executors.newSingleThreadExecutor()
    private val outputExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    val CANDIDATE_PACKAGES = listOf(
        "tv.danmaku.bili",
    )

    fun isRootAvailable(): Boolean {
        val paths = System.getenv("PATH")?.split(":") ?: listOf(
            "/system/bin",
            "/system/xbin",
            "/sbin",
            "/system/sd/xbin",
            "/system/bin/failsafe",
            "/data/local/xbin",
            "/data/local/bin",
            "/data/local",
            "/system/xbin/su",
            "/system/bin/su",
        )
        return paths.any { dir -> File(dir, "su").exists() }
    }

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun resolveBilibiliPackage(context: Context, prefs: SharedPreferences?): String {
        val runtimePkg = prefs?.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, null)
        if (!runtimePkg.isNullOrBlank() && isPackageInstalled(context.packageManager, runtimePkg)) {
            return runtimePkg
        }
        for (pkg in CANDIDATE_PACKAGES) {
            if (isPackageInstalled(context.packageManager, pkg)) {
                return pkg
            }
        }
        return runtimePkg?.takeIf { it.isNotBlank() } ?: "tv.danmaku.bili"
    }

    /**
     * 调用 Shizuku/Sui API 前确保当前进程 Sui 桥接已建立。
     * 强制停止逻辑运行在 BBZQ 自身进程（目标是 tv.danmaku.bili），
     * ShizukuProvider 通常会自动 init；此处用 [Sui.isSui] 守卫做一次安全兜底。
     */
    private fun ensureSuiInitialized(context: Context): Boolean {
        if (Sui.isSui()) return true
        return runCatching { Sui.init(context.packageName) }.getOrDefault(false)
    }

    /**
     * Shizuku / Sui 是否可用（binder 已连接且当前进程已被授权）。
     * Sui 环境下 [Shizuku.checkSelfPermission] 恒为 DENIED，但 binder 已建立，直接视为就绪。
     */
    private fun isShizukuReady(): Boolean {
        if (Sui.isSui()) return true
        return runCatching {
            val ping = Shizuku.pingBinder()
            if (!ping) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private val shizukuNewProcessMethod by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    // Shizuku 13.x 将 newProcess 标记为 private（@RestrictTo(LIBRARY)），但运行时仍可用，
    // 通过反射调用以获得高权限进程。
    private fun restartViaShizuku(command: String): Result<Unit> {
        return runCatching {
            check(isShizukuReady()) { "Shizuku/Sui is unavailable or permission was denied" }
            val method = checkNotNull(shizukuNewProcessMethod) { "Shizuku process API is unavailable" }
            val args = arrayOf("sh", "-c", command)
            val process = ReflectionUtils.safeInvoke(method, null, args, null, null) as? Process
            checkNotNull(process) { "Could not create Shizuku/Sui process" }
            awaitRestart(process)
        }
    }

    fun executeSuCommand(vararg commands: String): Result<Int> {
        return runCatching {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { writer ->
                for (cmd in commands) {
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) exitCode else throw IllegalStateException("su exited with code $exitCode")
        }
    }

    private fun restartViaRoot(command: String): Result<Unit> = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        awaitRestart(process)
    }

    private fun awaitRestart(process: Process) {
        // Drain both pipes while waiting: am -W may produce output before it exits.
        val stdout = outputExecutor.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
        val stderr = outputExecutor.submit<String> { process.errorStream.bufferedReader().use { it.readText() } }
        try {
            check(process.waitFor(60, TimeUnit.SECONDS)) { "Restart command timed out" }
            val output = stdout.get(5, TimeUnit.SECONDS) + "\n" + stderr.get(5, TimeUnit.SECONDS)
            // Some Android versions return zero even when am could not launch the activity.
            check(process.exitValue() == 0 && output.lineSequence().any { it.trim() == "Status: ok" }) {
                output.trim().takeLast(2000).ifBlank { "Restart command exited with code ${process.exitValue()}" }
            }
        } finally {
            process.destroy()
            stdout.cancel(true)
            stderr.cancel(true)
        }
    }

    // ---- 对外入口 ----

    fun restartBilibili(
        context: Context,
        prefs: SharedPreferences?,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
    ) {
        val targetPackage = resolveBilibiliPackage(context, prefs)
        // Resolve the real launcher before stopping anything; do not hard-code a Bilibili activity.
        val launchIntent = runCatching {
            context.packageManager.getLaunchIntentForPackage(targetPackage)
        }.getOrNull()
        if (launchIntent?.component == null) {
            mainHandler.post { callback(false, context.getString(R.string.restart_failed_pkg_not_found), null) }
            return
        }
        val onRestartFinished: (Boolean, String?, String?) -> Unit = { success, errorMessage, method ->
            if (success) {
                launchBilibiliAfterRestart(context, launchIntent, method, callback)
            } else {
                callback(false, errorMessage, method)
            }
        }
        // -S stops the package and waits briefly before starting; -W reports the launch result.
        // Keep stop AND start privileged: ordinary background startActivity may be blocked.
        // am needs the intent: scheme; a bare #Intent string is parsed as a component name.
        val intentUri = launchIntent.toUri(Intent.URI_INTENT_SCHEME).replace("'", "'\\''")
        val command = "am start -S -W --user current '$intentUri'"
        executor.execute {
            val rootResult = restartViaRoot(command)
            if (rootResult.isSuccess) {
                mainHandler.post { onRestartFinished(true, null, "Root") }
                return@execute
            }
            // Only touch Shizuku/Sui (including its permission dialog) after Root fails.
            mainHandler.post {
                restartWithShizuku(context, command, rootResult.exceptionOrNull(), onRestartFinished)
            }
        }
    }

    private fun launchBilibiliAfterRestart(
        context: Context,
        launchIntent: Intent,
        method: String?,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
    ) {
        // Called on the main thread, before the success callback can finish SettingsActivity.
        val result = runCatching {
            val foregroundIntent = Intent(launchIntent).apply {
                // Bring the launcher to the front even if SettingsActivity shares the host task.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val launchContext = if (context is Activity && !context.isFinishing && !context.isDestroyed) {
                context
            } else {
                context.applicationContext
            }
            launchContext.startActivity(foregroundIntent)
        }
        if (result.isSuccess) {
            callback(true, null, method)
        } else {
            callback(false, "Could not open Bilibili: ${result.exceptionOrNull()?.message}", null)
        }
    }

    private fun restartWithShizuku(
        context: Context,
        command: String,
        rootError: Throwable?,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
    ) {
        ensureSuiInitialized(context)
        val shizukuPing = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuPerm = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        val shizukuNeedsAuth = !Sui.isSui() && shizukuPing && shizukuPerm != PackageManager.PERMISSION_GRANTED

        if (shizukuNeedsAuth && context is Activity && !context.isFinishing && !context.isDestroyed) {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != SHIZUKU_REQUEST_CODE) return
                    runCatching { Shizuku.removeRequestPermissionResultListener(this) }
                    // Run the entire restart off the main thread, including waiting for launch.
                    executor.execute {
                        val result = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            restartViaShizuku(command)
                        } else {
                            Result.failure(IllegalStateException("Shizuku permission was denied"))
                        }
                        finishRestart(result, rootError, callback)
                    }
                }
            }

            runCatching {
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }.onFailure {
                runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                finishRestart(Result.failure(it), rootError, callback)
            }
            return
        }

        executor.execute {
            finishRestart(restartViaShizuku(command), rootError, callback)
        }
    }

    private fun finishRestart(
        result: Result<Unit>,
        rootError: Throwable?,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
    ) {
        mainHandler.post {
            if (result.isSuccess) {
                callback(true, null, "Shizuku/Sui")
            } else {
                callback(false, "Root: ${rootError?.message}\nShizuku/Sui: ${result.exceptionOrNull()?.message}", null)
            }
        }
    }

    fun showRestartBilibiliDialog(
        activity: Activity,
        prefs: SharedPreferences,
        onRestartSuccess: (() -> Unit)? = null,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.restart_dialog_title)
            .setMessage(R.string.restart_dialog_message)
            .setPositiveButton(R.string.restart_dialog_confirm) { _, _ ->
                Toast.makeText(activity.applicationContext, R.string.restart_in_progress, Toast.LENGTH_SHORT).show()
                restartBilibili(activity, prefs) { success, errorMessage, method ->
                    if (success) {
                        // 成功后会自动拉起哔哩哔哩，设置页被切到后台；
                        // 必须用 applicationContext，否则依附于后台 Activity 的 toast 不显示。
                        val text = if (!method.isNullOrBlank()) {
                            String.format(activity.getString(R.string.restart_success), method)
                        } else {
                            activity.getString(R.string.restart_success_fallback)
                        }
                        Toast.makeText(activity.applicationContext, text, Toast.LENGTH_LONG).show()
                        onRestartSuccess?.invoke()
                    } else {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            AlertDialog.Builder(activity)
                                .setTitle(R.string.restart_dialog_title)
                                .setMessage(errorMessage?.let {
                                    activity.getString(R.string.restart_failed_details, it)
                                } ?: activity.getString(R.string.restart_failed_root_required))
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
