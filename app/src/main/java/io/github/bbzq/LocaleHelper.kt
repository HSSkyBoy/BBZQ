package io.github.bbzq

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    fun getLocale(languageCode: String): Locale? = when (languageCode) {
        ModuleSettings.LANGUAGE_ZH_CN -> Locale.SIMPLIFIED_CHINESE
        ModuleSettings.LANGUAGE_ZH_TW -> Locale.TRADITIONAL_CHINESE
        ModuleSettings.LANGUAGE_EN -> Locale.ENGLISH
        else -> null
    }

    fun wrapContext(context: Context, languageCode: String): Context {
        val locale = getLocale(languageCode) ?: return context
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }

    fun getLanguageDisplayName(context: Context, languageCode: String): String = when (languageCode) {
        ModuleSettings.LANGUAGE_ZH_CN -> context.getString(R.string.settings_language_zh_cn)
        ModuleSettings.LANGUAGE_ZH_TW -> context.getString(R.string.settings_language_zh_tw)
        ModuleSettings.LANGUAGE_EN -> context.getString(R.string.settings_language_en)
        else -> context.getString(R.string.settings_language_auto)
    }
}
