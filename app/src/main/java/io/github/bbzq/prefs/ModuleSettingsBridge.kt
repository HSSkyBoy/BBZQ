package io.github.bbzq.prefs

import android.content.Context
import android.content.SharedPreferences
import io.github.bbzq.ModuleSettings
import io.github.bbzq.feats.BbzqLevelGate
import io.github.libxposed.api.XposedInterface

class ModuleSettingsBridge private constructor() : SharedPreferences {
    private val cacheLock = Any()
    private var remoteSnapshot: Map<String, Any> = emptyMap()
    private val readOnlyOverrides = mutableMapOf<String, Any?>()
    private var readOnlyCleared = false
    private var localCache: Map<String, Any> = emptyMap()
    private var lastLoadTime = 0L

    private val remoteChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        synchronized(cacheLock) {
            lastLoadTime = 0L
        }
    }

    private fun ensureLoaded() {
        val now = System.currentTimeMillis()
        synchronized(cacheLock) {
            if (lastLoadTime > 0L && now - lastLoadTime < CACHE_EXPIRATION) return
        }

        val result = fetchRemotePreferences()
        synchronized(cacheLock) {
            if (result != null) {
                remoteSnapshot = result
                rebuildEffectiveCacheLocked()
            }
            lastLoadTime = now
        }
    }

    private fun rebuildEffectiveCacheLocked() {
        if (readOnlyOverrides.isEmpty() && !readOnlyCleared) {
            localCache = remoteSnapshot
            return
        }
        val merged = if (readOnlyCleared) mutableMapOf() else remoteSnapshot.toMutableMap()
        for ((key, value) in readOnlyOverrides) {
            if (value == null) {
                merged.remove(key)
            } else {
                merged[key] = value
            }
        }
        localCache = merged
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
        if (!BbzqLevelGate.isAllowed()) return mutableMapOf<String, Any>()
        ensureLoaded()
        return synchronized(cacheLock) { localCache.toMutableMap() }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (!BbzqLevelGate.isAllowed()) return defValue
        ensureLoaded()
        return synchronized(cacheLock) { localCache[key] as? String } ?: defValue
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        if (!BbzqLevelGate.isAllowed()) return defValues
        ensureLoaded()
        val cached = synchronized(cacheLock) { localCache[key] }
        return when (cached) {
            is Set<*> -> safeStringSet(cached)
            is List<*> -> safeStringSet(cached)
            else -> defValues
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (!BbzqLevelGate.isAllowed()) return defValue
        ensureLoaded()
        return (synchronized(cacheLock) { localCache[key] } as? Number)?.toInt() ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (!BbzqLevelGate.isAllowed()) return defValue
        ensureLoaded()
        return when (val value = synchronized(cacheLock) { localCache[key] }) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (!BbzqLevelGate.isAllowed()) return defValue
        ensureLoaded()
        return when (val value = synchronized(cacheLock) { localCache[key] }) {
            is Number -> value.toFloat()
            is String -> value.toFloatOrNull() ?: defValue
            else -> defValue
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (!BbzqLevelGate.isAllowed()) return defValue
        ensureLoaded()
        return (synchronized(cacheLock) { localCache[key] } as? Boolean) ?: defValue
    }

    override fun contains(key: String?): Boolean {
        if (!BbzqLevelGate.isAllowed()) return false
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
        cachedRemotePrefs?.let { return it }
        val xposed = cachedXposed ?: return null
        return runCatching {
            xposed.getRemotePreferences(ModuleSettings.PREFS_NAME)
        }.getOrNull()?.also { prefs ->
            runCatching { prefs.registerOnSharedPreferenceChangeListener(remoteChangeListener) }
            cachedRemotePrefs = prefs
        }
    }

    private enum class RemoteWriteOutcome {
        COMMITTED,
        READ_ONLY_FALLBACK,
        FAILED,
    }

    private fun applyRemoteOperations(operations: List<PreferenceOperation>): RemoteWriteOutcome {
        if (operations.isEmpty()) return RemoteWriteOutcome.COMMITTED
        val remotePrefs = resolveRemotePreferences() ?: run {
            lastStatus = "remote unavailable"
            return RemoteWriteOutcome.READ_ONLY_FALLBACK
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
                if (committed) RemoteWriteOutcome.COMMITTED else RemoteWriteOutcome.FAILED
            },
            onFailure = { throwable ->
                if (throwable is UnsupportedOperationException) {
                    // Read-only remote preferences (e.g. injected host process under NPatch / LibXposed).
                    // In-memory override layer still succeeds without masking future remote updates.
                    lastStatus = "remote ok (read-only snapshot)"
                    RemoteWriteOutcome.READ_ONLY_FALLBACK
                } else {
                    lastStatus = "remote write ${throwable.javaClass.simpleName}: ${throwable.message}"
                    RemoteWriteOutcome.FAILED
                }
            },
        )
    }

    private inner class Editor : SharedPreferences.Editor {
        private val operations = mutableListOf<PreferenceOperation>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply {
            val safeValues = safeStringSetOrNull(values)
            if (key != null) operations += PreferenceOperation.Put(key, safeValues)
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Put(key, value)
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            if (key != null) operations += PreferenceOperation.Remove(key)
        }

        override fun clear(): SharedPreferences.Editor = apply {
            operations += PreferenceOperation.Clear
        }

        override fun commit(): Boolean =
            applyInternal()

        override fun apply() {
            applyInternal()
        }

        private fun applyInternal(): Boolean {
            val batch = operations.toList()
            operations.clear()
            if (batch.isEmpty()) return true

            ensureLoaded()
            return when (applyRemoteOperations(batch)) {
                RemoteWriteOutcome.COMMITTED -> {
                    synchronized(cacheLock) {
                        val updatedRemote = remoteSnapshot.toMutableMap()
                        batch.forEach { op ->
                            when (op) {
                                PreferenceOperation.Clear -> {
                                    updatedRemote.clear()
                                    readOnlyOverrides.clear()
                                    readOnlyCleared = false
                                }
                                is PreferenceOperation.Remove -> {
                                    updatedRemote.remove(op.key)
                                    readOnlyOverrides.remove(op.key)
                                }
                                is PreferenceOperation.Put -> {
                                    if (op.value == null) updatedRemote.remove(op.key)
                                    else updatedRemote[op.key] = op.value
                                    readOnlyOverrides.remove(op.key)
                                }
                            }
                        }
                        remoteSnapshot = updatedRemote
                        rebuildEffectiveCacheLocked()
                        lastLoadTime = System.currentTimeMillis()
                    }
                    true
                }

                RemoteWriteOutcome.READ_ONLY_FALLBACK -> {
                    synchronized(cacheLock) {
                        batch.forEach { op ->
                            when (op) {
                                PreferenceOperation.Clear -> {
                                    readOnlyOverrides.clear()
                                    readOnlyCleared = true
                                }
                                is PreferenceOperation.Remove -> {
                                    readOnlyOverrides[op.key] = null
                                }
                                is PreferenceOperation.Put -> {
                                    readOnlyOverrides[op.key] = op.value
                                }
                            }
                        }
                        rebuildEffectiveCacheLocked()
                        lastLoadTime = System.currentTimeMillis()
                    }
                    true
                }

                RemoteWriteOutcome.FAILED -> false
            }
        }
    }

    companion object {
        private const val CACHE_EXPIRATION = 5000L
        @Volatile private var cachedXposed: XposedInterface? = null
        @Volatile private var cachedRemotePrefs: SharedPreferences? = null

        @Volatile var lastStatus: String = "not called"
            private set

        fun attach(@Suppress("UNUSED_PARAMETER") context: Context, xposed: XposedInterface? = null) {
            if (xposed != null) cachedXposed = xposed
            instance.resetTransientState()
        }

        val instance: ModuleSettingsBridge = ModuleSettingsBridge()
    }

    private fun resetTransientState() {
        val previousRemote = cachedRemotePrefs
        cachedRemotePrefs = null
        if (previousRemote != null) {
            runCatching { previousRemote.unregisterOnSharedPreferenceChangeListener(remoteChangeListener) }
        }
        synchronized(cacheLock) {
            remoteSnapshot = emptyMap()
            readOnlyOverrides.clear()
            readOnlyCleared = false
            localCache = emptyMap()
            lastLoadTime = 0L
        }
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
