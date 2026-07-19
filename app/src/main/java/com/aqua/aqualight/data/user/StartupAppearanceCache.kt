package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.i18n.SupportedLocaleRegistry

/**
 * Tiny synchronous mirror for values required before the first Activity is inflated.
 *
 * Theme state mirrors encrypted Proto DataStore. Language state mirrors the effective
 * Android/AppCompat application locale and bootstraps that locale before Activity creation on
 * Android 12 and lower. The cache is never an independent user-visible language source.
 */
class StartupAppearanceCache private constructor(
    context: Context
) {

    data class Appearance(
        val themeMode: String,
        val languageCode: String
    )

    // During Application.attachBaseContext() the framework ContextImpl is usable,
    // while context.applicationContext can still be null on older Android releases.
    private val preferences = context.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun read(): Appearance {
        val defaultLanguage = SupportedLocaleRegistry.deviceDefault()
        return Appearance(
            themeMode = preferences.getString(
                KEY_THEME_MODE,
                UserPreferencesManager.DEFAULT_THEME_MODE
            ).orEmpty().ifBlank {
                UserPreferencesManager.DEFAULT_THEME_MODE
            },
            languageCode = preferences.getString(
                KEY_LANGUAGE_CODE,
                defaultLanguage
            ).orEmpty().ifBlank {
                defaultLanguage
            }
        )
    }

    fun write(
        themeMode: String,
        languageCode: String
    ) {
        preferences.edit()
            .putString(
                KEY_THEME_MODE,
                themeMode.ifBlank {
                    UserPreferencesManager.DEFAULT_THEME_MODE
                }
            )
            .putString(
                KEY_LANGUAGE_CODE,
                requireSupportedLanguage(languageCode)
            )
            .apply()
    }

    fun writeThemeMode(
        themeMode: String
    ) {
        preferences.edit()
            .putString(
                KEY_THEME_MODE,
                themeMode.ifBlank {
                    UserPreferencesManager.DEFAULT_THEME_MODE
                }
            )
            .apply()
    }

    fun writeLanguageCode(
        languageCode: String
    ) {
        preferences.edit()
            .putString(
                KEY_LANGUAGE_CODE,
                requireSupportedLanguage(languageCode)
            )
            .apply()
    }

    private fun requireSupportedLanguage(languageCode: String): String {
        return requireNotNull(
            SupportedLocaleRegistry.supportedCanonicalOrNull(languageCode)
        ) {
            "Startup language must be an explicit supported locale."
        }
    }

    companion object {
        private const val FILE_NAME = "startup_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_CODE = "language_code"

        fun create(
            context: Context
        ): StartupAppearanceCache {
            return StartupAppearanceCache(
                context.applicationContext ?: context
            )
        }
    }
}
