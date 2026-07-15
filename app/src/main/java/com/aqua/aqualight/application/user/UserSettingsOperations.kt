package com.aqua.aqualight.application.user

import kotlinx.coroutines.flow.Flow

/** Application-facing settings and reminder boundary. */
interface UserSettingsOperations {
    val themeMode: Flow<String>
    val languageCode: Flow<String>
    val notificationsEnabled: Flow<Boolean>
    val autoUpdateEnabled: Flow<Boolean>
    val usageAnalytics: Flow<UsageAnalyticsSnapshot>

    suspend fun updateThemeMode(mode: String)

    suspend fun updateLanguage(code: String)

    suspend fun updateNotificationsEnabled(enabled: Boolean)

    suspend fun updateAutoUpdateEnabled(enabled: Boolean)

    suspend fun reschedulePendingCareTaskReminders()

    suspend fun cancelPendingCareTaskReminders()

    companion object {
        const val DEFAULT_LANGUAGE_CODE = "en"
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
