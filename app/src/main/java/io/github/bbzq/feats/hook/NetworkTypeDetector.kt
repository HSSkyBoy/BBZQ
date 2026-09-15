package io.github.bbzq.feats.hook

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager

object NetworkTypeDetector {
    fun isCellular(context: Context): Boolean {
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) cm?.activeNetwork else null
            if (activeNetwork != null) {
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    val field = runCatching { caps.javaClass.getDeclaredField("mTransportTypes") }.getOrNull()
                    if (field != null) {
                        field.isAccessible = true
                        val mask = (field.get(caps) as? Number)?.toLong() ?: 0L
                        val hasCellular = (mask and (1L shl NetworkCapabilities.TRANSPORT_CELLULAR)) != 0L
                        val hasWifi = (mask and (1L shl NetworkCapabilities.TRANSPORT_WIFI)) != 0L
                        if (hasCellular && !hasWifi) return true
                    }
                }
            }
        }
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val dataState = tm?.dataState
            dataState == TelephonyManager.DATA_CONNECTED
        }.getOrDefault(false)
    }
}
