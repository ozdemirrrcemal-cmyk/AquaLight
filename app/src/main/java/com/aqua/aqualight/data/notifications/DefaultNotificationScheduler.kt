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
    private val scheduleLedger by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CareReminderScheduleLedger.create(appContext)
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
        scheduleLedger.markCancelled(owner, taskId)
        renderer.cancelCareReminder(owner, taskId)
    }

    override suspend fun reconcileOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        val ownerTasks = UserDataScope.withOwnerUid(owner) {
            careTasks.tasksFlow.first()
        }
        val currentTaskIds = ownerTasks.asSequence()
            .map(CareTask::id)
            .filter { taskId -> taskId > 0L }
            .toSet()

        // AlarmManager cannot enumerate app alarms. The durable ledger lets recovery
        // cancel alarms for tasks removed by a crash, corruption repair or rollback.
        val staleTaskIds = scheduleLedger.taskIds(owner) - currentTaskIds
        staleTaskIds.forEach { staleTaskId ->
            CareTaskReminderScheduler.cancel(appContext, staleTaskId, owner)
            scheduleLedger.markCancelled(owner, staleTaskId)
            renderer.cancelCareReminder(owner, staleTaskId)
        }

        ownerTasks.forEach { task ->
            scheduleOrCancel(owner, task.copy(ownerUid = owner))
        }
    }

    override suspend fun cancelOwner(ownerUid: String) {
        val owner = requireOwnerUid(ownerUid)
        CareReminderReconcileWorker.cancel(appContext, owner)
        CareReminderDeliveryWorker.cancelOwner(appContext, owner)

        val ledgerTaskIds = scheduleLedger.taskIds(owner)
        val taskSnapshot = runCatching {
            UserDataScope.withOwnerUid(owner) {
                careTasks.tasksFlow.first()
            }
        }
        val persistedTaskIds = taskSnapshot.getOrDefault(emptyList())
            .asSequence()
            .map(CareTask::id)
            .filter { taskId -> taskId > 0L }
            .toSet()

        (ledgerTaskIds + persistedTaskIds).forEach { taskId ->
            CareTaskReminderScheduler.cancel(appContext, taskId, owner)
        }
        scheduleLedger.clearOwner(owner)

        // Preserve cleanup observability without abandoning ledger-known alarms.
        taskSnapshot.exceptionOrNull()?.let { error -> throw error }
    }

    private suspend fun scheduleOrCancel(ownerUid: String, task: CareTask) {
        if (!isEligible(ownerUid, task)) {
            cancelCareTask(ownerUid, task.id)
            return
        }

        val scheduled = CareTaskReminderScheduler.schedule(appContext, task)
        if (scheduled) {
            scheduleLedger.markScheduled(ownerUid, task.id)
        } else {
            scheduleLedger.markCancelled(ownerUid, task.id)
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
