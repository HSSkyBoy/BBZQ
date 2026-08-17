package io.github.bbzq

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import io.github.bbzq.feats.symbol.BiliSymbolResolver
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RuntimeEnvironmentInfo {
    private const val UNKNOWN = "unknown"
    const val EXTRA_RUNTIME_VALUES = "io.github.bbzq.extra.RUNTIME_VALUES"

    private val targetPackages = listOf(
        "tv.danmaku.bili",
        "com.bilibili.app.blue",
        "top.nkbe.npatch",
    )

    fun versionSummary(context: Context, prefs: SharedPreferences): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        return buildString {
            append("APP ")
            append(host.displayName)
            append('\n')
            append("Module ")
            append(module.displayName)
        }
    }

    fun runtimeEnvironmentJson(context: Context, prefs: SharedPreferences): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        val serviceInfo = ModuleRemotePreferences.getFrameworkInfo()

        val xposedApiVersion = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_API_VERSION)
            .takeUnless { it == UNKNOWN }
            ?: serviceInfo?.apiVersion
            ?: UNKNOWN

        val xposedFrameworkName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_NAME)
            .takeUnless { it == UNKNOWN }
            ?: serviceInfo?.frameworkName?.takeIf { it.isNotBlank() }
            ?: UNKNOWN

        val xposedFrameworkVersion = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION)
            .takeUnless { it == UNKNOWN }
            ?: serviceInfo?.frameworkVersion?.takeIf { it.isNotBlank() }
            ?: UNKNOWN

        val xposedFrameworkVersionCode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE)
            .takeUnless { it == UNKNOWN }
            ?: serviceInfo?.frameworkVersionCode?.takeIf { it.isNotBlank() }
            ?: UNKNOWN

        val xposedFrameworkProperties = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES)
            .takeUnless { it == UNKNOWN }
            ?: serviceInfo?.frameworkProperties?.takeIf { it.isNotBlank() }
            ?: UNKNOWN

        val runtimeKind = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_KIND)
            .takeUnless { it == UNKNOWN }
            ?: classifyRuntimeKind(xposedFrameworkName)

        val hostSourceKind = resolveHostSourceKind(context, prefs, host.packageName)

        val patchMode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_PATCH_MODE)
            .takeUnless { it == UNKNOWN }
            ?: classifyPatchMode(context, hostSourceKind)

        val processName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_PROCESS_NAME)
            .takeUnless { it == UNKNOWN }
            ?: (context.applicationInfo?.processName ?: context.packageName)

        val lastUpdateTimeRaw = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_LAST_UPDATE_TIME)
        val lastRuntimeUpdateTime = if (lastUpdateTimeRaw != UNKNOWN) {
            formatRuntimeUpdateTime(lastUpdateTimeRaw)
        } else {
            UNKNOWN
        }

        return JSONObject()
            .put("hostPackageName", host.packageName)
            .put("hostVersionName", host.versionName)
            .put("hostVersionCode", host.versionCode)
            .put("hostSourceKind", hostSourceKind)
            .put("modulePackageName", context.packageName)
            .put("moduleVersionName", module.versionName)
            .put("moduleVersionCode", module.versionCode)
            .put("moduleDebug", isDebuggable(context))
            .put("androidSdk", Build.VERSION.SDK_INT)
            .put("xposedApiVersion", xposedApiVersion)
            .put("xposedFrameworkName", xposedFrameworkName)
            .put("xposedFrameworkVersion", xposedFrameworkVersion)
            .put("xposedFrameworkVersionCode", xposedFrameworkVersionCode)
            .put("xposedFrameworkProperties", xposedFrameworkProperties)
            .put("runtimeKind", runtimeKind)
            .put("patchMode", patchMode)
            .put("processName", processName)
            .put("lastRuntimeUpdateTime", lastRuntimeUpdateTime)
            .toString(2)
    }

    fun devicesText(context: Context, prefs: SharedPreferences, exportedAtMillis: Long = System.currentTimeMillis()): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        return buildString {
            appendLine("exportedAt=" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(exportedAtMillis)))
            appendLine("deviceBrand=" + Build.BRAND)
            appendLine("deviceManufacturer=" + Build.MANUFACTURER)
            appendLine("deviceModel=" + Build.MODEL)
            appendLine("deviceName=" + Build.DEVICE)
            appendLine("productName=" + Build.PRODUCT)
            appendLine("androidRelease=" + Build.VERSION.RELEASE)
            appendLine("androidSdk=" + Build.VERSION.SDK_INT)
            appendLine("securityPatch=" + (Build.VERSION.SECURITY_PATCH ?: UNKNOWN))
            appendLine("hostPackage=" + host.packageName)
            appendLine("hostVersion=" + host.displayName)
            appendLine("hostSourceKind=" + resolveHostSourceKind(context, prefs, host.packageName))
            appendLine("modulePackage=" + context.packageName)
            appendLine("moduleVersion=" + module.displayName)
            appendLine("runtimeSnapshot=")
            appendLine(runtimeEnvironmentJson(context, prefs))
        }
    }

    fun recordRuntimeSnapshot(
        hostContext: Context,
        processName: String,
        xposed: XposedInterface,
        prefs: SharedPreferences,
    ) {
        val editor = prefs.edit()
        val values = runtimeSnapshotValues(
            hostContext = hostContext,
            processName = processName,
            xposedApiVersion = runCatching { xposed.apiVersion.toString() }.getOrDefault(UNKNOWN),
            xposedFrameworkName = runCatching { xposed.frameworkName }.getOrDefault(UNKNOWN),
            xposedFrameworkVersion = runCatching { xposed.frameworkVersion }.getOrDefault(UNKNOWN),
            xposedFrameworkVersionCode = runCatching { xposed.frameworkVersionCode.toString() }.getOrDefault(UNKNOWN),
            xposedFrameworkProperties = runCatching { xposed.frameworkProperties.toString() }.getOrDefault(UNKNOWN),
        )
        values.forEach { (key, value) ->
            editor.putString(key, value)
        }
        runtimeObservedStringSets(prefs).forEach { (key, items) ->
            if (items.isNotEmpty()) {
                putObservedStringSet(editor, prefs, key, items)
            }
        }
        editor.commit()
    }

    fun runtimeSnapshotBundle(
        hostContext: Context,
        processName: String,
        xposedApiVersion: String,
        xposedFrameworkName: String,
        xposedFrameworkVersion: String,
        xposedFrameworkVersionCode: String,
        xposedFrameworkProperties: String,
        observedPrefs: SharedPreferences? = null,
    ): Bundle {
        return Bundle().apply {
            runtimeSnapshotValues(
                hostContext = hostContext,
                processName = processName,
                xposedApiVersion = xposedApiVersion,
                xposedFrameworkName = xposedFrameworkName,
                xposedFrameworkVersion = xposedFrameworkVersion,
                xposedFrameworkVersionCode = xposedFrameworkVersionCode,
                xposedFrameworkProperties = xposedFrameworkProperties,
            ).forEach { (key, value) -> putString(key, value) }
            runtimeObservedStringSets(observedPrefs).forEach { (key, value) ->
                putStringArrayList(key, ArrayList(value))
            }
        }
    }

    fun applyRuntimeSnapshotFromIntent(intent: Intent?, prefs: SharedPreferences): Boolean {
        val values = intent?.getBundleExtra(EXTRA_RUNTIME_VALUES) ?: return false
        val editor = prefs.edit()
        var changed = false
        values.keySet().forEach { key ->
            if (key in OBSERVED_STRING_SET_KEYS) {
                val items = values.getStringArrayList(key)
                    ?.filterTo(linkedSetOf()) { it.isNotBlank() }
                    .orEmpty()
                if (items.isNotEmpty()) {
                    putObservedStringSet(editor, prefs, key, items)
                    changed = true
                }
            } else {
                val value = values.getString(key) ?: return@forEach
                editor.putString(key, value)
                changed = true
            }
        }
        return changed && editor.commit()
    }

    private fun resolveHostVersion(context: Context, prefs: SharedPreferences): VersionInfo {
        val recordedPackage = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_PACKAGE)
            .takeUnless { it == UNKNOWN }
        if (!recordedPackage.isNullOrBlank()) {
            return VersionInfo(
                packageName = recordedPackage,
                versionName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_VERSION_NAME),
                versionCode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_VERSION_CODE),
            )
        }
        return targetPackages
            .asSequence()
            .mapNotNull { packageVersionOrNull(context, it) }
            .firstOrNull()
            ?: VersionInfo(UNKNOWN, UNKNOWN, UNKNOWN)
    }

    private fun moduleVersion(context: Context): VersionInfo =
        packageVersionOrNull(context, context.packageName)
            ?: VersionInfo(context.packageName, UNKNOWN, UNKNOWN)

    private fun runtimeSnapshotValues(
        hostContext: Context,
        processName: String,
        xposedApiVersion: String,
        xposedFrameworkName: String,
        xposedFrameworkVersion: String,
        xposedFrameworkVersionCode: String,
        xposedFrameworkProperties: String,
    ): Map<String, String> {
        val host = packageVersion(hostContext, hostContext.packageName)
        val sourceKind = classifyHostSource(hostContext.applicationInfo?.sourceDir)
        return linkedMapOf(
            ModuleSettings.KEY_RUNTIME_HOST_PACKAGE to host.packageName,
            ModuleSettings.KEY_RUNTIME_HOST_VERSION_NAME to host.versionName,
            ModuleSettings.KEY_RUNTIME_HOST_VERSION_CODE to host.versionCode,
            ModuleSettings.KEY_RUNTIME_HOST_SOURCE_KIND to sourceKind,
            ModuleSettings.KEY_RUNTIME_XPOSED_API_VERSION to xposedApiVersion.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_NAME to xposedFrameworkName.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION to xposedFrameworkVersion.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE to xposedFrameworkVersionCode.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES to xposedFrameworkProperties.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_KIND to classifyRuntimeKind(xposedFrameworkName),
            ModuleSettings.KEY_RUNTIME_PATCH_MODE to classifyPatchMode(hostContext, sourceKind),
            ModuleSettings.KEY_RUNTIME_PROCESS_NAME to processName.ifBlank { UNKNOWN },
            ModuleSettings.KEY_RUNTIME_LAST_UPDATE_TIME to System.currentTimeMillis().toString(),
        ).apply {
            putAll(symbolScanStatusValues(hostContext))
        }
    }

    private fun runtimeObservedStringSets(prefs: SharedPreferences?): Map<String, Set<String>> {
        if (prefs == null) return emptyMap()
        return buildMap {
            ModuleSettings.getKnownBottomBarItems(prefs)
                .takeIf { it.isNotEmpty() }
                ?.let { put(ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS, it) }
            ModuleSettings.getKnownHomeRecommendTabs(prefs)
                .takeIf { it.isNotEmpty() }
                ?.let { put(ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS, it) }
            ModuleSettings.getKnownHomeComponents(prefs)
                .takeIf { it.isNotEmpty() }
                ?.let { put(ModuleSettings.KEY_KNOWN_HOME_COMPONENTS, it) }
            ModuleSettings.getKnownMineComponents(prefs)
                .takeIf { it.isNotEmpty() }
                ?.let { put(ModuleSettings.KEY_KNOWN_MINE_COMPONENTS, it) }
        }
    }

    private fun putObservedStringSet(
        editor: SharedPreferences.Editor,
        prefs: SharedPreferences,
        key: String,
        items: Set<String>,
    ) {
        val localItems = prefs.getStringSet(key, emptySet()).orEmpty()
        val updated = if (items.size < localItems.size) localItems else items
        editor.putStringSet(key, updated.toMutableSet())
        when (key) {
            ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS -> ModuleSettings.cacheKnownBottomBarItems(updated)
            ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS -> ModuleSettings.cacheKnownHomeRecommendTabs(updated)
            ModuleSettings.KEY_KNOWN_HOME_COMPONENTS -> ModuleSettings.cacheKnownHomeComponents(updated)
            ModuleSettings.KEY_KNOWN_MINE_COMPONENTS -> ModuleSettings.cacheKnownMineComponents(updated)
        }
    }

    private fun symbolScanStatusValues(hostContext: Context): Map<String, String> {
        val prefs = hostContext.getSharedPreferences(BiliSymbolResolver.CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        return listOf(
            ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY,
            ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT,
            ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT,
        ).mapNotNull { key ->
            prefs.getString(key, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { key to it }
        }.toMap()
    }

    private fun packageVersionOrNull(context: Context, packageName: String): VersionInfo? =
        runCatching { packageVersion(context, packageName) }.getOrNull()

    private fun packageVersion(context: Context, packageName: String): VersionInfo {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        return VersionInfo(
            packageName = packageName,
            versionName = info.versionName ?: UNKNOWN,
            versionCode = info.longVersionCodeCompat().toString(),
        )
    }

    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    private fun readRuntimeString(prefs: SharedPreferences, key: String): String {
        return prefs.getString(key, null)?.takeIf { it.isNotBlank() && it != UNKNOWN } ?: UNKNOWN
    }

    private fun formatRuntimeUpdateTime(raw: String): String {
        val millis = raw.toLongOrNull() ?: return raw
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun resolveHostSourceKind(context: Context, prefs: SharedPreferences, hostPackageName: String): String {
        val recorded = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_SOURCE_KIND)
        if (recorded != UNKNOWN) return recorded
        return packageSourceKindOrNull(context, hostPackageName) ?: UNKNOWN
    }

    private fun packageSourceKindOrNull(context: Context, packageName: String): String? {
        if (packageName.isBlank() || packageName == UNKNOWN) return null
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return null
        return classifyHostSource(info.sourceDir)
    }

    fun classifyRuntimeKind(frameworkName: String): String {
        val lowerName = frameworkName.lowercase(Locale.ROOT)
        return when {
            lowerName.contains("npatch") -> "npatch"
            lowerName.contains("lsposed") -> "lsposed"
            lowerName.contains("lspatch") -> "lspatch"
            lowerName.contains("edxposed") -> "edxposed"
            lowerName.contains("sandhook") -> "sandhook"
            lowerName.contains("yahfa") -> "yahfa"
            lowerName.contains("vector") -> "vector"
            lowerName.contains("xposed") -> "xposed"
            frameworkName.isBlank() || frameworkName == UNKNOWN -> UNKNOWN
            else -> "xposed-compatible"
        }
    }

    private fun classifyHostSource(sourceDir: String?): String {
        val value = sourceDir?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.isBlank() -> UNKNOWN
            value.contains("/cache/npatch/origin/") -> "npatch-origin"
            value.contains("/cache/lspatch/origin/") -> "lspatch-origin"
            value.endsWith(".apk") -> "apk"
            else -> "other"
        }
    }

    private fun classifyPatchMode(context: Context, hostSourceKind: String): String {
        val source = context.applicationInfo?.sourceDir
            ?.replace('\\', '/')
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return when {
            hostSourceKind == "npatch-origin" || source.contains("/npatch/") || source.contains("-npatched.apk") -> "integrated"
            hostSourceKind == "lspatch-origin" || source.contains("/lspatch/") || source.contains("-lspatched.apk") -> "integrated"
            source.contains("/cache/") -> UNKNOWN
            else -> "none"
        }
    }

    private val OBSERVED_STRING_SET_KEYS = setOf(
        ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS,
        ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS,
        ModuleSettings.KEY_KNOWN_HOME_COMPONENTS,
        ModuleSettings.KEY_KNOWN_MINE_COMPONENTS,
    )

    private data class VersionInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: String,
    ) {
        val displayName: String
            get() = if (versionCode == UNKNOWN) versionName else "$versionName ($versionCode)"
    }
}
