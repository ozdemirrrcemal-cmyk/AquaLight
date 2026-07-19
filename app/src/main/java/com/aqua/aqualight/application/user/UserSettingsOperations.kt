package com.aqua.aqualight.application.user

import com.aqua.aqualight.i18n.SupportedLocaleRegistry
import kotlinx.coroutines.flow.Flow

/** Application-facing non-notification settings boundary. */
interface UserSettingsOperations {
    val themeMode: Flow<String>
    val languageCode: Flow<String>
    val autoUpdateEnabled: Flow<Boolean>
    val usageAnalytics: Flow<UsageAnalyticsSnapshot>

    suspend fun updateThemeMode(mode: String)
    suspend fun updateLanguage(code: String)
    suspend fun updateAutoUpdateEnabled(enabled: Boolean)

    companion object {
        val DEFAULT_LANGUAGE_CODE: String
            get() = SupportedLocaleRegistry.deviceDefault()
    }
}

data class UsageAnalyticsSnapshot(
    val weeklyAutomationCount: Int,
    val weeklyAlertCount: Int,
    val todayAutomationCount: Int,
    val todayManualActionCount: Int,
    val lastEventTimeMillis: Long,
    val lastEventDescription: String
)
