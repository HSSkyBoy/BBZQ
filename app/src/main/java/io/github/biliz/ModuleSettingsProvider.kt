package io.github.biliz

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Public ContentProvider exported by the standalone BILIZ manager app.
 * Enables the patched Bilibili host application to read user preferences
 * across Android application sandbox boundaries without root privileges.
 */
class ModuleSettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        val prefs = ctx.getSharedPreferences(ModuleSettings.PREFS_NAME, Context.MODE_PRIVATE)

        return when (method) {
            METHOD_GET_ALL -> {
                val bundle = Bundle()
                prefs.all.forEach { (key, value) ->
                    putValueToBundle(bundle, key, value)
                }
                Log.i(TAG, "getAll called from callerUid=${android.os.Binder.getCallingUid()}, returned ${bundle.size()} entries")
                bundle
            }
            METHOD_GET_STRING -> {
                val key = arg ?: return null
                val bundle = Bundle()
                bundle.putString("value", prefs.getString(key, extras?.getString("defValue")))
                bundle
            }
            METHOD_GET_BOOLEAN -> {
                val key = arg ?: return null
                val bundle = Bundle()
                bundle.putBoolean("value", prefs.getBoolean(key, extras?.getBoolean("defValue", false) ?: false))
                bundle
            }
            METHOD_PUT_VALUE -> {
                if (arg != null && extras != null) {
                    val editor = prefs.edit()
                    if (extras.containsKey("boolean")) {
                        editor.putBoolean(arg, extras.getBoolean("boolean"))
                    } else if (extras.containsKey("string")) {
                        editor.putString(arg, extras.getString("string"))
                    } else if (extras.containsKey("int")) {
                        editor.putInt(arg, extras.getInt("int"))
                    } else if (extras.containsKey("long")) {
                        editor.putLong(arg, extras.getLong("long"))
                    } else if (extras.containsKey("float")) {
                        editor.putFloat(arg, extras.getFloat("float"))
                    } else if (extras.containsKey("string_set")) {
                        val list = extras.getStringArrayList("string_set")
                        if (list != null) {
                            editor.putStringSet(arg, list.toSet())
                        }
                    }
                    editor.apply()
                }
                Bundle().apply { putBoolean("success", true) }
            }
            else -> null
        }
    }

    private fun putValueToBundle(bundle: Bundle, key: String, value: Any?) {
        when (value) {
            is Boolean -> bundle.putBoolean(key, value)
            is String -> bundle.putString(key, value)
            is Int -> bundle.putInt(key, value)
            is Long -> bundle.putLong(key, value)
            is Float -> bundle.putFloat(key, value)
            is Set<*> -> bundle.putStringArrayList(key, ArrayList(value.mapNotNull { it?.toString() }))
            is List<*> -> bundle.putStringArrayList(key, ArrayList(value.mapNotNull { it?.toString() }))
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val TAG = "BILIZ-Provider"
        const val AUTHORITY = "io.github.biliz.settings.provider"
        const val METHOD_GET_ALL = "getAll"
        const val METHOD_GET_STRING = "getString"
        const val METHOD_GET_BOOLEAN = "getBoolean"
        const val METHOD_PUT_VALUE = "putValue"
    }
}
