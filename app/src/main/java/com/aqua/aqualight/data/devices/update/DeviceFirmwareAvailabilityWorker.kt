package com.aqua.aqualight.data.devices.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareBackgroundAvailabilityProbe
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.notifications.DeviceUpdateNotificationLedger
import com.aqua.aqualight.data.notifications.NotificationPlatform
import com.aqua.aqualight.platform.notifications.AndroidDeviceFirmwareUpdateNotificationPublisher
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Periodic, runtime-free firmware discovery for a previously authenticated owner.
 *
 * The worker reads only durable non-secret device metadata and a signed official manifest. It never
 * opens UDP discovery, WebSocket runtime, device credentials or an installable OTA plan.
 */
class DeviceFirmwareAvailabilityWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val ownerUid = inputData.getString(KEY_OWNER_UID).orEmpty().trim()
        if (ownerUid.isBlank()) return Result.success()

        val ownerProvider = FirebaseAuthenticatedOwnerProvider.create(applicationContext)
        if (ownerProvider.currentOwnerUid() != ownerUid) {
            return Result.success()
        }

        // A live owner repository means foreground/runtime notification ownership is active.
        // Skipping avoids replacing an in-progress OTA notification with a durable snapshot hint.
        if (DevicesRepositoryProvider.currentOwnerUid() == ownerUid) {
            return Result.success()
        }

        return try {
            runCheck(ownerUid, ownerProvider)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            retryOrFinish()
        }
    }

    private suspend fun runCheck(
        ownerUid: String,
        ownerProvider: FirebaseAuthenticatedOwnerProvider
    ): Result {
        val platform = NotificationPlatform.get(applicationContext)
        val preference = platform.preferenceUseCase.snapshot(ownerUid)
        if (
            !preference.ownerPreferenceEnabled ||
            !preference.readiness(NotificationCategory.DEVICE_UPDATES).canDeliver
        ) {
            return Result.success()
        }

        val ledger = DeviceUpdateNotificationLedger.create(applicationContext)
        val snapshots = DeviceKnownStore(applicationContext, ownerUid).loadSnapshots()
        val currentDeviceUids = snapshots.map { snapshot -> snapshot.deviceUid.value }.toSet()
        reconcileRemovedDevices(ownerUid, currentDeviceUids, ledger, platform)
        if (snapshots.isEmpty()) return Result.success()

        val probe = DeviceFirmwareBackgroundAvailabilityProbe()
        val manifest = probe.loadManifest(DEVICE_FIRMWARE_MANIFEST_URL)
            .getOrElse { return retryOrFinish() }
        val publisher = AndroidDeviceFirmwareUpdateNotificationPublisher(
            context = applicationContext,
            ownerUid = ownerUid,
            dispatchUseCase = platform.dispatchUseCase,
            ledger = ledger
        )

        snapshots.forEach { snapshot ->
            if (
                ownerProvider.currentOwnerUid() != ownerUid ||
                DevicesRepositoryProvider.currentOwnerUid() == ownerUid
            ) {
                return Result.success()
            }
            val deviceUid = snapshot.deviceUid.value
            if (platform.renderer.isDeviceUpdateOperationNotificationActive(ownerUid, deviceUid)) {
                return@forEach
            }
            if (!snapshot.capabilities.ota) {
                clearAvailabilityAlert(ownerUid, deviceUid, ledger, platform)
                return@forEach
            }

            val hint = probe.evaluate(snapshot, manifest).getOrElse {
                clearAvailabilityAlert(ownerUid, deviceUid, ledger, platform)
                return@forEach
            }
            when (hint) {
                is DeviceFirmwareAvailabilityHint.UpdateAvailable -> {
                    publisher.publishAvailabilityHint(hint)
                }
                is DeviceFirmwareAvailabilityHint.UpToDate -> {
                    clearAvailabilityAlert(ownerUid, hint.deviceUid, ledger, platform)
                }
            }
        }

        return Result.success()
    }

    private suspend fun clearAvailabilityAlert(
        ownerUid: String,
        deviceUid: String,
        ledger: DeviceUpdateNotificationLedger,
        platform: NotificationPlatform
    ) {
        platform.renderer.cancelDeviceUpdate(ownerUid, deviceUid)
        ledger.clearDevice(ownerUid, deviceUid)
    }

    private fun retryOrFinish(): Result {
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private suspend fun reconcileRemovedDevices(
        ownerUid: String,
        currentDeviceUids: Set<String>,
        ledger: DeviceUpdateNotificationLedger,
        platform: NotificationPlatform
    ) {
        val removedDeviceUids = ledger.trackedDeviceUids(ownerUid) - currentDeviceUids
        removedDeviceUids.forEach { deviceUid ->
            platform.renderer.cancelDeviceUpdate(ownerUid, deviceUid)
            ledger.clearDevice(ownerUid, deviceUid)
        }
    }

    companion object {
        internal const val KEY_OWNER_UID = "owner_uid"
        private const val WORK_NAME_PREFIX = "device_firmware_availability_owner_"
        private const val REPEAT_INTERVAL_HOURS = 6L
        private const val RETRY_BACKOFF_MINUTES = 30L
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedule(context: Context, ownerUid: String) {
            val normalizedOwnerUid = ownerUid.trim()
            if (normalizedOwnerUid.isBlank()) return

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<DeviceFirmwareAvailabilityWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setInputData(workDataOf(KEY_OWNER_UID to normalizedOwnerUid))
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RETRY_BACKOFF_MINUTES,
                    TimeUnit.MINUTES
                )
                .addTag(workName(normalizedOwnerUid))
                .build()

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
    }
}
