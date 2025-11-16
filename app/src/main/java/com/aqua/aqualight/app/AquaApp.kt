package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.aqua.aqualight.data.UserPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AquaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ✅ Uygulama açılır açılmaz DataStore'dan tema modunu oku
        // ve global olarak uygula
        val userPrefs = UserPreferencesManager.create(this)

        val mode = runBlocking {
            // themeMode Flow<String> -> "light" / "dark" / "system"
            userPrefs.themeMode.first()
        }

        applyTheme(mode)
    }

    private fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}