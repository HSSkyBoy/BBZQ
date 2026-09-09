package io.github.biliz.feats.symbol.dexkit

import org.luckypray.dexkit.DexKitBridge
import java.io.File

class DexKitScanBridge(
    val sourcePath: String,
    val bridge: DexKitBridge,
) : AutoCloseable {
    override fun close() {
        bridge.close()
    }
}

object DexKitBridgeProvider {
    private const val DEXKIT_LIBRARY_NAME = "dexkit"

    @Volatile
    private var nativeLibraryLoaded = false

    @Volatile
    private var nativeLibraryLoadError: String? = null

    fun openFirstAvailable(
        sourcePaths: List<String>,
        recordError: (String) -> Unit,
        log: (String) -> Unit,
    ): DexKitScanBridge? {
        ensureNativeLibraryLoaded(recordError, log) ?: return null
        var failed = 0
        var firstError: String? = null
        for (sourcePath in sourcePaths.distinct()) {
            if (!File(sourcePath).isFile) continue
            try {
                return DexKitScanBridge(sourcePath, DexKitBridge.create(sourcePath))
            } catch (t: Throwable) {
                failed++
                if (firstError == null) firstError = t.scanMessage()
            }
        }
        if (failed > 0) {
            recordError("DexKitBridge open failed count=$failed first=$firstError")
        }
        return null
    }

    private fun ensureNativeLibraryLoaded(
        recordError: (String) -> Unit,
        log: (String) -> Unit,
    ): Boolean? {
        if (nativeLibraryLoaded) return true
        nativeLibraryLoadError?.let {
            recordError("DexKit native unavailable: $it")
            return null
        }
        return synchronized(this) {
            if (nativeLibraryLoaded) return@synchronized true
            nativeLibraryLoadError?.let {
                recordError("DexKit native unavailable: $it")
                return@synchronized null
            }
            val loadError = tryLoadNativeLibrary(log)
            if (loadError == null) {
                nativeLibraryLoaded = true
                true
            } else {
                nativeLibraryLoadError = loadError
                recordError("DexKit native unavailable: $loadError")
                null
            }
        }
    }

    private fun tryLoadNativeLibrary(
        log: (String) -> Unit,
    ): String? {
        return try {
            System.loadLibrary(DEXKIT_LIBRARY_NAME)
            log("DexKitBridge: loaded by System.loadLibrary")
            null
        } catch (t: Throwable) {
            "System.loadLibrary failed: ${t.scanMessage()}"
        }
    }
}

internal fun Throwable.scanMessage(): String =
    "${javaClass.name}: ${message.orEmpty()}".replace('\n', ' ').take(420)
