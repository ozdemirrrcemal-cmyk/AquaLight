package com.aqua.aqualight.data.care.reminder

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.application.notifications.CareReminderKind
import com.aqua.aqualight.application.notifications.CareReminderNotification
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.care.model.CareTask
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

/** Durable owner-scoped delivery for one alarm occurrence. */
class CareReminderDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        val ownerUid = UserDataScope.normalizeOwnerUid(inputData.getString(KEY_OWNER_UID))
        val occurrence = inputData.getString(KEY_OCCURRENCE)?.let { raw ->
            runCatching { CareReminderOccurrence.valueOf(raw) }.getOrNull()
        }

        if (taskId <= 0L || ownerUid.isBlank() || occurrence == null) {
            return Result.success()
        }

        return try {
            deliver(taskId, ownerUid, occurrence)
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception.printStackTrace()
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    private suspend fun deliver(
        taskId: Long,
        ownerUid: String,
        occurrence: CareReminderOccurrence
    ) {
        val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext)
        if (ownerProvider.currentOwnerUid() != ownerUid) return

        val platform = NotificationPlatform.get(applicationContext)
        val notificationState = platform.preferenceUseCase.snapshot(ownerUid)
        if (!notificationState.ownerPreferenceEnabled ||
            !notificationState.readiness(NotificationCategory.CARE_REMINDERS).canDeliver
        ) {
            return
        }

        val careTaskManager = CareTaskDataStoreManager.create(applicationContext)
        val tankManager = AquariumTankDataStoreManager(applicationContext)
        val task = UserDataScope.withOwnerUid(ownerUid) {
            careTaskManager.taskFlow(taskId).firstOrNull()
        } ?: return
        if (task.ownerUid != ownerUid) return

        val ownerTask = task.copy(ownerUid = ownerUid)
        val nowMillis = System.currentTimeMillis()
        if (!matchesScheduledOccurrence(ownerTask, occurrence, nowMillis)) {
            platform.scheduler.scheduleCareTask(ownerUid, ownerTask.id)
            return
        }

        val tanks = UserDataScope.withOwnerUid(ownerUid) {
            tankManager.tanksFlow.firstOrNull().orEmpty()
        }
        val tank = tanks.firstOrNull { candidate -> candidate.id == ownerTask.tankId }

        if (!CareReminderDeliveryPolicy.shouldDeliver(ownerTask, tank)) {
            platform.preferenceUseCase.cancelCareTask(ownerUid, ownerTask.id)
            return
        }

        // Account switching can occur while persistent state is read.
        if (ownerProvider.currentOwnerUid() != ownerUid) return

        val notificationText = CareReminderTextResolver(applicationContext).resolve(
            task = ownerTask,
            tank = tank
        )
        platform.dispatchUseCase.dispatchCareReminder(
            CareReminderNotification(
                ownerUid = ownerUid,
                taskId = ownerTask.id,
                kind = CareReminderKind.valueOf(ownerTask.type.name),
                title = notificationText.title,
                message = notificationText.message
            )
        )
        platform.scheduler.scheduleCareTask(ownerUid, ownerTask.id)
    }

    private fun matchesScheduledOccurrence(
        task: CareTask,
        occurrence: CareReminderOccurrence,
        nowMillis: Long
    ): Boolean {
        return when (occurrence) {
            CareReminderOccurrence.DUE -> task.dueAtMillis <= nowMillis
            CareReminderOccurrence.MISSED -> {
                if (!task.missedReminderEnabled) {
                    false
                } else {
                    val missedAt = runCatching {
                        Math.addExact(
                            task.dueAtMillis,
                            TimeUnit.DAYS.toMillis(task.missedReminderDays.toLong())
                        )
                    }.getOrNull()
                    missedAt != null && missedAt <= nowMillis
                }
            }
        }
    }

    companion object {
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_OWNER_UID = "owner_uid"
        private const val KEY_OCCURRENCE = "occurrence"
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_SECONDS = 30L

        internal fun enqueue(
            context: Context,
            taskId: Long,
            ownerUid: String,
            occurrence: CareReminderOccurrence
        ) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (taskId <= 0L || owner.isBlank()) return

            val requestBuilder = OneTimeWorkRequestBuilder<CareReminderDeliveryWorker>()
                .setInputData(
                    workDataOf(
                        KEY_TASK_ID to taskId,
                        KEY_OWNER_UID to owner,
                        KEY_OCCURRENCE to occurrence.name
                    )
                )
                .addTag(CareReminderIdentity.ownerWorkTag(owner))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )

            // Android 12+ can execute this short user-visible delivery as an expedited
            // job. Earlier APIs keep the normal Worker path to avoid a foreground-service
            // requirement solely for posting a reminder.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBuilder.setExpedited(
                    OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST
                )
            }

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                CareReminderIdentity.deliveryWorkName(owner, taskId, occurrence),
                ExistingWorkPolicy.REPLACE,
                requestBuilder.build()
            )
        }

        internal fun cancelOwner(context: Context, ownerUid: String?) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) return
            WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag(CareReminderIdentity.ownerWorkTag(owner))
        }
    }
}
