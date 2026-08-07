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
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

internal class DeviceFirmwareAvailabilityCheckRunner(
    private val ownerProvider: AuthenticatedOwnerProvider,
    private val preferenceUseCase: NotificationPreferenceUseCase,
    private val notifications: DeviceFirmwareUpdateNotificationOperations,
    private val snapshotReader: DeviceFirmwareAvailabilitySnapshotReader,
    private val manifestLoader: suspend (String) -> Result<DeviceFirmwareManifest>,
    private val hintEvaluator: (
        DeviceSnapshot,
        DeviceFirmwareManifest
    ) -> Result<DeviceFirmwareAvailabilityHint>
) {

    suspend fun execute(ownerUid: String): DeviceFirmwareAvailabilityCheckOutcome {
        val owner = ownerUid.trim()
        if (owner.isBlank()) {
            return DeviceFirmwareAvailabilityCheckOutcome.Completed
        }
        if (!isOwnerActive(owner)) {
            return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
        validateOwner(owner)?.let { outcome -> return outcome }
        if (!isOwnerActive(owner)) {
            return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
        if (!canDeliver(owner)) {
            return DeviceFirmwareAvailabilityCheckOutcome.NotificationsUnavailable
        }
        return loadAndEvaluate(owner)
    }

    private suspend fun loadAndEvaluate(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome {
        val snapshotResult = snapshotReader.load(ownerUid)
        if (!isOwnerActive(ownerUid)) {
            return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
        return when (snapshotResult) {
            DeviceFirmwareAvailabilitySnapshotResult.Retryable ->
                retry(DeviceFirmwareAvailabilityFailureStage.SNAPSHOT_SOURCE)
            is DeviceFirmwareAvailabilitySnapshotResult.Ready ->
                reconcileAndEvaluate(ownerUid, snapshotResult)
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
        if (!isOwnerActive(ownerUid)) {
            return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        }
        if (snapshotResult.eligibleSnapshots.isEmpty()) {
            return DeviceFirmwareAvailabilityCheckOutcome.Completed
        }
        val manifest = manifestLoader(DEVICE_FIRMWARE_MANIFEST_URL).getOrNull()
            ?: return retry(DeviceFirmwareAvailabilityFailureStage.MANIFEST)
        return evaluateDevices(
            ownerUid,
            snapshotResult.eligibleSnapshots,
            manifest
        )
    }

    private suspend fun evaluateDevices(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>,
        manifest: DeviceFirmwareManifest
    ): DeviceFirmwareAvailabilityCheckOutcome {
        var evaluationFailed = false
        for (snapshot in snapshots) {
            if (!isOwnerActive(ownerUid)) {
                return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            }
            if (!evaluateDevice(ownerUid, snapshot, manifest)) {
                evaluationFailed = true
            }
            if (!isOwnerActive(ownerUid)) {
                return DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            }
        }
        return if (evaluationFailed) {
            retry(DeviceFirmwareAvailabilityFailureStage.DEVICE_EVALUATION)
        } else {
            DeviceFirmwareAvailabilityCheckOutcome.Completed
        }
    }

    private suspend fun evaluateDevice(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Boolean {
        if (!snapshot.capabilities.ota) {
            notifications.clearAvailability(ownerUid, snapshot.deviceUid.value)
            return true
        }
        val hint = hintEvaluator(snapshot, manifest).getOrNull() ?: return false
        if (!isOwnerActive(ownerUid)) {
            return true
        }
        applyHint(ownerUid, hint)
        return true
    }

    private suspend fun applyHint(
        ownerUid: String,
        hint: DeviceFirmwareAvailabilityHint
    ) {
        when (hint) {
            is DeviceFirmwareAvailabilityHint.UpdateAvailable -> {
                notifications.publishAvailabilityHint(ownerUid, hint)
            }
            is DeviceFirmwareAvailabilityHint.UpToDate -> {
                notifications.clearAvailability(ownerUid, hint.deviceUid)
            }
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
                retry(DeviceFirmwareAvailabilityFailureStage.OWNER_VALIDATION)
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

    private fun retry(
        stage: DeviceFirmwareAvailabilityFailureStage
    ): DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure {
        return DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure(stage)
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
