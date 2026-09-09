package io.github.biliz

import android.content.Context
import android.content.SharedPreferences

class ReadableModulePreferences(
    private val context: Context,
    private val delegate: SharedPreferences,
) : SharedPreferences by delegate {

    init {
        ModuleRemotePreferences.attach(context, delegate)
    }

    override fun edit(): SharedPreferences.Editor =
        ReadableEditor(delegate.edit())

    private inner class ReadableEditor(
        private val editor: SharedPreferences.Editor,
    ) : SharedPreferences.Editor by editor {
        private val operations = mutableListOf<PreferenceOperation>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            editor.putString(key, value)
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            val safeValues = safeStringSetOrNull(values)
            if (key != null) operations += PreferenceOperation.Put(key, safeValues)
            editor.putStringSet(key, safeValues)
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            editor.putInt(key, value)
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            editor.putLong(key, value)
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            editor.putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Put(key, value)
            editor.putBoolean(key, value)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) operations += PreferenceOperation.Remove(key)
            editor.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            operations += PreferenceOperation.Clear
            editor.clear()
            return this
        }

        override fun apply() {
            editor.apply()
            syncRemote()
        }

        override fun commit(): Boolean {
            val result = editor.commit()
            syncRemote()
            return result
        }

        private fun syncRemote() {
            ModuleRemotePreferences.applyOperations(operations)
            operations.clear()
        }
    }
}
