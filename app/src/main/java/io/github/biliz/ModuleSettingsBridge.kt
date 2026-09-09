package io.github.biliz

import android.content.Context
import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference

class ModuleSettingsBridge private constructor() : SharedPreferences {
    private val cacheLock = Any()
    private var localCache: Map<String, Any> = emptyMap()
    private var lastLoadTime = 0L

    private fun ensureLoaded() {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (lastLoadTime > 0L && now - lastLoadTime < CACHE_EXPIRATION) return
        }

        val loaded = getAllSettingsFromAllSources()
        synchronized(cacheLock) {
            if (loaded.isNotEmpty() || localCache.isEmpty()) {
                localCache = if (localCache.isEmpty()) loaded else (localCache + loaded)
            }
            lastLoadTime = now
        }
    }

    private fun getAllSettingsFromAllSources(): Map<String, Any> {
        val resultMap = mutableMapOf<String, Any>()
        var hasLoadedAny = false

        // 1. RemotePreferences (LSPosed / LibXposed)
        val remotePrefs = resolveRemotePreferences()
        if (remotePrefs != null) {
            runCatching {
                remotePrefs.all.forEach { (k, v) ->
                    if (v != null) resultMap[k] = v
                }
                hasLoadedAny = true
            }
        }

        // 2. Host local SharedPreferences (e.g. Settings changed within host app)
        val ctx = cachedContext.get()
        if (ctx != null) {
            runCatching {
                val localPrefs = ctx.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
                localPrefs.all.forEach { (k, v) ->
                    if (v != null) resultMap[k] = v
                }
                hasLoadedAny = true
            }

            // 3. Standalone BILIZ App ContentProvider (Cross-process sync for rootless NPatch environment!)
            runCatching {
                val uri = android.net.Uri.parse("content://io.github.biliz.settings.provider")
                val bundle = ctx.contentResolver?.call(uri, ModuleSettingsProvider.METHOD_GET_ALL, null, null)
                if (bundle != null && !bundle.isEmpty) {
                    val editor = ctx.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
                    for (key in bundle.keySet()) {
                        val value = bundle.get(key)
                        if (value != null) {
                            val normalized: Any = when (value) {
                                is Collection<*> -> safeStringSet(value)
                                is Array<*> -> safeStringSet(value.toList())
                                else -> value
                            }
                            resultMap[key] = normalized
                            when (normalized) {
                                is Boolean -> editor.putBoolean(key, normalized)
                                is String -> editor.putString(key, normalized)
                                is Int -> editor.putInt(key, normalized)
                                is Long -> editor.putLong(key, normalized)
                                is Float -> editor.putFloat(key, normalized)
                                is Set<*> -> editor.putStringSet(key, safeStringSet(normalized))
                            }
                        }
                    }
                    editor.apply()
                    hasLoadedAny = true
                    lastStatus = "provider ok (${bundle.size()} keys)"
                }
            }.onFailure {
                // Provider may not be installed if running without standalone manager app
            }
        }

        if (hasLoadedAny && lastStatus == "not called") {
            lastStatus = "ok (${resultMap.size} keys)"
        }
        return resultMap
    }

    override fun getAll(): MutableMap<String, *> {
        ensureLoaded()
        return synchronized(cacheLock) {
            localCache.mapValues { (_, value) ->
                if (value is Collection<*>) safeStringSet(value) else value
            }.toMutableMap()
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        ensureLoaded()
        return synchronized(cacheLock) {
            val v = localCache[key]
            (v as? String) ?: (v?.toString()) ?: defValue
        }
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        ensureLoaded()
        return synchronized(cacheLock) {
            when (val v = localCache[key]) {
                is Set<*> -> safeStringSet(v)
                is Collection<*> -> safeStringSet(v)
                is Array<*> -> safeStringSet(v.toList())
                is String -> mutableSetOf(v)
                else -> defValues
            }
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        ensureLoaded()
        return synchronized(cacheLock) {
            when (val v = localCache[key]) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: defValue
                else -> defValue
            }
        }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        ensureLoaded()
        return synchronized(cacheLock) {
            when (val v = localCache[key]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: defValue
                else -> defValue
            }
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        ensureLoaded()
        return synchronized(cacheLock) {
            when (val v = localCache[key]) {
                is Number -> v.toFloat()
                is String -> v.toFloatOrNull() ?: defValue
                else -> defValue
            }
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        ensureLoaded()
        return synchronized(cacheLock) {
            when (val v = localCache[key]) {
                is Boolean -> v
                is String -> v.toBooleanStrictOrNull() ?: defValue
                is Number -> v.toInt() != 0
                else -> defValue
            }
        }
    }

    override fun contains(key: String?): Boolean {
        ensureLoaded()
        return synchronized(cacheLock) { localCache.containsKey(key) }
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        // no-op
    }

    private fun getAllFromRemotePreferences(): Map<String, Any?> =
        getAllSettingsFromAllSources()

    private fun resolveRemotePreferences(): SharedPreferences? {
        cachedRemotePrefs.get()?.let { return it }
        val xposed = cachedXposed
        val remote = runCatching {
            xposed?.getRemotePreferences(ModuleSettings.PREFS_NAME)
        }.getOrNull()
        if (remote != null) {
            cachedRemotePrefs = WeakReference(remote)
            return remote
        }
        val fallback = cachedContext.get()?.let { ctx ->
            runCatching {
                ctx.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
        }
        if (fallback != null) {
            cachedRemotePrefs = WeakReference(fallback)
            return fallback
        }
        return null
    }

    private fun applyRemoteOperations(operations: List<PreferenceOperation>): Boolean {
        if (operations.isEmpty()) return true
        val ctx = cachedContext.get()
        if (ctx != null) {
            runCatching {
                val localEditor = ctx.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE).edit()
                operations.forEach { op ->
                    when (op) {
                        PreferenceOperation.Clear -> localEditor.clear()
                        is PreferenceOperation.Remove -> localEditor.remove(op.key)
                        is PreferenceOperation.Put -> localEditor.putValue(op.key, op.value)
                    }
                }
                localEditor.apply()
            }
            runCatching {
                val uri = android.net.Uri.parse("content://io.github.biliz.settings.provider")
                operations.forEach { op ->
                    if (op is PreferenceOperation.Put) {
                        val extras = android.os.Bundle()
                        when (val v = op.value) {
                            is Boolean -> extras.putBoolean("boolean", v)
                            is String -> extras.putString("string", v)
                            is Int -> extras.putInt("int", v)
                            is Long -> extras.putLong("long", v)
                            is Float -> extras.putFloat("float", v)
                            is Set<*> -> extras.putStringArrayList("string_set", ArrayList(v.mapNotNull { it?.toString() }))
                            is Collection<*> -> extras.putStringArrayList("string_set", ArrayList(v.mapNotNull { it?.toString() }))
                        }
                        ctx.contentResolver?.call(uri, ModuleSettingsProvider.METHOD_PUT_VALUE, op.key, extras)
                    }
                }
            }
        }

        val remotePrefs = resolveRemotePreferences() ?: return true
        return runCatching {
            val editor = remotePrefs.edit()
            operations.forEach { operation ->
                when (operation) {
                    PreferenceOperation.Clear -> editor.clear()
                    is PreferenceOperation.Remove -> editor.remove(operation.key)
                    is PreferenceOperation.Put -> editor.putValue(operation.key, operation.value)
                }
            }
            editor.commit()
        }.fold(
            onSuccess = { committed ->
                lastStatus = if (committed) "remote ok" else "remote commit failed"
                committed
            },
            onFailure = { throwable ->
                if (throwable is UnsupportedOperationException) {
                    lastStatus = "remote ok (read-only snapshot)"
                    true
                } else {
                    lastStatus = "remote write ${throwable.javaClass.simpleName}: ${throwable.message}"
                    false
                }
            },
        )
    }

    private inner class Editor : SharedPreferences.Editor {
        private val operations = mutableListOf<PreferenceOperation>()
        private val cacheUpdates = mutableListOf<(MutableMap<String, Any>) -> Unit>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            cacheUpdates += { cache ->
                if (key != null && value != null) cache[key] = value
                else if (key != null) cache.remove(key)
            }
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply {
            val safeValues = safeStringSetOrNull(values)
            if (key != null) operations += PreferenceOperation.Put(key, safeValues)
            cacheUpdates += { cache ->
                if (key != null && safeValues != null) cache[key] = safeValues
                else if (key != null) cache.remove(key)
            }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            cacheUpdates += { cache -> if (key != null) cache[key] = value }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            cacheUpdates += { cache -> if (key != null) cache[key] = value }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            cacheUpdates += { cache -> if (key != null) cache[key] = value }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            cacheUpdates += { cache -> if (key != null) cache[key] = value }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Remove(key)
            cacheUpdates += { cache -> if (key != null) cache.remove(key) }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
            operations += PreferenceOperation.Clear
        }

        override fun commit(): Boolean =
            applyInternal()

        override fun apply() {
            applyInternal()
        }

        private fun applyInternal(): Boolean {
            ensureLoaded()
            val success = applyRemoteOperations(operations)
            if (success) {
                synchronized(cacheLock) {
                    val updated = if (clearRequested) mutableMapOf() else localCache.toMutableMap()
                    cacheUpdates.forEach { it(updated) }
                    localCache = updated
                    lastLoadTime = System.currentTimeMillis()
                }
            }
            operations.clear()
            cacheUpdates.clear()
            clearRequested = false
            return success
        }
    }

    companion object {
        private const val CACHE_EXPIRATION = 1500L
        private var cachedXposed: XposedInterface? = null
        private var cachedRemotePrefs = WeakReference<SharedPreferences>(null)
        private var cachedContext = WeakReference<Context>(null)

        @Volatile var lastStatus: String = "not called"
            private set

        fun attach(context: Context, xposed: XposedInterface? = null) {
            cachedContext = WeakReference(context)
            if (xposed != null) cachedXposed = xposed
            instance.resetTransientState()
        }

        fun invalidateCache() {
            instance.resetTransientState()
        }

        val instance: ModuleSettingsBridge by lazy(LazyThreadSafetyMode.NONE) {
            ModuleSettingsBridge()
        }
    }

    private fun resetTransientState() {
        synchronized(cacheLock) {
            localCache = emptyMap()
            lastLoadTime = 0L
        }
        cachedRemotePrefs = WeakReference(null)
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
