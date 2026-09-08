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
import rikka.shizuku.Shizuku
import rikka.sui.Sui

object RootUtils {
    private const val SHIZUKU_REQUEST_CODE = 0xBB29

    private val executor = Executors.newSingleThreadExecutor()
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

    /**
     * 通过 Shizuku / Sui 强制停止目标应用。
     * 利用 Shizuku/Sui 提供的高权限进程执行 `am force-stop`：Sui 下以 root 运行、
     * Shizuku 下以 adb shell 运行，均等价于 root 但无需设备 root。
     */
    private fun forceStopViaShizuku(targetPackage: String): Boolean {
        if (!isShizukuReady()) return false
        return runCommandViaShizuku(arrayOf("am", "force-stop", targetPackage))
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
    private fun runCommandViaShizuku(command: Array<String>): Boolean {
        return runCatching {
            val method = shizukuNewProcessMethod ?: return false
            val process = ReflectionUtils.safeInvoke(method, null, command, null, null) as? Process
            val exitCode = process?.waitFor()
            process?.destroy()
            exitCode == 0
        }.getOrDefault(false)
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

    private fun forceStopViaRoot(targetPackage: String): Boolean {
        return executeSuCommand("am force-stop $targetPackage").isSuccess
    }

    // ---- 对外入口 ----

    fun restartBilibili(
        context: Context,
        prefs: SharedPreferences?,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
    ) {
        val targetPackage = resolveBilibiliPackage(context, prefs)
        ensureSuiInitialized(context)

        val shizukuPing = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val shizukuPerm = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        val shizukuNeedsAuth = !Sui.isSui() && shizukuPing && shizukuPerm != PackageManager.PERMISSION_GRANTED

        if (shizukuNeedsAuth && context is Activity) {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != SHIZUKU_REQUEST_CODE) return
                    runCatching { Shizuku.removeRequestPermissionResultListener(this) }
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    // 在后台线程执行，避免主线程在 waitFor 或 Root 弹窗授权时发生 ANR
                    executor.execute {
                        val viaShizuku = granted && forceStopViaShizuku(targetPackage)
                        val effective = if (viaShizuku) true else forceStopViaRoot(targetPackage)
                        val method = if (effective) (if (viaShizuku) "Shizuku/Sui" else "Root") else null
                        finishRestart(context, effective, callback, targetPackage, method)
                    }
                }
            }

            runCatching {
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }.onFailure {
                runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                executor.execute {
                    val ok = forceStopViaRoot(targetPackage)
                    finishRestart(context, ok, callback, targetPackage, if (ok) "Root" else null)
                }
            }
            return
        }

        executor.execute {
            // 1) 优先尝试 Shizuku / Sui（二者共用同一 API）
            var isSuccess = forceStopViaShizuku(targetPackage)
            var method: String? = if (isSuccess) "Shizuku/Sui" else null
            // 2) Shizuku / Sui 失败，再兜底 Root (su)
            if (!isSuccess) {
                isSuccess = forceStopViaRoot(targetPackage)
                method = if (isSuccess) "Root" else null
            }
            finishRestart(context, isSuccess, callback, targetPackage, method)
        }
    }

    private fun finishRestart(
        context: Context,
        success: Boolean,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
        targetPackage: String,
        method: String? = null,
    ) {
        mainHandler.post {
            if (success) {
                launchAndCallback(context, targetPackage, callback, method)
            } else {
                callback(false, "shizuku/sui 与 root 均未能强制停止 $targetPackage", null)
            }
        }
    }

    private fun launchAndCallback(
        context: Context,
        targetPackage: String,
        callback: (success: Boolean, errorMessage: String?, method: String?) -> Unit,
        method: String?,
    ) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // 授权期间 SettingsActivity 可能已被销毁，必须用 applicationContext 启动
            runCatching { context.applicationContext.startActivity(launchIntent) }
        }
        callback(true, null, method)
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
                restartBilibili(activity, prefs) { success, _, method ->
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
                                .setMessage(R.string.restart_failed_root_required)
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
