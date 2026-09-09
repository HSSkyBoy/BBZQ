package io.github.biliz

import android.app.Application

class BilizApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ModuleRemotePreferences.init(this)
        autoSeedDefaultConfig()
        applyDesktopIconSetting()
    }

    private fun autoSeedDefaultConfig() {
        val prefs = getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean("config_preloaded_v1", false)) {
            runCatching {
                assets.open("default_config.zip").use { input ->
                    val bytes = input.readBytes()
                    if (bytes.isNotEmpty()) {
                        val result = ConfigPorter.importFromZip(bytes, prefs)
                        if (result is ConfigPorter.ImportResult.Success) {
                            prefs.edit().putBoolean("config_preloaded_v1", true).apply()
                        }
                    }
                }
            }
        }
    }

    private fun applyDesktopIconSetting() {
        val prefs = getSharedPreferences(ModuleSettings.PREFS_NAME, MODE_PRIVATE)
        val hideIcon = ModuleSettings.isHideDesktopIconEnabled(prefs)
        DesktopIconHelper.applySetting(this, hideIcon)
    }
}
