package io.github.bbzq

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import io.github.bbzq.feats.symbol.BiliSymbolResolver
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.service.XposedService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

object RuntimeEnvironmentInfo {
    private const val UNKNOWN = "unknown"
    const val EXTRA_RUNTIME_VALUES = "io.github.bbzq.extra.RUNTIME_VALUES"

    private const val BLOCKED_FRAMEWORK_NAME = "LSPosed"
    private val BLOCKED_FRAMEWORK_VERSION_CODES = setOf("7777")

    private val targetPackages = listOf(
        "tv.danmaku.bili",
        "com.bilibili.app.blue",
        "top.nkbe.npatch",
    )

    private class ObservedSetSpec(
        val key: String,
        val getter: (SharedPreferences) -> Set<String>,
        val cacher: (Set<String>) -> Unit,
    )

    private val observedSetSpecs = listOf(
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_BOTTOM_BAR_ITEMS,
            ModuleSettings::getKnownBottomBarItems,
            ModuleSettings::cacheKnownBottomBarItems,
        ),
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_HOME_RECOMMEND_TABS,
            ModuleSettings::getKnownHomeRecommendTabs,
            ModuleSettings::cacheKnownHomeRecommendTabs,
        ),
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_HOME_COMPONENTS,
            ModuleSettings::getKnownHomeComponents,
            ModuleSettings::cacheKnownHomeComponents,
        ),
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_MINE_COMPONENTS,
            ModuleSettings::getKnownMineComponents,
            ModuleSettings::cacheKnownMineComponents,
        ),
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_VIDEO_DETAIL_RELATE_TYPES,
            ModuleSettings::getKnownVideoDetailRelateTypes,
            ModuleSettings::cacheKnownVideoDetailRelateTypes,
        ),
        ObservedSetSpec(
            ModuleSettings.KEY_KNOWN_COMPONENT_POOLS,
            ModuleSettings::getKnownComponentPools,
            ModuleSettings::cacheKnownComponentPools,
        ),
    ).associateBy { it.key }

    fun versionSummary(context: Context, prefs: SharedPreferences): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        return "APP ${host.displayName}\nModule ${module.displayName}"
    }

    fun runtimeEnvironmentJson(context: Context, prefs: SharedPreferences): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        return buildRuntimeEnvironmentJson(context, prefs, host, module)
    }

    private fun buildRuntimeEnvironmentJson(
        context: Context,
        prefs: SharedPreferences,
        host: VersionInfo,
        module: VersionInfo,
        hostSourceKind: String = resolveHostSourceKind(context, prefs, host.packageName),
    ): String {
        val serviceInfo = ModuleRemotePreferences.getFrameworkInfo()

        val xposedApiVersion = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_API_VERSION) {
            serviceInfo?.apiVersion
        }
        val xposedFrameworkName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_NAME) {
            serviceInfo?.frameworkName
        }
        val xposedFrameworkVersion = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION) {
            serviceInfo?.frameworkVersion
        }
        val xposedFrameworkVersionCode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE) {
            serviceInfo?.frameworkVersionCode
        }
        val xposedFrameworkProperties = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES) {
            serviceInfo?.frameworkProperties
        }
        val runtimeKind = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_KIND) {
            classifyRuntimeKind(xposedFrameworkName)
        }
        val patchMode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_PATCH_MODE) {
            classifyPatchMode(context, hostSourceKind)
        }
        val processName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_PROCESS_NAME) {
            context.applicationInfo?.processName ?: context.packageName
        }

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

    fun devicesText(
        context: Context,
        prefs: SharedPreferences,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String {
        val host = resolveHostVersion(context, prefs)
        val module = moduleVersion(context)
        val hostSourceKind = resolveHostSourceKind(context, prefs, host.packageName)

        return buildString {
            appendLine("exportedAt=${formatTimestamp(exportedAtMillis)}")
            appendLine("deviceBrand=${Build.BRAND}")
            appendLine("deviceManufacturer=${Build.MANUFACTURER}")
            appendLine("deviceModel=${Build.MODEL}")
            appendLine("deviceName=${Build.DEVICE}")
            appendLine("productName=${Build.PRODUCT}")
            appendLine("androidRelease=${Build.VERSION.RELEASE}")
            appendLine("androidSdk=${Build.VERSION.SDK_INT}")
            appendLine("securityPatch=${Build.VERSION.SECURITY_PATCH ?: UNKNOWN}")
            appendLine("hostPackage=${host.packageName}")
            appendLine("hostVersion=${host.displayName}")
            appendLine("hostSourceKind=$hostSourceKind")
            appendLine("modulePackage=${context.packageName}")
            appendLine("moduleVersion=${module.displayName}")
            appendLine("runtimeSnapshot=")
            appendLine(buildRuntimeEnvironmentJson(context, prefs, host, module, hostSourceKind))
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
            putObservedStringSet(editor, prefs, key, items)
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
    ): Bundle = Bundle().apply {
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

    fun applyRuntimeSnapshotFromIntent(intent: Intent?, prefs: SharedPreferences) {
        val values = intent?.getBundleExtra(EXTRA_RUNTIME_VALUES) ?: return
        val editor = prefs.edit()
        var changed = false

        values.keySet().forEach { key ->
            if (key in observedSetSpecs) {
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
        if (changed) editor.apply()
    }

    private fun resolveHostVersion(context: Context, prefs: SharedPreferences): VersionInfo {
        val recordedPackage = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_PACKAGE)
        if (recordedPackage != UNKNOWN) {
            return VersionInfo(
                packageName = recordedPackage,
                versionName = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_VERSION_NAME),
                versionCode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_VERSION_CODE),
            )
        }
        return targetPackages
            .firstNotNullOfOrNull { packageVersionOrNull(context, it) }
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
        return buildMap {
            put(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE, host.packageName)
            put(ModuleSettings.KEY_RUNTIME_HOST_VERSION_NAME, host.versionName)
            put(ModuleSettings.KEY_RUNTIME_HOST_VERSION_CODE, host.versionCode)
            put(ModuleSettings.KEY_RUNTIME_HOST_SOURCE_KIND, sourceKind)
            put(ModuleSettings.KEY_RUNTIME_XPOSED_API_VERSION, xposedApiVersion.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_NAME, xposedFrameworkName.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION, xposedFrameworkVersion.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE, xposedFrameworkVersionCode.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES, xposedFrameworkProperties.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_KIND, classifyRuntimeKind(xposedFrameworkName))
            put(ModuleSettings.KEY_RUNTIME_PATCH_MODE, classifyPatchMode(hostContext, sourceKind))
            put(ModuleSettings.KEY_RUNTIME_PROCESS_NAME, processName.ifBlank { UNKNOWN })
            put(ModuleSettings.KEY_RUNTIME_LAST_UPDATE_TIME, System.currentTimeMillis().toString())
            putAll(symbolScanStatusValues(hostContext))
        }
    }

    private fun runtimeObservedStringSets(prefs: SharedPreferences?): Map<String, Set<String>> {
        if (prefs == null) return emptyMap()
        return buildMap {
            observedSetSpecs.values.forEach { spec ->
                val items = spec.getter(prefs)
                if (items.isNotEmpty()) {
                    put(spec.key, items)
                }
            }
        }
    }

    private fun putObservedStringSet(
        editor: SharedPreferences.Editor,
        prefs: SharedPreferences,
        key: String,
        items: Set<String>,
    ) {
        if (items.isEmpty()) return
        val localItems = prefs.getStringSet(key, emptySet()).orEmpty()
        val updated = if (key == ModuleSettings.KEY_KNOWN_COMPONENT_POOLS) {
            mergeComponentPools(localItems, items)
        } else {
            if (items.size < localItems.size) localItems else items
        }
        editor.putStringSet(key, updated.toMutableSet())
        observedSetSpecs[key]?.cacher?.invoke(updated)
    }

    private fun mergeComponentPools(local: Set<String>, incoming: Set<String>): Set<String> {
        val counts = linkedMapOf<String, Int>()
        (local.asSequence() + incoming.asSequence())
            .mapNotNull(ModuleSettings::decodeComponentPool)
            .forEach { (name, count) -> counts[name] = maxOf(counts[name] ?: 0, count) }
        return counts.entries
            .sortedBy { it.key }
            .mapTo(linkedSetOf()) { ModuleSettings.encodeComponentPool(it.key, it.value) }
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
        val pm = context.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        return VersionInfo(
            packageName = packageName,
            versionName = info.versionName ?: UNKNOWN,
            versionCode = info.longVersionCode.toString(),
        )
    }

    private inline fun readRuntimeString(
        prefs: SharedPreferences,
        key: String,
        fallback: () -> String? = { null },
    ): String {
        val stored = prefs.getString(key, null)?.takeIf { it.isNotBlank() && it != UNKNOWN }
        return stored ?: fallback()?.takeIf { it.isNotBlank() && it != UNKNOWN } ?: UNKNOWN
    }

    private fun formatRuntimeUpdateTime(raw: String): String {
        val millis = raw.toLongOrNull() ?: return raw
        return formatTimestamp(millis)
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun resolveHostSourceKind(context: Context, prefs: SharedPreferences, hostPackageName: String): String =
        readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_HOST_SOURCE_KIND) {
            packageSourceKindOrNull(context, hostPackageName)
        }

    private fun packageSourceKindOrNull(context: Context, packageName: String): String? {
        if (packageName.isBlank() || packageName == UNKNOWN) return null
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        }.getOrNull() ?: return null
        return classifyHostSource(info.sourceDir)
    }

    enum class FrameworkGate { ALLOWED, NEEDS_ROOT, BLOCKED }

    fun frameworkGate(prefs: SharedPreferences, context: Context, intent: Intent?): FrameworkGate {
        val runtimeValues = intent?.getBundleExtra(EXTRA_RUNTIME_VALUES)
        val runtimeKind = runtimeValues?.getString(ModuleSettings.KEY_RUNTIME_KIND)
        if (runtimeKind == "lspatch") {
            return lspatchFrameworkGate(context, runtimeValues)
        }

        val serviceInfo = ModuleRemotePreferences.getFrameworkInfo()
        val name = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_NAME) {
            serviceInfo?.frameworkName
        }
        val versionCode = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_VERSION_CODE) {
            serviceInfo?.frameworkVersionCode
        }
        if (name == BLOCKED_FRAMEWORK_NAME && versionCode in BLOCKED_FRAMEWORK_VERSION_CODES) {
            return FrameworkGate.BLOCKED
        }
        val kind = readRuntimeString(prefs, ModuleSettings.KEY_RUNTIME_KIND) {
            classifyRuntimeKind(name)
        }
        return when (kind) {
            "lsposed" -> FrameworkGate.NEEDS_ROOT
            "npatch", "vector", UNKNOWN -> FrameworkGate.ALLOWED
            // LSPatch 1.0 和 1.1 的宿主侧可能报告为 Vector。
            "lspatch" -> if (runtimeKind == "vector") {
                lspatchFrameworkGate(context, runtimeValues)
            } else {
                FrameworkGate.BLOCKED
            }
            else -> FrameworkGate.BLOCKED
        }
    }

    private fun lspatchFrameworkGate(context: Context, runtimeValues: Bundle): FrameworkGate {
        val hostPackage = runtimeValues.getString(ModuleSettings.KEY_RUNTIME_HOST_PACKAGE)
        val metadata = hostPackage?.let { packageName ->
            runCatching {
                val pm = context.packageManager
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                }
                info.metaData?.getString("lspatch")
            }.getOrNull()
        }
        return if (isLspatchManagerSupported(
                runtimeValues.getString(ModuleSettings.KEY_RUNTIME_XPOSED_API_VERSION),
                runtimeValues.getString(ModuleSettings.KEY_RUNTIME_XPOSED_FRAMEWORK_PROPERTIES),
                metadata,
            )
        ) FrameworkGate.ALLOWED else FrameworkGate.BLOCKED
    }

    internal fun isLspatchManagerSupported(apiVersion: String?, properties: String?, metadata: String?): Boolean {
        val api = apiVersion?.toIntOrNull() ?: return false
        val flags = properties?.toLongOrNull() ?: return false
        if (api < XposedService.API_102 || (flags and XposedService.PROP_CAP_REMOTE) == 0L) return false
        val encoded = metadata ?: return false
        return runCatching {
            val config = String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
            JSONObject(config).opt("useManager") == true
        }.getOrDefault(false)
    }

    fun classifyRuntimeKind(frameworkName: String): String {
        if (frameworkName.isBlank() || frameworkName == UNKNOWN) return UNKNOWN
        val lowerName = frameworkName.lowercase(Locale.ROOT)
        return when {
            "npatch" in lowerName -> "npatch"
            "lsposed" in lowerName -> "lsposed"
            "lspatch" in lowerName -> "lspatch"
            "edxposed" in lowerName -> "edxposed"
            "sandhook" in lowerName -> "sandhook"
            "yahfa" in lowerName -> "yahfa"
            "vector" in lowerName -> "vector"
            "xposed" in lowerName -> "xposed"
            else -> "xposed-compatible"
        }
    }

    private fun classifyHostSource(sourceDir: String?): String {
        val value = sourceDir?.replace('\\', '/')?.lowercase(Locale.ROOT).orEmpty()
        return when {
            value.isBlank() -> UNKNOWN
            "/cache/npatch/origin/" in value -> "npatch-origin"
            "/cache/lspatch/origin/" in value -> "lspatch-origin"
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
            hostSourceKind == "npatch-origin" || "/npatch/" in source || "-npatched.apk" in source -> "integrated"
            hostSourceKind == "lspatch-origin" || "/lspatch/" in source || "-lspatched.apk" in source -> "integrated"
            "/cache/" in source -> UNKNOWN
            else -> "none"
        }
    }

    private data class VersionInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: String,
    ) {
        val displayName: String
            get() = if (versionCode == UNKNOWN) versionName else "$versionName ($versionCode)"
    }
}
