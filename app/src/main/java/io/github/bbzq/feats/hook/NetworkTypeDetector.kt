package io.github.bbzq.feats.hook

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.telephony.TelephonyManager

object NetworkTypeDetector {
    private var cachedResult: Boolean = false
    private var cacheTimestamp: Long = 0L
    private const val CACHE_TTL_MS = 10_000L

    fun isCellular(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - cacheTimestamp < CACHE_TTL_MS) return cachedResult
        val result = detectCellular(context)
        cachedResult = result
        cacheTimestamp = now
        return result
    }

    private fun detectCellular(context: Context): Boolean {
        runCatching {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
                return@runCatching
            }
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    NetworkTransportReadScope.read {
                        when {
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> false
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                            else -> null
                        }
                    }?.let { return it }
                }
            }
        }
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.dataState == TelephonyManager.DATA_CONNECTED
        }.getOrDefault(false)
    }
}

