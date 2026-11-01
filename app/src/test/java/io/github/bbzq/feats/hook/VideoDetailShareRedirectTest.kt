package io.github.bbzq.feats.hook

import android.content.SharedPreferences
import io.github.bbzq.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDetailShareRedirectTest {

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
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testPreferenceDefaultsAndMutation() {
        val prefs = FakePreferences()
        assertFalse(ModuleSettings.isShareRedirectToOverflowEnabled(prefs))

        ModuleSettings.setShareRedirectToOverflowEnabled(prefs, true)
        assertTrue(ModuleSettings.isShareRedirectToOverflowEnabled(prefs))

        ModuleSettings.setShareRedirectToOverflowEnabled(prefs, false)
        assertFalse(ModuleSettings.isShareRedirectToOverflowEnabled(prefs))
    }

    @Test
    fun testExportableSpecIncludesShareRedirect() {
        val spec = ModuleSettings.exportableSwitchSpecs.firstOrNull {
            it.key == ModuleSettings.KEY_SHARE_REDIRECT_TO_OVERFLOW_ENABLED
        }
        assertTrue("Exportable spec must contain KEY_SHARE_REDIRECT_TO_OVERFLOW_ENABLED", spec != null)
        val prefs = FakePreferences()
        assertEquals(false, spec?.read?.invoke(prefs))
        ModuleSettings.setShareRedirectToOverflowEnabled(prefs, true)
        assertEquals(true, spec?.read?.invoke(prefs))
    }

    @Test
    fun testConstants() {
        assertEquals("playback_share_redirect_to_overflow_enabled", ModuleSettings.KEY_SHARE_REDIRECT_TO_OVERFLOW_ENABLED)
        assertEquals(0x7f09186b, VideoDetailShareRedirectHook.FALLBACK_FRAME_SHARE_ID)
        assertEquals(0x7f094067, VideoDetailShareRedirectHook.FALLBACK_SHARE_ICON_ID)
        assertEquals(0x7f09472c, VideoDetailShareRedirectHook.FALLBACK_OVERFLOW_ID)
        assertEquals("com.bilibili.ship.theseus.united.widget.DetailsShareAnimViewV2", VideoDetailShareRedirectHook.TARGET_SHARE_ANIM_VIEW_CLASS)
    }
}
