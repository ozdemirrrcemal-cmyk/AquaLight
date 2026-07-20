package com.aqua.aqualight.application.user

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
}

data class UsageAnalyticsSnapshot(
    val weeklyAutomationCount: Int,
    val weeklyAlertCount: Int,
    val todayAutomationCount: Int,
    val todayManualActionCount: Int,
    val lastEventTimeMillis: Long,
    val lastEventDescription: String
)
