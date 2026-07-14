package com.aqua.aqualight.data.user

import android.content.Context

/**
 * Tiny synchronous mirror for values required before the first Activity is
 * inflated. The encrypted Proto DataStore remains authoritative; this cache only
 * prevents Application.onCreate() from blocking on disk/decryption.
 */
class StartupAppearanceCache private constructor(
    context: Context
) {

    data class Appearance(
        val themeMode: String,
        val languageCode: String
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun read(): Appearance {
        return Appearance(
            themeMode = preferences.getString(
                KEY_THEME_MODE,
                UserPreferencesManager.DEFAULT_THEME_MODE
            ).orEmpty().ifBlank {
                UserPreferencesManager.DEFAULT_THEME_MODE
            },
            languageCode = preferences.getString(
                KEY_LANGUAGE_CODE,
                UserPreferencesManager.DEFAULT_LANGUAGE_CODE
            ).orEmpty().ifBlank {
                UserPreferencesManager.DEFAULT_LANGUAGE_CODE
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
                languageCode.ifBlank {
                    UserPreferencesManager.DEFAULT_LANGUAGE_CODE
                }
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
                languageCode.ifBlank {
                    UserPreferencesManager.DEFAULT_LANGUAGE_CODE
                }
            )
            .apply()
    }

    companion object {
        private const val FILE_NAME = "startup_appearance"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_CODE = "language_code"

        fun create(
            context: Context
        ): StartupAppearanceCache {
            return StartupAppearanceCache(context.applicationContext)
        }
    }
}
