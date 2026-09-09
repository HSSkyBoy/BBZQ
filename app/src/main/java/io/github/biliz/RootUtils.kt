package io.github.biliz

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

object RootUtils {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    val CANDIDATE_PACKAGES = listOf(
        "tv.danmaku.bili",
        "com.bilibili.app.in",
        "tv.danmaku.bilibilihd",
        "com.bili.app",
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
        val currentPkg = context.packageName
        // Do not match the module's own package name
        if (currentPkg != "io.github.biliz" && (currentPkg == "tv.danmaku.bili" || currentPkg.startsWith("tv.danmaku.") || currentPkg.startsWith("com.bilibili."))) {
            return currentPkg
        }
        val runtimePkg = prefs?.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, null)
        if (!runtimePkg.isNullOrBlank() && runtimePkg != "io.github.biliz") {
            if (isPackageInstalled(context.packageManager, runtimePkg)) {
                return runtimePkg
            }
        }
        for (pkg in CANDIDATE_PACKAGES) {
            if (isPackageInstalled(context.packageManager, pkg)) {
                return pkg
            }
        }
        return runtimePkg?.takeIf { it.isNotBlank() && it != "io.github.biliz" } ?: "tv.danmaku.bili"
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
            if (exitCode == 0) {
                exitCode
            } else {
                throw IllegalStateException("su exited with code $exitCode")
            }
        }
    }

    fun restartBilibili(
        context: Context,
        prefs: SharedPreferences?,
        callback: (success: Boolean, errorMessage: String?) -> Unit,
    ) {
        val targetPackage = resolveBilibiliPackage(context, prefs)
        val currentPkg = context.packageName

        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val component = launchIntent?.component?.flattenToShortString()
        val suStartCommand = if (!component.isNullOrBlank()) {
            "am start --user 0 -n $component -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
        } else {
            "monkey -p $targetPackage -c android.intent.category.LAUNCHER 1"
        }

        // Scenario 1: Running inside Bilibili process (BiliRoaming pattern / injected via LSPosed or NPatch)
        if (currentPkg == targetPackage) {
            // 1. If Root is available, spawn an independent background subshell to start Bilibili after 600ms
            if (isRootAvailable()) {
                executeSuCommand("nohup sh -c 'sleep 0.6; $suStartCommand' >/dev/null 2>&1 &")
            }

            // 2. Schedule system AlarmManager PendingIntent (fires from system_server, guaranteed across modern Android versions)
            if (launchIntent != null) {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val pendingIntent = PendingIntent.getActivity(context, 199811, launchIntent, flags)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val triggerTime = System.currentTimeMillis() + 600
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager?.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerTime, pendingIntent)
                    } else {
                        alarmManager?.set(AlarmManager.RTC, triggerTime, pendingIntent)
                    }
                }.onFailure {
                    runCatching {
                        alarmManager?.set(AlarmManager.RTC, triggerTime, pendingIntent)
                    }
                }
            }

            callback(true, null)

            if (context is Activity) {
                context.finishAffinity()
            }

            mainHandler.postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(0)
            }, 350)
            return
        }

        // Scenario 2: Running from separate process (BILIz standalone SettingsActivity)
        executor.execute {
            val stopResult = executeSuCommand("am force-stop $targetPackage")
            if (stopResult.isSuccess) {
                Thread.sleep(400)
                executeSuCommand(suStartCommand)
                mainHandler.post {
                    if (launchIntent != null) {
                        runCatching { context.startActivity(launchIntent) }
                    }
                    callback(true, null)
                }
            } else {
                mainHandler.post {
                    // Non-root environment (NPatch / LSPatch / no root):
                    // Launch Bilibili to foreground so user can easily kill & restart it
                    if (launchIntent != null) {
                        runCatching { context.startActivity(launchIntent) }
                    }
                    callback(false, stopResult.exceptionOrNull()?.message)
                }
            }
        }
    }

    fun showRestartBilibiliDialog(
        activity: Activity,
        prefs: SharedPreferences,
        onRestartSuccess: (() -> Unit)? = null,
    ) {
        val targetPackage = resolveBilibiliPackage(activity, prefs)
        val currentPkg = activity.packageName
        val isInsideBilibili = currentPkg == targetPackage

        val message = if (isInsideBilibili) {
            "将立即重新启动哔哩哔哩以使所有设置生效。是否继续？"
        } else {
            activity.getString(R.string.restart_dialog_message)
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.restart_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.restart_dialog_confirm) { _, _ ->
                Toast.makeText(activity, R.string.restart_in_progress, Toast.LENGTH_SHORT).show()
                restartBilibili(activity, prefs) { success, _ ->
                    if (success) {
                        Toast.makeText(activity, R.string.restart_success, Toast.LENGTH_SHORT).show()
                        onRestartSuccess?.invoke()
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("提示")
                            .setMessage("未检测到 Root 权限，无法在后台强制停止哔哩哔哩进程。\n\n已为您自动拉起哔哩哔哩客户端。免 Root（如 NPatch / LSPatch）环境下，请在系统多任务列表（后台）中将原哔哩哔哩向上划掉彻底退出，然后重新打开即可完全生效。")
                            .setPositiveButton("我知道了") { _, _ ->
                                onRestartSuccess?.invoke()
                            }
                            .show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
