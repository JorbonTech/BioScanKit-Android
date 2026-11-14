package com.bioscankit.android.settings

import android.app.LocaleManager
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

    /**
     * 与 iOS `BioScanAppLanguage` 一致：简体中文只保留 `zh-Hans`。
     * 历史上曾同时存在 `zh-CN`，会在语言列表里出现两个“简体中文”，已移除；
     * `selectedIdentifier` 仍会把残留的 `zh-CN` 归一到 `zh-Hans`。
     */
    /**
     * 与 iOS `Locale.availableIdentifiers` 过滤后按标识符升序一致：
     * de → en → es → fr → it → ja → nl → zh-Hans。
     */
    val availableIdentifiers: List<String> = listOf(
        "de", "en", "es", "fr", "it", "ja", "nl", "zh-Hans",
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
        val currentTags = currentLanguageTags(context)
        if (currentTags.isNotEmpty()) {
            return normalizeIdentifier(currentTags.substringBefore(','))
        }
        return normalizeIdentifier(
            preferences(context).getString(STORAGE_KEY, "").orEmpty(),
        )
    }

    /**
     * 当前生效的应用语言。
     *
     * API 33+ 以系统 `LocaleManager` 为准（系统设置里的“应用语言”也会改到它）；
     * 更早版本退回 AppCompat 的进程内状态。
     */
    private fun currentLanguageTags(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)
                ?.applicationLocales
                ?: return ""
            return locales.toLanguageTags()
        }
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (appLocales.isEmpty) "" else appLocales.toLanguageTags()
    }

    /** 把历史遗留的 `zh-CN` 归一到 `zh-Hans`；其余不在支持列表内的值返回空（跟随系统）。 */
    fun normalizeIdentifier(identifier: String): String {
        if (identifier.isBlank()) return ""
        val canonical = if (identifier == "zh-CN") "zh-Hans" else identifier
        return canonical.takeIf(availableIdentifiers::contains).orEmpty()
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

    /**
     * 切换应用语言。
     *
     * API 33+ 直接写系统的 `LocaleManager`：Activity 声明了 `configChanges="locale|layoutDirection"`，
     * 因此只收到一次 `onConfigurationChanged`，界面就地刷新，不需要重启。
     * 更早版本仍走 AppCompat，需要重建 Activity（见 [requiresRestart]）。
     */
    fun select(context: Context, identifier: String) {
        val normalized = identifier.takeIf(availableIdentifiers::contains).orEmpty()
        preferences(context).edit().putString(STORAGE_KEY, normalized).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
            val locales = if (normalized.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
            localeManager.setApplicationLocales(locales)
        } else {
            val locales = normalized.takeIf(String::isNotEmpty)?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    /** 是否需要重建 Activity 才能看到新语言。API 33+ 可以就地生效。 */
    fun requiresRestart(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    /**
     * 冷启动时套用已保存的语言。
     *
     * API 33+ 系统会自行把应用语言应用到资源，这里原样返回；
     * 更早版本没有 `LocaleManager`，用保存的标识包一层配置。
     */
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val stored = preferences(context).getString(STORAGE_KEY, "").orEmpty()
        if (stored.isBlank()) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(stored))
        }
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("bioscan.settings", Context.MODE_PRIVATE)
}
