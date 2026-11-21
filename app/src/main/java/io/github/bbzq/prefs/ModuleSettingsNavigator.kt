package io.github.bbzq.prefs

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.github.bbzq.RuntimeEnvironmentInfo
import io.github.bbzq.SettingsActivity
import java.util.concurrent.TimeUnit

object ModuleSettingsNavigator {
    fun open(
        context: Context,
        runtimeValues: Bundle? = null,
        page: String = SettingsActivity.PAGE_ROOT,
    ) {
        val intent = Intent().apply {
            component = ComponentName(MODULE_PACKAGE, SETTINGS_ACTIVITY)
            runtimeValues?.let { putExtra(RuntimeEnvironmentInfo.EXTRA_RUNTIME_VALUES, it) }
            putExtra(SettingsActivity.EXTRA_PAGE, page)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        runCatching { context.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "open SettingsActivity failed", it)
                // 部分 ROM 的套件可見性過濾會讓宿主解析不到模組 Activity，改走 root 兜底
                if (it is ActivityNotFoundException) {
                    openViaRoot(context.applicationContext ?: context, page)
                } else {
                    toast(context, "无法打开 BBZQ 设置")
                }
            }
    }

    /** 以 root 身分啟動設定頁；無 root 或被拒絕時提示原因。此路徑帶不了宿主傳來的執行環境資料。 */
    private fun openViaRoot(context: Context, page: String) {
        Thread {
            val started = runCatching {
                val command = "am start -n $MODULE_PACKAGE/$SETTINGS_ACTIVITY --es ${SettingsActivity.EXTRA_PAGE} ${shellQuote(page)}"
                val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                val finished = process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                finished && process.exitValue() == 0
            }.onFailure { Log.w(TAG, "root fallback failed", it) }.getOrDefault(false)
            if (!started) {
                toast(context, "无法打开 BBZQ 设置：系统限制了宿主访问模组，请从桌面图标进入 BBZQ")
            }
        }.start()
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private const val TAG = "BBZQ"
    private const val MODULE_PACKAGE = "io.github.bbzq"
    private const val SETTINGS_ACTIVITY = "io.github.bbzq.SettingsActivity"
    private const val ROOT_TIMEOUT_SECONDS = 5L
}
