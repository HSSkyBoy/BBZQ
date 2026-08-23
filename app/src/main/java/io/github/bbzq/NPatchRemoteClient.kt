package io.github.bbzq

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Client for connecting to NPatch Remote Store Provider (top.nkbe.npatch.remote).
 * Enables rootless module configuration synchronization with NPatch Manager.
 */
object NPatchRemoteClient {
    private const val TAG = "BBZQ-Remote"
    const val DEFAULT_NPATCH_AUTHORITY = "top.nkbe.npatch.remote"

    private val isConnecting = AtomicBoolean(false)
    @Volatile private var isConnected = false

    fun isConnected(): Boolean = isConnected

    fun connectAsync(context: Context): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        thread(name = "BBZQ-NPatchRemoteConnect", isDaemon = true) {
            val result = connect(context)
            future.complete(result)
        }
        return future
    }

    fun connect(context: Context, customAuthority: String? = null): Boolean {
        val authority = customAuthority ?: DEFAULT_NPATCH_AUTHORITY
        val targetPackage = context.packageName
        // Protocol must match NPatch manager RemoteApiProvider:
        // https://github.com/7723mod/NPatch/blob/miuix/manager/src/main/java/top/nkbe/npatch/manager/RemoteApiProvider.kt
        // (extras key "modulePackageName", see official SDK NPatchRemoteClient.requestBinder)
        val extras = Bundle().apply {
            putString("modulePackageName", targetPackage)
        }

        val binder = queryProviderBinder(context, authority, targetPackage, extras)
        if (binder != null && binder.isBinderAlive) {
            if (feedBinderToXposedHelper(binder)) {
                isConnected = true
                Log.i(TAG, "Successfully connected to NPatch Remote XposedService via authority: $authority")
                return true
            }
        }
        return false
    }

    fun tryConnectBackground(context: Context, maxRetries: Int = 3, retryIntervalMs: Long = 1000L) {
        if (!isConnecting.compareAndSet(false, true)) return

        thread(name = "BBZQ-RemoteProbe", isDaemon = true) {
            try {
                for (attempt in 1..maxRetries) {
                    if (connect(context)) {
                        break
                    }
                    if (attempt < maxRetries) {
                        Thread.sleep(retryIntervalMs)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "NPatch background connect probe error", t)
            } finally {
                isConnecting.set(false)
            }
        }
    }

    private fun queryProviderBinder(
        context: Context,
        authority: String,
        targetPackage: String,
        extras: Bundle,
    ): IBinder? {
        val uri = Uri.parse("content://$authority")
        // Manager RemoteApiProvider only accepts "getRemoteService" (writable, for module
        // apps) and "getInjectedRemoteService" (read-only, for scoped injected targets).
        // The settings app needs the writable one; other method names fall through to
        // ContentProvider.call -> null, which broke all rootless config sync before.
        val methods = listOf("getRemoteService")

        for (method in methods) {
            try {
                val bundle = context.contentResolver.call(uri, method, targetPackage, extras)
                    ?: continue
                val binder = bundle.getBinder("binder")
                    ?: bundle.getBinder("service")
                    ?: bundle.getBinder("xposed_service")
                    ?: bundle.getBinder("extra_binder")
                if (binder != null) {
                    return binder
                }
            } catch (e: SecurityException) {
                Log.d(TAG, "NPatch Provider $authority security exception: ${e.message}")
                break
            } catch (t: Throwable) {
                Log.d(TAG, "Querying NPatch $authority with method $method failed: ${t.message}")
            }
        }
        return null
    }

    private fun feedBinderToXposedHelper(binder: IBinder): Boolean {
        return runCatching {
            val helperClass = Class.forName("io.github.libxposed.service.XposedServiceHelper")
            val method = helperClass.getDeclaredMethod("onBinderReceived", IBinder::class.java)
            method.isAccessible = true
            method.invoke(null, binder)
            true
        }.getOrElse { e ->
            Log.w(TAG, "Failed to feed NPatch remote binder to XposedServiceHelper", e)
            false
        }
    }
}
