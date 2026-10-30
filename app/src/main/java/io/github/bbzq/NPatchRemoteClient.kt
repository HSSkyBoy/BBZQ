package io.github.bbzq

import android.content.Context
import android.os.IBinder
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import top.nkbe.npatch.remote.NPatchRemoteClient as OfficialNPatchRemoteClient

/**
 * Client adapter connecting to NPatch Manager via official NPatch-Remote-API SDK.
 * Enables rootless module configuration synchronization with NPatch Remote Store.
 */
object NPatchRemoteClient {
    private const val TAG = "BBZQ-Remote"

    private val isConnecting = AtomicBoolean(false)
    @Volatile private var isConnected = false

    fun isConnected(): Boolean = isConnected

    fun isAvailable(context: Context): Boolean = runCatching {
        OfficialNPatchRemoteClient.isAvailable(context)
    }.getOrDefault(false)

    fun connect(context: Context): Boolean = runCatching {
        val client = OfficialNPatchRemoteClient.connect(context)
        val binder = client.service.asBinder()
        if (binder.isBinderAlive && feedBinderToXposedHelper(binder)) {
            isConnected = true
            Log.i(TAG, "Successfully connected to NPatch Remote XposedService")
            true
        } else {
            false
        }
    }.getOrDefault(false)

    fun connectAsync(context: Context): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        OfficialNPatchRemoteClient.connectAsync(context)
            .thenAccept { client ->
                val binder = runCatching { client.service.asBinder() }.getOrNull()
                val success = if (binder != null && binder.isBinderAlive) {
                    feedBinderToXposedHelper(binder).also { fed ->
                        if (fed) isConnected = true
                    }
                } else {
                    false
                }
                future.complete(success)
            }
            .exceptionally { error ->
                Log.d(TAG, "NPatch Remote async connection failed: ${error.message}")
                future.complete(false)
                null
            }
        return future
    }

    fun tryConnectBackground(context: Context) {
        if (isConnected || !isConnecting.compareAndSet(false, true)) return

        OfficialNPatchRemoteClient.connectAsync(context)
            .thenAccept { client ->
                val binder = runCatching { client.service.asBinder() }.getOrNull()
                if (binder != null && binder.isBinderAlive) {
                    if (feedBinderToXposedHelper(binder)) {
                        isConnected = true
                        Log.i(TAG, "Successfully connected to NPatch Remote XposedService")
                    }
                }
                isConnecting.set(false)
            }
            .exceptionally { error ->
                Log.d(TAG, "NPatch Remote background connect skipped: ${error.message}")
                isConnecting.set(false)
                null
            }
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
