package io.github.bbzq

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object DesktopIconHelper {
    private const val LAUNCHER_ACTIVITY = "io.github.bbzq.IntroActivity"

    fun applySetting(context: Context, hide: Boolean) {
        val component = ComponentName(context, LAUNCHER_ACTIVITY)
        val pm = context.packageManager
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        pm.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP,
        )
    }
}
