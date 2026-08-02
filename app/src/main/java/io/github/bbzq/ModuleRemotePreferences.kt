package io.github.bbzq

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object ModuleRemotePreferences : XposedServiceHelper.OnServiceListener {
    private const val TAG = "BBZQ"

    private val registered = AtomicBoolean(false)
    @Volatile private var appContext: Context? = null
    @Volatile private var service: XposedService? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        appContext = context.applicationContext ?: context
        if (registered.compareAndSet(false, true)) {
            XposedServiceHelper.registerListener(this)
        }
    }

    fun attach(context: Context, prefs: SharedPreferences) {
        init(context)
        syncLocalToRemote(prefs)
    }

    override fun onServiceBind(service: XposedService) {
        this.service = service
        appContext?.let { context ->
            syncLocalToRemote(context.moduleSettingsPreferences())
        }
    }

    override fun onServiceDied(service: XposedService) {
        if (this.service === service) this.service = null
    }

    private fun syncLocalToRemote(prefs: SharedPreferences) {
        val values = prefs.all
        withRemoteEditor { editor ->
            // Runtime-discovered component catalogs are written by the host process only.
            // Clearing here when the module app binds would erase them before the settings
            // activity can import its host snapshot, which is especially visible in APKS builds.
            values.forEach { (key, value) -> editor.putValue(key, value) }
        }
    }

    fun applyOperations(operations: List<PreferenceOperation>) {
        if (operations.isEmpty()) return
        withRemoteEditor { editor ->
            operations.forEach { operation ->
                when (operation) {
                    PreferenceOperation.Clear -> editor.clear()
                    is PreferenceOperation.Remove -> editor.remove(operation.key)
                    is PreferenceOperation.Put -> editor.putValue(operation.key, operation.value)
                }
            }
        }
    }

    fun requestSymbolCacheRefresh(callback: (String) -> Unit) {
        requestSymbolCacheRefresh(callback, serviceWaitAttempt = 0)
    }

    private fun requestSymbolCacheRefresh(
        callback: (String) -> Unit,
        serviceWaitAttempt: Int,
    ) {
        init(appContext ?: run {
            fail(callback, "Xposed 服务尚未连接")
            return
        })
        val currentService = service ?: run {
            if (serviceWaitAttempt < SERVICE_WAIT_MAX_ATTEMPTS) {
                mainHandler.postDelayed(
                    { requestSymbolCacheRefresh(callback, serviceWaitAttempt + 1) },
                    SERVICE_WAIT_RETRY_MS,
                )
            } else {
                fail(callback, "Xposed 服务尚未连接")
            }
            return
        }
        val context = appContext ?: run {
            fail(callback, "Xposed 服务尚未连接")
            return
        }
        thread(name = SYMBOL_REFRESH_THREAD_NAME, isDaemon = true) {
            runCatching {
                if (currentService.apiVersion < XposedService.API_102) {
                    fail(callback, "当前框架不支持 API102 远程配置")
                    return@thread
                }
                val requestId = UUID.randomUUID().toString()
                submitSymbolScanRequest(currentService, requestId)
                scheduleSymbolScanRequestCleanup(currentService, requestId)
                dispatch(callback, context.getString(R.string.symbol_cache_refresh_submitted_toast))
                Log.i(TAG, "symbol cache refresh request sent id=$requestId")
            }.onFailure {
                fail(callback, "请求重扫失败：${it.javaClass.simpleName}: ${it.message}")
            }
        }
    }

    private fun fail(
        callback: (String) -> Unit,
        message: String,
    ) {
        Log.w(TAG, message)
        dispatch(callback, message)
    }

    private fun submitSymbolScanRequest(
        service: XposedService,
        requestId: String,
    ) {
        val editor = service.remoteSettingsPreferences().edit()
        editor.putString(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID, requestId)
        check(editor.commit()) { "remote preference commit failed" }
        Log.i(TAG, "symbol cache refresh request submitted id=$requestId")
    }

    private fun scheduleSymbolScanRequestCleanup(
        service: XposedService,
        requestId: String,
    ) {
        mainHandler.postDelayed(
            {
                runCatching {
                    val prefs = service.remoteSettingsPreferences()
                    if (prefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID, null) != requestId) {
                        return@runCatching
                    }
                    prefs.edit()
                        .remove(ModuleSettings.KEY_SYMBOL_SCAN_REFRESH_REQUEST_ID)
                        .commit()
                }.onFailure {
                    Log.w(TAG, "symbol cache refresh request cleanup failed: ${it.javaClass.simpleName}: ${it.message}")
                }
            },
            SYMBOL_SCAN_REQUEST_CLEANUP_MS,
        )
    }

    private fun dispatch(
        callback: (String) -> Unit,
        message: String,
    ) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(message)
        } else {
            mainHandler.post { callback(message) }
        }
    }

    private fun withRemoteEditor(block: (SharedPreferences.Editor) -> Unit) {
        val currentService = service ?: return
        runCatching {
            val editor = currentService.remoteSettingsPreferences().edit()
            block(editor)
            editor.commit()
        }.onFailure {
            Log.w(TAG, "sync remote preferences failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun SharedPreferences.Editor.putValue(key: String, value: Any?) {
        when (value) {
            null -> remove(key)
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is String -> putString(key, value)
            is Set<*> -> putStringSet(key, safeStringSet(value))
            is List<*> -> putStringSet(key, safeStringSet(value))
            else -> putString(key, value.toString())
        }
    }

    private fun Context.moduleSettingsPreferences(): SharedPreferences =
        getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)

    private fun XposedService.remoteSettingsPreferences(): SharedPreferences =
        getRemotePreferences(ModuleSettings.PREFS_NAME)

    private const val SYMBOL_REFRESH_THREAD_NAME = "BBZQ-SymbolRefresh"
    private const val SERVICE_WAIT_RETRY_MS = 500L
    private const val SERVICE_WAIT_MAX_ATTEMPTS = 10
    private const val SYMBOL_SCAN_REQUEST_CLEANUP_MS = 30_000L
}

sealed interface PreferenceOperation {
    data object Clear : PreferenceOperation
    data class Remove(val key: String) : PreferenceOperation
    data class Put(val key: String, val value: Any?) : PreferenceOperation
}
