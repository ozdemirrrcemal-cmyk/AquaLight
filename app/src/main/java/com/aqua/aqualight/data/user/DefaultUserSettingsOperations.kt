package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.application.user.UsageAnalyticsSnapshot
import com.aqua.aqualight.application.user.UserSettingsOperations
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Existing DataStore/reminder behavior wired behind the application contract. */
class DefaultUserSettingsOperations(
    context: Context,
    private val preferences: UserPreferencesManager,
    private val startupAppearanceCache: StartupAppearanceCache
) : UserSettingsOperations {

    private val appContext = context.applicationContext
    private val careTasks by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CareTaskDataStoreManager.create(appContext)
    }

    override val themeMode: Flow<String> = preferences.themeMode
    override val languageCode: Flow<String> = preferences.languageCode
    override val notificationsEnabled: Flow<Boolean> = preferences.notificationsEnabled
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
        preferences.updateNotificationsEnabled(enabled)
    }

    override suspend fun updateAutoUpdateEnabled(enabled: Boolean) {
        preferences.updateAutoUpdateEnabled(enabled)
    }

    override suspend fun reschedulePendingCareTaskReminders() {
        val now = System.currentTimeMillis()
        careTasks.pendingTasksFlow
            .first()
            .filter { task -> task.dueAtMillis > now }
            .forEach { task ->
                CareTaskReminderScheduler.schedule(
                    context = appContext,
                    task = task
                )
            }
    }

    override suspend fun cancelPendingCareTaskReminders() {
        careTasks.pendingTasksFlow
            .first()
            .forEach { task ->
                CareTaskReminderScheduler.cancel(
                    context = appContext,
                    taskId = task.id,
                    ownerUid = task.ownerUid
                )
            }
    }
}
