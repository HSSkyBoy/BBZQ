package io.github.bzzq.hooks

import android.content.SharedPreferences
import io.github.bzzq.ModuleSettings
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Hook to capture access_key from Bilibili's internal storage or network responses.
 */
class AccessKeyCaptureHook(
    override val targetPackageName: String,
) : AppHook {
    override fun install(xposed: XposedInterface, packageReady: PackageReadyParam, log: (String, Throwable?) -> Unit) {
        val classLoader = packageReady.getClassLoader()
        val prefs = xposed.getRemotePreferences(ModuleSettings.PREFS_NAME)

        // Capture from Token info
        runCatching {
            val tokenClass = Class.forName("com.bilibili.nativelibrary.BiliBiliToken", false, classLoader)
            val getAccessToken = tokenClass.getDeclaredMethod("getAccessToken")
            xposed.hook(getAccessToken).intercept { chain ->
                val token = chain.proceed() as? String
                if (!token.isNullOrEmpty()) {
                    saveAccessKey(prefs, token)
                }
                token
            }
        }

        // Also try to capture from common storage classes if they exist
        runCatching {
            val loginHelperClass = Class.forName("com.bilibili.lib.login.model.AccessToken", false, classLoader)
            // This is a data class, we can hook the constructor or getters
            val getAccessToken = loginHelperClass.getDeclaredMethod("getAccessToken")
            xposed.hook(getAccessToken).intercept { chain ->
                val token = chain.proceed() as? String
                if (!token.isNullOrEmpty()) {
                    saveAccessKey(prefs, token)
                }
                token
            }
        }
    }

    private fun saveAccessKey(prefs: SharedPreferences, token: String) {
        val lastKey = prefs.getString(ModuleSettings.KEY_LAST_ACCESS_KEY, null)
        if (lastKey != token) {
            prefs.edit().putString(ModuleSettings.KEY_LAST_ACCESS_KEY, token).apply()
        }
    }
}
