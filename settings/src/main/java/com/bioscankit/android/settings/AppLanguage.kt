package com.bioscankit.android.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** Shared language selection helpers for BioScanKit Android apps. */
object BioScanAppLanguage {
    const val STORAGE_KEY = "app_language"

    val availableIdentifiers: List<String> = listOf(
        "en", "zh-Hans", "zh-CN", "de", "es", "fr", "it", "ja", "nl",
    )

    private val nativeNames = mapOf(
        "de" to "Deutsch",
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "it" to "Italiano",
        "ja" to "日本語",
        "nl" to "Nederlands",
        "zh-CN" to "简体中文",
        "zh-Hans" to "简体中文",
    )

    fun selectedIdentifier(context: Context): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) return appLocales.toLanguageTags().substringBefore(',')
        return preferences(context).getString(STORAGE_KEY, "").orEmpty()
            .takeIf(availableIdentifiers::contains).orEmpty()
    }

    fun systemIdentifier(): String {
        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault().get(0)
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault()
        }
        return when (systemLocale.language) {
            "zh" -> "zh-Hans"
            else -> availableIdentifiers.firstOrNull {
                it == systemLocale.language || it.startsWith("${systemLocale.language}-")
            } ?: "en"
        }
    }

    fun displayName(identifier: String): String =
        nativeNames[identifier] ?: Locale.forLanguageTag(identifier).displayName.ifBlank { identifier }

    fun select(context: Context, identifier: String) {
        val normalized = identifier.takeIf(availableIdentifiers::contains).orEmpty()
        preferences(context).edit().putString(STORAGE_KEY, normalized).apply()
        val locales = normalized.takeIf(String::isNotEmpty)?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun wrap(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        }
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("bioscan.settings", Context.MODE_PRIVATE)
}
