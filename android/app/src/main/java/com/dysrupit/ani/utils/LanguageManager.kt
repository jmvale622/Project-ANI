package com.dysrupit.ani.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {

    private const val PREF_NAME = "ani_language_pref"
    private const val KEY_LANG = "selected_language"

    fun getLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANG, "en") ?: "en"
    }

    fun getLanguageDisplayName(context: Context): String {
        return when (getLanguage(context)) {
            "tl", "fil" -> "Tagalog"
            else -> "English"
        }
    }

    fun setLanguage(context: Context, langCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, langCode).apply()

        // 1. Update AndroidX AppCompat application locales
        val appLocales = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocales)

        // 2. Fallback update resources
        updateResources(context, langCode)
    }

    fun applyLanguage(context: Context): Context {
        val langCode = getLanguage(context)
        return updateResources(context, langCode)
    }

    fun updateResources(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }
}
