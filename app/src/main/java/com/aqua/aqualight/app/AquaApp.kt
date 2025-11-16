package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aqua.aqualight.data.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AquaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val userPrefs = UserPreferencesManager.create(this)

        // Uygulama açılır açılmaz DataStore’dan oku
        val (themeMode, languageCode) = runBlocking {
            val mode = userPrefs.themeMode.first()        // "light" / "dark" / "system"
            val lang = userPrefs.languageCode.first()     // "tr" / "en" / "de" / ...
            mode to lang
        }

        applyTheme(themeMode)
        applyLanguage(languageCode)
    }

    private fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun applyLanguage(code: String) {
        // Boşsa default dil (ör: "en" ya da istersen "tr")
        val safeCode = code.ifBlank { "en" }

        // Gerekirse burada map’leyebilirsin, şimdilik direkt languageTag kullanıyoruz
        val localeList = LocaleListCompat.forLanguageTags(safeCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}