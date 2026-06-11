package com.aqua.aqualight.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.aqua.aqualight.data.user.UserPreferencesManager
import com.aqua.aqualight.data.devices.light.runtime.LightDeviceDataCenter
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.care.smartcare.SmartCareDailyWorker
import com.aqua.aqualight.utils.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AquaApp : Application() {

    override fun onCreate() {
        super.onCreate()

        LightDeviceDataCenter.configure(this)
        DevicePresenceMonitor.start(this)

        val userPrefs = UserPreferencesManager.create(this)

        val (themeMode, languageCode) = runBlocking {
            val mode = userPrefs.themeMode.first()
            val lang = userPrefs.languageCode.first()
            mode to lang
        }

        applyTheme(themeMode)
        applyLanguage(languageCode)

        // 🔔 Notification channel
        NotificationHelper.createNotificationChannel(this)

        // 🧠 Smart care daily background sync
        SmartCareDailyWorker.schedule(this)
    }

    private fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun applyLanguage(code: String) {
        val safeCode = code.ifBlank { UserPreferencesManager.DEFAULT_LANGUAGE_CODE }
        val localeList = LocaleListCompat.forLanguageTags(safeCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}