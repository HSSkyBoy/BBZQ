package io.github.bbzq

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModulePreferenceSyncPolicyTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            pending.clear()
            storage.clear()
            return this
        }

        override fun commit(): Boolean {
            pending.forEach { (k, v) ->
                if (v == null) storage.remove(k) else storage[k] = v
            }
            pending.clear()
            return true
        }

        override fun apply() {
            commit()
        }
    }

    private class FakeSharedPreferences(
        val storage: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            storage[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = storage[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = storage[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    @Test
    fun testLocalUserSettingsOverwriteRemoteWhenChangedFromTrueToFalse() {
        val localPrefs = FakeSharedPreferences(mutableMapOf(
            ModuleSettings.KEY_STORY_VIDEO_AS_DETAIL_ENABLED to false
        ))
        val remotePrefs = FakeSharedPreferences(mutableMapOf(
            ModuleSettings.KEY_STORY_VIDEO_AS_DETAIL_ENABLED to true
        ))

        val changed = ModuleRemotePreferences.syncPreferences(localPrefs, remotePrefs)

        assertTrue("应当检测到变更并同步", changed)
        assertFalse("remotePrefs 中的开关应当被正确覆写为 false", remotePrefs.getBoolean(ModuleSettings.KEY_STORY_VIDEO_AS_DETAIL_ENABLED, true))
    }

    @Test
    fun testHostManagedKeysSyncFromRemoteToLocal() {
        val localPrefs = FakeSharedPreferences(mutableMapOf())
        val remotePrefs = FakeSharedPreferences(mutableMapOf(
            "runtime_xposed_framework_name" to "LSPosed",
            "symbol_scan_status_summary" to "ok",
            "known_video_detail_relate_types" to setOf("AV", "BANGUMI")
        ))

        val changed = ModuleRemotePreferences.syncPreferences(localPrefs, remotePrefs)

        assertTrue(changed)
        assertEquals("LSPosed", localPrefs.getString("runtime_xposed_framework_name", null))
        assertEquals("ok", localPrefs.getString("symbol_scan_status_summary", null))
    }

    @Test
    fun testExistingRemoteKeysPreservedWhenNotModifiedLocally() {
        val localPrefs = FakeSharedPreferences(mutableMapOf(
            "setting_a" to true
        ))
        val remotePrefs = FakeSharedPreferences(mutableMapOf(
            "setting_b" to "preserved"
        ))

        ModuleRemotePreferences.syncPreferences(localPrefs, remotePrefs)

        assertEquals("preserved", remotePrefs.getString("setting_b", null))
        assertTrue(remotePrefs.getBoolean("setting_a", false))
    }
}
