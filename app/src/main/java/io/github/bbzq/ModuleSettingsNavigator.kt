package io.github.bbzq

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

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
                Toast.makeText(context, "无法打开 BBZQ 设置", Toast.LENGTH_SHORT).show()
            }
    }

    private const val MODULE_PACKAGE = "io.github.bbzq"
    private const val SETTINGS_ACTIVITY = "io.github.bbzq.SettingsActivity"
}
