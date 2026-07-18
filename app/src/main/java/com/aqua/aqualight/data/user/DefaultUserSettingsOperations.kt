package com.aqua.aqualight.data.user

import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.application.user.UserSettingsOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Existing non-notification settings behavior wired behind the application contract. */
class DefaultUserSettingsOperations(
    private val preferences: UserPreferencesManager,
    private val startupAppearanceCache: StartupAppearanceCache
) : UserSettingsOperations {

    override val themeMode: Flow<String> = preferences.themeMode
    override val languageCode: Flow<String> = preferences.languageCode
    override val autoUpdateEnabled: Flow<Boolean> = preferences.autoUpdateEnabled
    override val usageAnalytics: Flow<UsageAnalyticsSnapshot> =
        preferences.usageAnalyticsFlow.map { usage ->
            UsageAnalyticsSnapshot(
                weeklyAutomationCount = usage.weeklyAutomationCount,
                weeklyAlertCount = usage.weeklyAlertCount,
                todayAutomationCount = usage.todayAutomationCount,
                todayManualActionCount = usage.todayManualActionCount,
                lastEventTimeMillis = usage.lastEventTimeMillis,
                lastEventDescription = usage.lastEventDescription
            )
        }

    override suspend fun updateThemeMode(mode: String) {
        preferences.updateThemeMode(mode)
        startupAppearanceCache.writeThemeMode(mode)
    }

    override suspend fun updateLanguage(code: String) {
        preferences.updateLanguage(code)
        startupAppearanceCache.writeLanguageCode(code)
    }

    override suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        preferences.updateAutoUpdateEnabled(enabled)
    }
}
