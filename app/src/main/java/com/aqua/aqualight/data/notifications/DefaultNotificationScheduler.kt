package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.application.notifications.NotificationRenderer
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
    private val preferences: OwnerNotificationPreferences,
    private val renderer: NotificationRenderer
) : NotificationScheduler {

    private val appContext = context.applicationContext
    private val careTasks by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CareTaskDataStoreManager.create(appContext)
    }
    private val tanks by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AquariumTankDataStoreManager(appContext)
    }

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
    }

    private suspend fun scheduleOrCancel(ownerUid: String, task: CareTask) {
        if (isEligible(ownerUid, task)) {
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
