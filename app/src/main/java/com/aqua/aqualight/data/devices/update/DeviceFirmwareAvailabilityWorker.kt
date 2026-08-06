package com.aqua.aqualight.data.devices.update

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.notifications.NotificationPlatform
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Periodic, runtime-free firmware discovery for a previously authenticated owner. */
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
        return DeviceFirmwareAvailabilityCheckRunner(
            context = applicationContext,
            ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext),
            preferenceUseCase = platform.preferenceUseCase,
            notifications = platform.deviceFirmwareUpdates,
            isProcessForeground = ::isProcessForeground
        )
    }

    private fun mapOutcome(outcome: DeviceFirmwareAvailabilityCheckOutcome): Result {
        return when (outcome) {
            DeviceFirmwareAvailabilityCheckOutcome.Completed,
            DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged,
            DeviceFirmwareAvailabilityCheckOutcome.Foreground,
            DeviceFirmwareAvailabilityCheckOutcome.NotificationsUnavailable -> Result.success()
            is DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure -> {
                Log.w(TAG, "Firmware availability retry requested at ${outcome.stage}.")
                retryOrFinish()
            }
        }
    }

    private fun isProcessForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun retryOrFinish(): Result {
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        internal const val KEY_OWNER_UID = "owner_uid"
        private const val TAG = "FirmwareAvailability"
        private const val WORK_NAME_PREFIX = "device_firmware_availability_owner_"
        private const val REPEAT_INTERVAL_HOURS = 6L
        private const val RETRY_BACKOFF_MINUTES = 30L
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context, ownerUid: String) {
            val normalizedOwnerUid = ownerUid.trim()
            if (normalizedOwnerUid.isBlank()) return

            val request = periodicRequest(normalizedOwnerUid)
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                workName(normalizedOwnerUid),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context, ownerUid: String?) {
            ownerUid
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { normalizedOwnerUid ->
                    WorkManager.getInstance(context.applicationContext)
                        .cancelUniqueWork(workName(normalizedOwnerUid))
                }
        }

        internal fun workName(ownerUid: String): String {
            return WORK_NAME_PREFIX + ownerUid
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
                    RETRY_BACKOFF_MINUTES,
                    TimeUnit.MINUTES
                )
                .addTag(workName(ownerUid))
                .build()

        private fun networkConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        }
    }
}
