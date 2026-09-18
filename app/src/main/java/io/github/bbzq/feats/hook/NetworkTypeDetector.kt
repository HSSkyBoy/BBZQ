package io.github.bbzq.feats.hook

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.telephony.TelephonyManager
import java.lang.reflect.Field

object NetworkTypeDetector {
    private var transportField: Field? = null
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
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val caps = cm?.getNetworkCapabilities(activeNetwork)
                if (caps != null) {
                    val field = transportField ?: runCatching {
                        caps.javaClass.getDeclaredField("mTransportTypes").also { f ->
                            f.isAccessible = true
                            transportField = f
                        }
                    }.getOrNull()
                    if (field != null) {
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
            tm?.dataState == TelephonyManager.DATA_CONNECTED
        }.getOrDefault(false)
    }
}

