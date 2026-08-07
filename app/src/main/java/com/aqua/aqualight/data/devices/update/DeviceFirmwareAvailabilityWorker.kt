@file:Suppress("TooManyFunctions")

package com.aqua.aqualight.data.devices.update

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareBackgroundAvailabilityProbe
import com.aqua.aqualight.data.notifications.NotificationPlatform
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Owner-scoped firmware discovery with central dispatch and bounded retries. */
class DeviceFirmwareAvailabilityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ownerUid = inputData.getString(KEY_OWNER_UID).orEmpty().trim()
        if (ownerUid.isBlank()) return Result.success()

        return try {
            mapOutcome(createRunner().execute(ownerUid))
        } catch (error: IOException) {
            Log.w(TAG, "Firmware availability storage read failed; retrying.", error)
            retryOrFinish()
        }
    }

    private fun createRunner(): DeviceFirmwareAvailabilityCheckRunner {
        val platform = NotificationPlatform.get(applicationContext)
        val probe = DeviceFirmwareBackgroundAvailabilityProbe()
        return DeviceFirmwareAvailabilityCheckRunner(
            ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext),
            preferenceUseCase = platform.preferenceUseCase,
            notifications = platform.deviceFirmwareUpdates,
            snapshotReader = DeviceFirmwareAvailabilitySnapshotSource.create(applicationContext),
            manifestLoader = probe::loadManifest,
            hintEvaluator = probe::evaluate
        )
    }

    private fun mapOutcome(outcome: DeviceFirmwareAvailabilityCheckOutcome): Result {
        return when (outcome) {
            DeviceFirmwareAvailabilityCheckOutcome.Completed,
            DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged,
            DeviceFirmwareAvailabilityCheckOutcome.NotificationsUnavailable -> Result.success()
            is DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure -> {
                Log.w(TAG, "Firmware availability retry requested at ${outcome.stage}.")
                retryOrFinish()
            }
        }
    }

    private fun retryOrFinish(): Result {
        return if (runAttemptCount + 1 < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        internal const val KEY_OWNER_UID = "owner_uid"
        private const val TAG = "FirmwareAvailability"
        private const val PERIODIC_WORK_PREFIX =
            "device_firmware_availability_periodic_owner_"
        private const val IMMEDIATE_WORK_PREFIX =
            "device_firmware_availability_immediate_owner_"
        private const val REPEAT_INTERVAL_HOURS = 24L
        private const val BACKOFF_SECONDS = 30L
        private const val MAX_ATTEMPTS = 3

        fun schedule(context: Context, ownerUid: String) {
            val owner = ownerUid.trim()
            if (owner.isBlank()) return

            enqueuePeriodic(context.applicationContext, owner)
            enqueueImmediate(context.applicationContext, owner)
        }

        fun enqueueImmediate(context: Context, ownerUid: String) {
            enqueueOneTime(
                context = context,
                ownerUid = ownerUid,
                policy = ExistingWorkPolicy.KEEP
            )
        }

        fun cancel(context: Context, ownerUid: String?) {
            val owner = ownerUid?.trim()?.takeIf(String::isNotBlank) ?: return
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(periodicWorkName(owner))
                cancelUniqueWork(immediateWorkName(owner))
            }
        }

        private fun enqueueOneTime(
            context: Context,
            ownerUid: String,
            policy: ExistingWorkPolicy
        ) {
            val owner = ownerUid.trim()
            if (owner.isBlank()) return

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                immediateWorkName(owner),
                policy,
                immediateRequest(owner)
            )
        }

        private fun enqueuePeriodic(context: Context, ownerUid: String) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                periodicWorkName(ownerUid),
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest(ownerUid)
            )
        }

        private fun periodicRequest(ownerUid: String) =
            PeriodicWorkRequestBuilder<DeviceFirmwareAvailabilityWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_OWNER_UID to ownerUid))
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

        private fun immediateRequest(ownerUid: String) =
            OneTimeWorkRequestBuilder<DeviceFirmwareAvailabilityWorker>()
                .setInputData(workDataOf(KEY_OWNER_UID to ownerUid))
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

        private fun networkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        }

        internal fun periodicWorkName(ownerUid: String): String {
            return PERIODIC_WORK_PREFIX + ownerUid
        }

        internal fun immediateWorkName(ownerUid: String): String {
            return IMMEDIATE_WORK_PREFIX + ownerUid
        }
    }
}
