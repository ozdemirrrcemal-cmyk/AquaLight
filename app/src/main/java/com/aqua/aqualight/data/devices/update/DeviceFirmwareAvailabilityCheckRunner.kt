@file:Suppress("LongParameterList")

package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.auth.AuthenticatedOwnerProvider
import com.aqua.aqualight.data.auth.OwnerTokenValidationResult
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifest
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifestNotPublishedException
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

internal class DeviceFirmwareAvailabilityCheckRunner(
    private val ownerProvider: AuthenticatedOwnerProvider,
    private val preferenceUseCase: NotificationPreferenceUseCase,
    private val notifications: DeviceFirmwareUpdateNotificationOperations,
    private val snapshotReader: DeviceFirmwareAvailabilitySnapshotReader,
    private val manifestLoader: suspend (String) -> Result<DeviceFirmwareManifest>,
    hintEvaluator: (
        DeviceSnapshot,
        DeviceFirmwareManifest
    ) -> Result<DeviceFirmwareAvailabilityHint>
) {

    private val evaluator = DeviceFirmwareAvailabilityEvaluator(
        ownerProvider = ownerProvider,
        notifications = notifications,
        hintEvaluator = hintEvaluator
    )

    suspend fun execute(ownerUid: String): DeviceFirmwareAvailabilityCheckOutcome {
        val owner = ownerUid.trim()
        val initialOutcome = initialOutcome(owner)
        return initialOutcome ?: loadAndEvaluate(owner)
    }

    private suspend fun initialOutcome(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome? = when {
        ownerUid.isBlank() -> DeviceFirmwareAvailabilityCheckOutcome.Completed
        !isOwnerActive(ownerUid) -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        else -> validateAndCheckDelivery(ownerUid)
    }

    private suspend fun validateAndCheckDelivery(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome? {
        val validationOutcome = validateOwner(ownerUid)
        return when {
            validationOutcome != null -> validationOutcome
            !isOwnerActive(ownerUid) -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            !canDeliver(ownerUid) ->
                DeviceFirmwareAvailabilityCheckOutcome.NotificationsUnavailable
            else -> null
        }
    }

    private suspend fun loadAndEvaluate(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome {
        val snapshotResult = snapshotReader.load(ownerUid)
        return if (!isOwnerActive(ownerUid)) {
            DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        } else {
            when (snapshotResult) {
                DeviceFirmwareAvailabilitySnapshotResult.Retryable ->
                    retryOutcome(DeviceFirmwareAvailabilityFailureStage.SNAPSHOT_SOURCE)
                is DeviceFirmwareAvailabilitySnapshotResult.Ready ->
                    reconcileAndEvaluate(ownerUid, snapshotResult)
            }
        }
    }

    private suspend fun reconcileAndEvaluate(
        ownerUid: String,
        snapshotResult: DeviceFirmwareAvailabilitySnapshotResult.Ready
    ): DeviceFirmwareAvailabilityCheckOutcome {
        notifications.reconcileDevices(
            ownerUid,
            snapshotResult.currentDeviceUids
        )
        val ownerActive = isOwnerActive(ownerUid) &&
            cancelUntrustedAvailability(ownerUid, snapshotResult)
        return when {
            !ownerActive -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            snapshotResult.eligibleSnapshots.isEmpty() ->
                DeviceFirmwareAvailabilityCheckOutcome.Completed
            else -> loadManifestAndEvaluate(ownerUid, snapshotResult.eligibleSnapshots)
        }
    }

    private suspend fun cancelUntrustedAvailability(
        ownerUid: String,
        snapshotResult: DeviceFirmwareAvailabilitySnapshotResult.Ready
    ): Boolean {
        val eligibleDeviceUids = snapshotResult.eligibleSnapshots
            .mapTo(mutableSetOf()) { snapshot -> snapshot.deviceUid.value }
        val untrustedDeviceUids = snapshotResult.currentDeviceUids - eligibleDeviceUids
        var ownerActive = isOwnerActive(ownerUid)
        for (deviceUid in untrustedDeviceUids) {
            if (ownerActive) {
                notifications.cancelUntrustedAvailability(ownerUid, deviceUid)
                ownerActive = isOwnerActive(ownerUid)
            }
        }
        return ownerActive
    }

    private suspend fun loadManifestAndEvaluate(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>
    ): DeviceFirmwareAvailabilityCheckOutcome {
        return manifestLoader(DEVICE_FIRMWARE_MANIFEST_URL).fold(
            onSuccess = { manifest -> evaluator.evaluate(ownerUid, snapshots, manifest) },
            onFailure = { error ->
                if (error is DeviceFirmwareManifestNotPublishedException) {
                    clearUnpublishedAvailability(ownerUid, snapshots)
                } else {
                    retryOutcome(DeviceFirmwareAvailabilityFailureStage.MANIFEST)
                }
            }
        )
    }

    private suspend fun clearUnpublishedAvailability(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>
    ): DeviceFirmwareAvailabilityCheckOutcome {
        for (snapshot in snapshots) {
            if (!isOwnerActive(ownerUid)) {
                return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            }
            notifications.clearAvailability(ownerUid, snapshot.deviceUid.value)
        }
        return if (isOwnerActive(ownerUid)) {
            DeviceFirmwareAvailabilityCheckOutcome.Completed
        } else {
            DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
    }

    private suspend fun validateOwner(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome? {
        return when (val result = ownerProvider.validateCurrentOwner()) {
            is OwnerTokenValidationResult.Valid -> {
                if (result.ownerUid == ownerUid) null
                else DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            }
            is OwnerTokenValidationResult.TransientFailure ->
                retryOutcome(DeviceFirmwareAvailabilityFailureStage.OWNER_VALIDATION)
            OwnerTokenValidationResult.Unauthenticated,
            is OwnerTokenValidationResult.Revoked ->
                DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
    }

    private suspend fun canDeliver(ownerUid: String): Boolean {
        val preference = preferenceUseCase.snapshot(ownerUid)
        return preference.ownerPreferenceEnabled &&
            preference.readiness(NotificationCategory.DEVICE_UPDATES).canDeliver
    }

    private fun isOwnerActive(ownerUid: String): Boolean {
        return ownerProvider.currentOwnerUid() == ownerUid
    }
}

internal sealed interface DeviceFirmwareAvailabilityCheckOutcome {
    data object Completed : DeviceFirmwareAvailabilityCheckOutcome
    data object OwnerChanged : DeviceFirmwareAvailabilityCheckOutcome
    data object NotificationsUnavailable : DeviceFirmwareAvailabilityCheckOutcome

    data class RetryableFailure(
        val stage: DeviceFirmwareAvailabilityFailureStage
    ) : DeviceFirmwareAvailabilityCheckOutcome
}

internal enum class DeviceFirmwareAvailabilityFailureStage {
    OWNER_VALIDATION,
    SNAPSHOT_SOURCE,
    MANIFEST,
    DEVICE_EVALUATION
}

internal fun retryOutcome(
    stage: DeviceFirmwareAvailabilityFailureStage
): DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure {
    return DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure(stage)
}
