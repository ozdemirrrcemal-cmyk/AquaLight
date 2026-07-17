package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.data.care.reminder.CareReminderCoordinator
import com.aqua.aqualight.data.notifications.ActiveNotificationPreferenceProjection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Existing DataStore/settings behavior wired behind the application contract. */
class DefaultUserSettingsOperations(
    context: Context,
    private val preferences: UserPreferencesManager,
    private val startupAppearanceCache: StartupAppearanceCache
) : UserSettingsOperations {

    private val appContext = context.applicationContext
    private val reminderCoordinator = CareReminderCoordinator.create(appContext)
    private val activeNotificationProjection =
        ActiveNotificationPreferenceProjection.create(appContext)

    override val themeMode: Flow<String> = preferences.themeMode
    override val languageCode: Flow<String> = preferences.languageCode
    override val notificationsEnabled: Flow<Boolean> = flow {
        val ownerUid = UserDataScope.requireCurrentUid()
        emitAll(reminderCoordinator.preferenceFlow(ownerUid))
    }
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

    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        val ownerUid = UserDataScope.requireCurrentUid()
        reminderCoordinator.setPreference(
            ownerUid = ownerUid,
            enabled = enabled
        )
        activeNotificationProjection.publishForActiveOwner(
            ownerUid = ownerUid,
            enabled = enabled
        )
    }

    override suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        preferences.updateAutoUpdateEnabled(enabled)
    }

    override suspend fun reschedulePendingCareTaskReminders() {
        reminderCoordinator.reconcileOwner(
            UserDataScope.requireCurrentUid()
        )
    }

    override suspend fun cancelPendingCareTaskReminders() {
        reminderCoordinator.cancelOwner(
            UserDataScope.requireCurrentUid()
        )
    }
}
