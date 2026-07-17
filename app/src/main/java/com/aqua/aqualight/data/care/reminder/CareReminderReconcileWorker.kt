package com.aqua.aqualight.data.care.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.user.UserDataScope

/** Durable owner-scoped reconciliation after boot, package update, or session start. */
class CareReminderReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ownerUid = UserDataScope.normalizeOwnerUid(
            inputData.getString(KEY_OWNER_UID)
        )
        if (ownerUid.isBlank()) {
            return Result.success()
        }

        val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(
            applicationContext
        )
        if (ownerProvider.currentOwnerUid() != ownerUid) {
            return Result.success()
        }

        val coordinator = CareReminderCoordinator.create(applicationContext)
        return try {
            coordinator.reconcileOwner(ownerUid)

            // A fast account switch can happen while DataStore is read. Remove
            // anything just reconciled for an owner that is no longer active.
            if (ownerProvider.currentOwnerUid() != ownerUid) {
                coordinator.cancelOwner(ownerUid)
            }

            Result.success()
        } catch (exception: Exception) {
            exception.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        internal const val KEY_OWNER_UID = "owner_uid"
        private const val WORK_NAME_PREFIX = "care_reminder_reconcile_owner_"

        fun enqueue(
            context: Context,
            ownerUid: String
        ) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) {
                return
            }

            val request = OneTimeWorkRequestBuilder<CareReminderReconcileWorker>()
                .setInputData(
                    workDataOf(KEY_OWNER_UID to owner)
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    workName(owner),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }

        fun cancel(
            context: Context,
            ownerUid: String?
        ) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) {
                return
            }

            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(workName(owner))
        }

        internal fun workName(ownerUid: String): String {
            return WORK_NAME_PREFIX + UserDataScope.normalizeOwnerUid(ownerUid)
        }
    }
}
