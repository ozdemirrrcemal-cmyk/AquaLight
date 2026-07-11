package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AquaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val userPrefs = UserPreferencesManager.create(this)

        val (themeMode, languageCode) = runBlocking {
            val mode = userPrefs.themeMode.first()
            val lang = userPrefs.languageCode.first()
            mode to lang
        }

        applyTheme(themeMode)
        applyLanguage(languageCode)

        // Runtime token providers are installed only inside owner-bound device repositories.
        NotificationHelper.createNotificationChannel(this)
    }

    private fun applyTheme(mode: String) {
        AppThemeController.apply(
            context = this,
            mode = mode
        )
    }

    private fun applyLanguage(code: String) {
        val safeCode = code.ifBlank { UserPreferencesManager.DEFAULT_LANGUAGE_CODE }
        val localeList = LocaleListCompat.forLanguageTags(safeCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
