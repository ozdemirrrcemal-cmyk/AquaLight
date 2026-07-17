package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.application.notifications.NotificationScheduler
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.care.model.CareTaskStatus
import com.aqua.aqualight.data.care.reminder.CareReminderDeliveryWorker
import com.aqua.aqualight.data.care.reminder.CareReminderReconcileWorker
import com.aqua.aqualight.data.care.reminder.CareTaskReminderScheduler
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

/** Single owner-explicit authority for care-reminder alarms and durable work. */
class DefaultNotificationScheduler(
    context: Context,
    private val preferences: OwnerNotificationPreferences =
        OwnerNotificationPreferences.create(context),
    private val careTasks: CareTaskDataStoreManager =
        CareTaskDataStoreManager.create(context),
    private val tanks: AquariumTankDataStoreManager =
        AquariumTankDataStoreManager(context),
    private val renderer: AndroidNotificationRenderer =
        AndroidNotificationRenderer(context)
) : NotificationScheduler {

    private val appContext = context.applicationContext

    override suspend fun scheduleCareTask(ownerUid: String, taskId: Long) {
        val owner = requireOwnerUid(ownerUid)
        require(taskId > 0L) { "taskId must be positive" }

        val task = UserDataScope.withOwnerUid(owner) {
            careTasks.taskFlow(taskId).firstOrNull()
        }
        if (task == null || task.ownerUid != owner) {
            cancelCareTask(owner, taskId)
            return
        }

        scheduleOrCancel(owner, task.copy(ownerUid = owner))
    }

    override suspend fun cancelCareTask(ownerUid: String, taskId: Long) {
        val owner = requireOwnerUid(ownerUid)
        require(taskId > 0L) { "taskId must be positive" }
        CareTaskReminderScheduler.cancel(appContext, taskId, owner)
        renderer.cancelCareReminder(owner, taskId)
    }

    override suspend fun reconcileOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        val ownerTasks = UserDataScope.withOwnerUid(owner) {
            careTasks.tasksFlow.first()
        }

        ownerTasks.forEach { task ->
            scheduleOrCancel(owner, task.copy(ownerUid = owner))
        }
    }

    override suspend fun cancelOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        CareReminderReconcileWorker.cancel(appContext, owner)
        CareReminderDeliveryWorker.cancelOwner(appContext, owner)

        val ownerTasks = UserDataScope.withOwnerUid(owner) {
            careTasks.tasksFlow.first()
        }
        ownerTasks.forEach { task ->
            CareTaskReminderScheduler.cancel(appContext, task.id, owner)
        }
        renderer.cancelOwner(owner)
    }

    private suspend fun scheduleOrCancel(ownerUid: String, task: CareTask) {
        val eligible = isEligible(ownerUid, task)
        if (eligible) {
            CareTaskReminderScheduler.schedule(appContext, task)
        } else {
            CareTaskReminderScheduler.cancel(appContext, task.id, ownerUid)
            renderer.cancelCareReminder(ownerUid, task.id)
        }
    }

    private suspend fun isEligible(ownerUid: String, task: CareTask): Boolean {
        if (!preferences.isEnabled(ownerUid)) return false
        if (task.status != CareTaskStatus.PENDING || !task.reminderEnabled) return false

        return tanks.tanksSnapshotForOwner(ownerUid)
            .firstOrNull { tank -> tank.id == task.tankId }
            ?.careRemindersEnabled == true
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) { "ownerUid must not be blank" }
        }
    }
}
