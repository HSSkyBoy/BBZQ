package io.github.bbzq.feats.hook

import android.content.SharedPreferences
import io.github.bbzq.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFontScaleTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this

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

    private class FakePreferences(
        private val storage: MutableMap<String, Any?> = mutableMapOf(),
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (storage[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (storage[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (storage[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (storage[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(storage)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    @Test
    fun testDefaultSettings() {
        val prefs = FakePreferences()
        assertFalse(ModuleSettings.isCustomFontScaleEnabled(prefs))
        assertEquals(100, ModuleSettings.getCustomFontScalePercent(prefs))
        assertEquals(1.0f, ModuleSettings.getCustomFontScaleFloat(prefs), 0.001f)
    }

    @Test
    fun testCustomFontScaleValuesAndBounds() {
        val prefs = FakePreferences()
        prefs.edit()
            .putBoolean(ModuleSettings.KEY_CUSTOM_FONT_SCALE_ENABLED, true)
            .putInt(ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT, 120)
            .apply()

        assertTrue(ModuleSettings.isCustomFontScaleEnabled(prefs))
        assertEquals(120, ModuleSettings.getCustomFontScalePercent(prefs))
        assertEquals(1.20f, ModuleSettings.getCustomFontScaleFloat(prefs), 0.001f)

        // Below minimum (70)
        prefs.edit().putInt(ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT, 50).apply()
        assertEquals(70, ModuleSettings.getCustomFontScalePercent(prefs))
        assertEquals(0.70f, ModuleSettings.getCustomFontScaleFloat(prefs), 0.001f)

        // Above maximum (150)
        prefs.edit().putInt(ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT, 200).apply()
        assertEquals(150, ModuleSettings.getCustomFontScalePercent(prefs))
        assertEquals(1.50f, ModuleSettings.getCustomFontScaleFloat(prefs), 0.001f)
    }

    @Test
    fun testExportableSpecsIncluded() {
        val switchSpec = ModuleSettings.exportableSwitchSpecs.firstOrNull { it.key == ModuleSettings.KEY_CUSTOM_FONT_SCALE_ENABLED }
        assertTrue("Switch spec must include KEY_CUSTOM_FONT_SCALE_ENABLED", switchSpec != null)

        val valueSpec = ModuleSettings.exportableManualSpecs.firstOrNull { it.key == ModuleSettings.KEY_CUSTOM_FONT_SCALE_PERCENT }
        assertTrue("Value spec must include KEY_CUSTOM_FONT_SCALE_PERCENT", valueSpec != null)
    }
}
