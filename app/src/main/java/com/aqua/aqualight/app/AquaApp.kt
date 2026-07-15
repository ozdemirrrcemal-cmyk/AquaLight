package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aqua.aqualight.base.theme.AppThemeController
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.StartupAppearanceCache
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AquaApp : Application() {

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    override fun onCreate() {
        super.onCreate()

        LocalDataRecoveryTracker.initialize(this)

        val appearanceCache = StartupAppearanceCache.create(this)
        val cachedAppearance = appearanceCache.read()

        // The first frame uses a tiny SharedPreferences mirror. Encrypted Proto
        // DataStore is reconciled asynchronously and never blocks app startup.
        applyTheme(cachedAppearance.themeMode)
        applyLanguage(cachedAppearance.languageCode)

        val userPrefs = UserPreferencesManager.create(this)
        applicationScope.launch {
            val preferences = userPrefs.userPrefsFlow.first()
            val resolvedThemeMode = preferences.themeMode.ifBlank {
                UserPreferencesManager.DEFAULT_THEME_MODE
            }
            val resolvedLanguageCode = preferences.languageCode.ifBlank {
                UserPreferencesManager.DEFAULT_LANGUAGE_CODE
            }

            appearanceCache.write(
                themeMode = resolvedThemeMode,
                languageCode = resolvedLanguageCode
            )

            if (
                cachedAppearance.themeMode != resolvedThemeMode ||
                cachedAppearance.languageCode != resolvedLanguageCode
            ) {
                withContext(Dispatchers.Main.immediate) {
                    applyTheme(resolvedThemeMode)
                    applyLanguage(resolvedLanguageCode)
                }
            }
        }

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
