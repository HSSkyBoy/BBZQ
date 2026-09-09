package io.github.bbzq.feats.hook

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BaseRoamingHook
import io.github.bbzq.feats.RoamingEnv
import io.github.bbzq.feats.hookAfterMethod

class FakeWifiHook(env: RoamingEnv) : BaseRoamingHook(env) {

    override fun startHook() {
        if (!ModuleSettings.isFakeWifiEnabled(prefs)) return

        // 1. Hook NetworkCapabilities
        runCatching {
            env.hookAfterMethod(NetworkCapabilities::class.java, "hasTransport", Int::class.javaPrimitiveType!!) { param ->
                val transport = param.args[0] as? Int ?: return@hookAfterMethod
                if (transport == NetworkCapabilities.TRANSPORT_WIFI) {
                    param.result = true
                } else if (transport == NetworkCapabilities.TRANSPORT_CELLULAR) {
                    param.result = false
                }
            }
        }.onFailure { log("FakeWifi: failed to hook NetworkCapabilities.hasTransport", it) }

        runCatching {
            env.hookAfterMethod(NetworkCapabilities::class.java, "hasCapability", Int::class.javaPrimitiveType!!) { param ->
                val capability = param.args[0] as? Int ?: return@hookAfterMethod
                if (capability == NetworkCapabilities.NET_CAPABILITY_NOT_METERED) {
                    param.result = true
                }
            }
        }.onFailure { log("FakeWifi: failed to hook NetworkCapabilities.hasCapability", it) }

        // 2. Hook NetworkInfo
        runCatching {
            env.hookAfterMethod(NetworkInfo::class.java, "getType") { param ->
                param.result = ConnectivityManager.TYPE_WIFI
            }
        }.onFailure { log("FakeWifi: failed to hook NetworkInfo.getType", it) }

        runCatching {
            env.hookAfterMethod(NetworkInfo::class.java, "getTypeName") { param ->
                param.result = "WIFI"
            }
        }.onFailure { log("FakeWifi: failed to hook NetworkInfo.getTypeName", it) }

        // 3. Hook ConnectivityManager.isActiveNetworkMetered
        runCatching {
            env.hookAfterMethod(ConnectivityManager::class.java, "isActiveNetworkMetered") { param ->
                param.result = false
            }
        }.onFailure { log("FakeWifi: failed to hook ConnectivityManager.isActiveNetworkMetered", it) }

        log("FakeWifiHook installed successfully")
    }
}
