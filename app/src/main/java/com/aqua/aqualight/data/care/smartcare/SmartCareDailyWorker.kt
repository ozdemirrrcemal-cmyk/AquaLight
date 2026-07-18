package com.aqua.aqualight.data.care.smartcare

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.data.user.UserDataScope
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SmartCareDailyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val scheduledOwnerUid = inputData.getString(KEY_OWNER_UID)
            .orEmpty()
            .trim()

        if (scheduledOwnerUid.isBlank()) return Result.success()

        val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext)
        if (ownerProvider.currentOwnerUid() != scheduledOwnerUid) {
            return Result.success()
        }

        return try {
            val tankDataStoreManager = AquariumTankDataStoreManager(applicationContext)
            val careTaskDataStoreManager = CareTaskDataStoreManager.create(
                applicationContext
            )

            // Explicit owner snapshot: no DevicesRepositoryProvider, discovery or
            // WebSocket runtime is opened by this worker.
            val tanks = tankDataStoreManager.tanksSnapshotForOwner(scheduledOwnerUid)
            val generatedTasks = SmartCareTaskGenerator.generateForTanks(
                context = applicationContext,
                tanks = tanks
            )

            // Re-check the owner immediately before the write so a fast account
            // switch cannot write generated tasks into the next user's session.
            if (ownerProvider.currentOwnerUid() != scheduledOwnerUid) {
                return Result.success()
            }

            UserDataScope.withOwnerUid(scheduledOwnerUid) {
                careTaskDataStoreManager.syncAutomaticTasks(
                    generatedTasks = generatedTasks
                )
            }

            if (ownerProvider.currentOwnerUid() != scheduledOwnerUid) {
                return Result.success()
            }

            NotificationPlatform.get(applicationContext)
                .preferenceUseCase
                .reconcileOwner(scheduledOwnerUid)

            Result.success()
        } catch (exception: Exception) {
            exception.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val LEGACY_WORK_NAME = "smart_care_daily_worker"
        private const val WORK_NAME_PREFIX = "smart_care_daily_worker_owner_"
        internal const val KEY_OWNER_UID = "owner_uid"

        fun schedule(context: Context, ownerUid: String) {
            val normalizedOwnerUid = ownerUid.trim()
            if (normalizedOwnerUid.isBlank()) return

            val request = PeriodicWorkRequestBuilder<SmartCareDailyWorker>(
                1,
                TimeUnit.DAYS
            )
                .setInputData(workDataOf(KEY_OWNER_UID to normalizedOwnerUid))
                .setInitialDelay(
                    calculateInitialDelayMillis(),
                    TimeUnit.MILLISECONDS
                )
                .build()

            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            workManager.enqueueUniquePeriodicWork(
                workName(normalizedOwnerUid),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context, ownerUid: String?) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)

            ownerUid
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { normalizedOwnerUid ->
                    workManager.cancelUniqueWork(workName(normalizedOwnerUid))
                }
        }

        internal fun workName(ownerUid: String): String {
            return WORK_NAME_PREFIX + ownerUid
        }

        internal fun calculateInitialDelayMillis(
            now: Calendar = Calendar.getInstance()
        ): Long {
            val nextRun = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 6)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }

            return nextRun.timeInMillis - now.timeInMillis
        }
    }
}
