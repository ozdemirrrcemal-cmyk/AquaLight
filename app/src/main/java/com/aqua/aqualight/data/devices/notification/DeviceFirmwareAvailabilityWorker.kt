package com.aqua.aqualight.data.devices.notification

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
import com.aqua.aqualight.BuildConfig
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.auth.OwnerBackgroundRuntimeLease
import com.aqua.aqualight.data.user.UserDataScope
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Owner-scoped best-effort firmware availability check.
 *
 * Process-death recovery is bounded by [OwnerBackgroundRuntimeLease], which delegates to the same
 * owner session coordinator and repository/OTA graph as the foreground. This worker never opens a
 * parallel runtime and never posts Android notifications directly; visible delivery stays on the
 * central notification dispatch path.
 */
@Suppress(
    "TooManyFunctions",
    "TooGenericExceptionCaught",
    "RethrowCaughtException",
    "ReturnCount"
)
class DeviceFirmwareAvailabilityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ownerUid = UserDataScope.normalizeOwnerUid(inputData.getString(KEY_OWNER_UID))
        if (ownerUid.isBlank()) return Result.success()

        val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext)
        if (ownerProvider.currentOwnerUid() != ownerUid) return Result.success()

        val appContainer = applicationContext.requireAppContainer()
        if (!appContainer.notificationPreferenceUseCase.observe(ownerUid).first()) {
            return Result.success()
        }

        return try {
            OwnerBackgroundRuntimeLease.create(applicationContext).withOwner(ownerUid) {
                if (
                    ownerProvider.currentOwnerUid() != ownerUid ||
                    !appContainer.notificationPreferenceUseCase.observe(ownerUid).first()
                ) {
                    return@withOwner Result.success()
                }

                val refresh = appContainer.deviceFirmwareBackgroundOperations
                    .refreshRegisteredDevices(
                        manifestUrl = BuildConfig.AQL_OTA_MANIFEST_URL,
                        applyNow = true
                    )
                    .getOrThrow()
                if (refresh.failedDeviceCount > 0) retryOrFinish() else Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Firmware availability work failed for the scheduled owner.", error)
            retryOrFinish()
        }
    }

    private fun retryOrFinish(): Result {
        return if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.success() else Result.retry()
    }

    companion object {
        internal const val KEY_OWNER_UID = "owner_uid"
        private const val PERIODIC_WORK_NAME_PREFIX = "device_firmware_availability_owner_"
        private const val IMMEDIATE_WORK_NAME_PREFIX = "device_firmware_availability_now_owner_"
        private const val PERIODIC_HOURS = 12L
        private const val FLEX_HOURS = 2L
        private const val INITIAL_DELAY_SECONDS = 30L
        private const val BACKOFF_SECONDS = 30L
        private const val MAX_ATTEMPTS = 3
        private const val TAG = "FirmwareAvailability"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(
            context: Context,
            ownerUid: String,
            enqueueImmediate: Boolean = true
        ) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) return

            val appContext = context.applicationContext
            val periodic = PeriodicWorkRequestBuilder<DeviceFirmwareAvailabilityWorker>(
                PERIODIC_HOURS,
                TimeUnit.HOURS,
                FLEX_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_OWNER_UID to owner))
                .setConstraints(networkConstraints)
                .setInitialDelay(PERIODIC_HOURS, TimeUnit.HOURS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                periodicWorkName(owner),
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )
            if (enqueueImmediate) enqueueNow(appContext, owner)
        }

        fun enqueueNow(context: Context, ownerUid: String) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) return

            val request = OneTimeWorkRequestBuilder<DeviceFirmwareAvailabilityWorker>()
                .setInputData(workDataOf(KEY_OWNER_UID to owner))
                .setConstraints(networkConstraints)
                .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                immediateWorkName(owner),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, ownerUid: String?) {
            val owner = UserDataScope.normalizeOwnerUid(ownerUid)
            if (owner.isBlank()) return
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(periodicWorkName(owner))
                cancelUniqueWork(immediateWorkName(owner))
            }
        }

        internal fun periodicWorkName(ownerUid: String): String =
            PERIODIC_WORK_NAME_PREFIX + UserDataScope.normalizeOwnerUid(ownerUid)

        internal fun immediateWorkName(ownerUid: String): String =
            IMMEDIATE_WORK_NAME_PREFIX + UserDataScope.normalizeOwnerUid(ownerUid)
    }
}
