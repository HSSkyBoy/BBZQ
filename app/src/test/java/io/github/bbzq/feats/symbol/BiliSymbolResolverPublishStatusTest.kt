package io.github.bbzq.feats.symbol

import android.content.SharedPreferences
import io.github.bbzq.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliSymbolResolverPublishStatusTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = null
            return this
        }
        override fun clear(): SharedPreferences.Editor = this

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

    private class FakeSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    @Test
    fun testPublishStatusDualWritesToBothPreferences() {
        val cachePrefs = FakeSharedPreferences()
        val settingsPrefs = FakeSharedPreferences()

        val symbols = BiliHookSymbols(
            fingerprint = "test_fp_123",
            hookPoints = emptyList(),
            scanErrors = emptyList(),
        )

        val logs = mutableListOf<String>()
        BiliSymbolResolver.publishStatusToPrefs(
            prefsList = listOf(cachePrefs, settingsPrefs),
            symbols = symbols,
            log = { msg, _ -> logs.add(msg) },
        )

        assertTrue("不应有失败日志", logs.isEmpty())

        // 验证 cachePrefs
        val cacheSummary = cachePrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY, null)
        val cacheReport = cachePrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, null)
        val cacheUpdatedAt = cachePrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT, null)

        assertNotNull("cachePrefs 应有 summary", cacheSummary)
        assertNotNull("cachePrefs 应有 report", cacheReport)
        assertNotNull("cachePrefs 应有 updated_at", cacheUpdatedAt)

        // 验证 settingsPrefs
        val settingsSummary = settingsPrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_SUMMARY, null)
        val settingsReport = settingsPrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_REPORT, null)
        val settingsUpdatedAt = settingsPrefs.getString(ModuleSettings.KEY_SYMBOL_SCAN_STATUS_UPDATED_AT, null)

        assertNotNull("settingsPrefs 应有 summary", settingsSummary)
        assertNotNull("settingsPrefs 应有 report", settingsReport)
        assertNotNull("settingsPrefs 应有 updated_at", settingsUpdatedAt)

        // 验证两者完全一致
        assertEquals(cacheSummary, settingsSummary)
        assertEquals(cacheReport, settingsReport)
        assertEquals(cacheUpdatedAt, settingsUpdatedAt)

        assertEquals("当前扫描结果：未发现缺失方法", cacheSummary)
        assertTrue(cacheReport!!.contains("缓存指纹：test_fp_123"))
        assertTrue(cacheReport.contains("未发现异常 HookPoint。"))
    }
}
