package io.github.bbzq

import android.app.Application

class BbzqApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModuleRemotePreferences.init(this)
        applyDesktopIconSetting()
    }

    private fun applyDesktopIconSetting() {
        val prefs = getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE)
        val hideIcon = ModuleSettings.isHideDesktopIconEnabled(prefs)
        DesktopIconHelper.applySetting(this, hideIcon)
    }
}
