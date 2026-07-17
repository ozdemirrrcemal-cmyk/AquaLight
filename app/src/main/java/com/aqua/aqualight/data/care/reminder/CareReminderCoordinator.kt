package com.aqua.aqualight.data.care.reminder

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.notifications.ActiveNotificationPreferenceProjection
import com.aqua.aqualight.data.notifications.NotificationChannelRegistry
import com.aqua.aqualight.data.notifications.OwnerNotificationPreferences
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Owner-explicit application boundary for notification preference and reminder alarms.
 */
class CareReminderCoordinator private constructor(
    context: Context,
    private val preferences: OwnerNotificationPreferences,
    private val activeProjection: ActiveNotificationPreferenceProjection,
    private val careTasks: CareTaskDataStoreManager,
    private val tanks: AquariumTankDataStoreManager
) {

    private val appContext = context.applicationContext

    fun preferenceFlow(ownerUid: String): Flow<Boolean> {
        return preferences.enabledFlow(requireOwnerUid(ownerUid))
    }

    suspend fun setPreference(
        ownerUid: String,
        enabled: Boolean
    ) {
        val owner = requireOwnerUid(ownerUid)
        preferences.setEnabled(
            ownerUid = owner,
            enabled = enabled
        )

        // Existing care-task write paths still consume the active-session
        // projection. Publish before reconciliation so a task created concurrently
        // with this setting change sees the same owner preference immediately.
        activeProjection.publishForActiveOwner(
            ownerUid = owner,
            enabled = enabled
        )

        if (enabled) {
            NotificationChannelRegistry.ensureChannels(appContext)
            reconcileOwner(owner)
        } else {
            cancelOwner(owner)
        }
    }

    suspend fun reconcileOwner(
        ownerUid: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val owner = requireOwnerUid(ownerUid)
        val appPreferenceEnabled = preferences.isEnabled(owner)
        val ownerTasks = UserDataScope.withOwnerUid(owner) {
            careTasks.tasksFlow.first()
        }
        val tanksById = tanks.tanksSnapshotForOwner(owner)
            .associateBy { tank -> tank.id }

        ownerTasks.forEach { task ->
            val ownerTask = task.copy(ownerUid = owner)
            val tankAllowsReminders = tanksById[ownerTask.tankId]
                ?.careRemindersEnabled == true

            if (
                appPreferenceEnabled &&
                tankAllowsReminders &&
                ownerTask.reminderEnabled
            ) {
                CareTaskReminderScheduler.schedule(
                    context = appContext,
                    task = ownerTask,
                    nowMillis = nowMillis
                )
            } else {
                CareTaskReminderScheduler.cancel(
                    context = appContext,
                    taskId = ownerTask.id,
                    ownerUid = owner
                )
            }
        }
    }

    suspend fun cancelOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        val ownerTasks = UserDataScope.withOwnerUid(owner) {
            careTasks.tasksFlow.first()
        }

        ownerTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(
                context = appContext,
                taskId = task.id,
                ownerUid = owner
            )
        }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }
    }

    companion object {
        fun create(context: Context): CareReminderCoordinator {
            val appContext = context.applicationContext
            return CareReminderCoordinator(
                context = appContext,
                preferences = OwnerNotificationPreferences.create(appContext),
                activeProjection = ActiveNotificationPreferenceProjection.create(appContext),
                careTasks = CareTaskDataStoreManager.create(appContext),
                tanks = AquariumTankDataStoreManager(appContext)
            )
        }
    }
}
