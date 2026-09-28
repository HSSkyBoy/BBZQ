package io.github.bbzq

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

        val result = fetchRemotePreferences()
        synchronized(cacheLock) {
            if (!result.isNullOrEmpty() || localCache.isEmpty()) {
                localCache = if (localCache.isEmpty()) result.orEmpty() else (localCache + result.orEmpty())
            }
            lastLoadTime = now
        }
    }

    private fun fetchRemotePreferences(): Map<String, Any>? {
        val remotePrefs = resolveRemotePreferences() ?: run {
            lastStatus = "remote unavailable"
            return null
        }
        return runCatching {
            val all = remotePrefs.all
            lastStatus = "remote ok"
            all.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        }.getOrElse {
            lastStatus = "remote ${it.javaClass.simpleName}: ${it.message}"
            null
        }
    }

    override fun getAll(): MutableMap<String, *> {
        ensureLoaded()
        return synchronized(cacheLock) { localCache.toMutableMap() }
    }

    override fun getString(key: String?, defValue: String?): String? {
        ensureLoaded()
        return synchronized(cacheLock) { localCache[key] as? String } ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        ensureLoaded()
        val cached = synchronized(cacheLock) { localCache[key] }
        return when (cached) {
            is Set<*> -> safeStringSet(cached)
            is List<*> -> safeStringSet(cached)
            else -> defValues
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        ensureLoaded()
        return (synchronized(cacheLock) { localCache[key] } as? Number)?.toInt() ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        ensureLoaded()
        return when (val value = synchronized(cacheLock) { localCache[key] }) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        ensureLoaded()
        return when (val value = synchronized(cacheLock) { localCache[key] }) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        ensureLoaded()
        return (synchronized(cacheLock) { localCache[key] } as? Boolean) ?: defValue
    }

    override fun contains(key: String?): Boolean {
        ensureLoaded()
        return synchronized(cacheLock) { localCache.containsKey(key) }
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private fun resolveRemotePreferences(): SharedPreferences? {
        cachedRemotePrefs.get()?.let { return it }
        val xposed = cachedXposed ?: return null
        return runCatching {
            xposed.getRemotePreferences(ModuleSettings.PREFS_NAME)
        }.getOrNull()?.also {
            cachedRemotePrefs = WeakReference(it)
        }
    }

    private fun applyRemoteOperations(operations: List<PreferenceOperation>): Boolean {
        if (operations.isEmpty()) return true
        val remotePrefs = resolveRemotePreferences() ?: run {
            lastStatus = "remote unavailable"
            return true
        }
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
                    // Read-only remote preferences (e.g. injected host process under NPatch / LibXposed).
                    // In-memory update still succeeds.
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
        private const val CACHE_EXPIRATION = 5000L
        private var cachedXposed: XposedInterface? = null
        private var cachedRemotePrefs = WeakReference<SharedPreferences>(null)

        @Volatile var lastStatus: String = "not called"
            private set

        fun attach(@Suppress("UNUSED_PARAMETER") context: Context, xposed: XposedInterface? = null) {
            if (xposed != null) cachedXposed = xposed
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
