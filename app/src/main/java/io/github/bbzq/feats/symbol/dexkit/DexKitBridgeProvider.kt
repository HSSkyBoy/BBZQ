package io.github.bbzq.feats.symbol.dexkit

import android.content.Context
import android.os.Build
import android.os.Process
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.util.zip.ZipFile

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
        context: Context? = null,
        recordError: (String) -> Unit,
        log: (String) -> Unit,
    ): DexKitScanBridge? {
        ensureNativeLibraryLoaded(context, recordError, log) ?: return null
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
        context: Context?,
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
            val loadError = tryLoadNativeLibrary(context, log)
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
        context: Context?,
        log: (String) -> Unit,
    ): String? {
        val errors = mutableListOf<String>()

        // 1. Try standard System.loadLibrary
        try {
            System.loadLibrary(DEXKIT_LIBRARY_NAME)
            log("DexKitBridge: loaded by System.loadLibrary")
            return null
        } catch (t: Throwable) {
            errors += "System.loadLibrary failed: ${t.scanMessage()}"
        }

        // 2. Discover module APK and native lib directories
        val candidateNativeDirs = mutableListOf<String>()
        val candidateApkPaths = mutableListOf<String>()

        context?.let { ctx ->
            runCatching {
                val appInfo = ctx.packageManager.getApplicationInfo("io.github.bbzq", 0)
                appInfo.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { candidateNativeDirs += it }
                appInfo.sourceDir?.takeIf { it.isNotBlank() }?.let { candidateApkPaths += it }
                appInfo.splitSourceDirs?.forEach { path ->
                    if (!path.isNullOrBlank()) candidateApkPaths += path
                }
            }
            ctx.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() }?.let { candidateNativeDirs += it }
        }

        val cl = DexKitBridgeProvider::class.java.classLoader
        if (cl != null) {
            // LspModuleClassLoader has private field 'apk'
            runCatching {
                val apkField = cl.javaClass.declaredFields.firstOrNull { it.name == "apk" }
                apkField?.isAccessible = true
                (apkField?.get(cl) as? String)?.takeIf { it.isNotBlank() }?.let { candidateApkPaths += it }
            }
            // Or extract from toString(), e.g. LspModuleClassLoader[module=/data/app/.../base.apk, ...]
            runCatching {
                val clStr = cl.toString()
                Regex("module=([^,\\]]+)").find(clStr)?.groupValues?.get(1)?.trim()?.let {
                    if (it.isNotBlank()) candidateApkPaths += it
                }
            }
        }

        // 3. Try loading directly from candidate native directories
        val libraryFileName = "lib$DEXKIT_LIBRARY_NAME.so"
        for (dirPath in candidateNativeDirs.distinct()) {
            val file = File(dirPath, libraryFileName)
            if (file.isFile) {
                try {
                    System.load(file.absolutePath)
                    log("DexKitBridge: loaded from ${file.absolutePath}")
                    return null
                } catch (t: Throwable) {
                    errors += "load(${file.absolutePath}) failed: ${t.scanMessage()}"
                }
            }
        }

        // 4. Try loading direct from APK (supported on Android 7.0+ if uncompressed STORED entries)
        val is64Bit = Process.is64Bit()
        val preferredAbis = if (is64Bit) listOf("arm64-v8a", "x86_64") else listOf("armeabi-v7a", "armeabi", "x86")
        val candidateAbis = buildList {
            addAll(preferredAbis)
            Build.SUPPORTED_ABIS?.forEach { if (!it.isNullOrBlank()) add(it) }
        }.distinct()

        val validApkPaths = candidateApkPaths.distinct().filter { File(it).isFile }
        for (apkPath in validApkPaths) {
            for (abi in candidateAbis) {
                val apkSoPath = "$apkPath!/lib/$abi/$libraryFileName"
                try {
                    System.load(apkSoPath)
                    log("DexKitBridge: loaded direct from $apkSoPath")
                    return null
                } catch (t: Throwable) {
                    if (abi == preferredAbis.firstOrNull()) {
                        errors += "loadDirect($apkSoPath) failed: ${t.scanMessage()}"
                    }
                }
            }
        }

        // 5. Try extracting libdexkit.so from module APK to writable executable code cache dir
        val targetDirs = buildList {
            context?.codeCacheDir?.let { add(it) }
            context?.cacheDir?.let { add(it) }
            context?.noBackupFilesDir?.let { add(it) }
            context?.filesDir?.let { add(it) }
        }.distinct().filter { it.isDirectory || it.mkdirs() }

        for (apkPath in validApkPaths) {
            val zip = runCatching { ZipFile(File(apkPath)) }.getOrNull() ?: continue
            zip.use { zipFile ->
                for (abi in candidateAbis) {
                    val entry = zipFile.getEntry("lib/$abi/$libraryFileName") ?: continue
                    for (targetDir in targetDirs) {
                        val outFile = File(targetDir, "libdexkit_${abi}.so")
                        try {
                            if (!outFile.exists() || outFile.length() != entry.size) {
                                val tempFile = File(targetDir, "libdexkit_${abi}_temp_${System.currentTimeMillis()}.so")
                                zipFile.getInputStream(entry).use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                if (outFile.exists()) outFile.delete()
                                tempFile.renameTo(outFile)
                            }
                            outFile.setReadable(true, false)
                            outFile.setExecutable(true, false)
                            System.load(outFile.absolutePath)
                            log("DexKitBridge: loaded extracted lib from ${outFile.absolutePath}")
                            return null
                        } catch (t: Throwable) {
                            errors += "extractAndLoad(${outFile.absolutePath}) failed: ${t.scanMessage()}"
                        }
                    }
                }
            }
        }

        return errors.joinToString("; ").take(420)
    }
}

internal fun Throwable.scanMessage(): String =
    "${javaClass.name}: ${message.orEmpty()}".replace('\n', ' ').take(420)
