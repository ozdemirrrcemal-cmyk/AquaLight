package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.data.auth.AuthenticatedOwnerProvider
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifest
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

/** Evaluates trusted snapshots while enforcing owner continuity at every dispatch boundary. */
internal class DeviceFirmwareAvailabilityEvaluator(
    private val ownerProvider: AuthenticatedOwnerProvider,
    private val notifications: DeviceFirmwareUpdateNotificationOperations,
    private val hintEvaluator: (
        DeviceSnapshot,
        DeviceFirmwareManifest
    ) -> Result<DeviceFirmwareAvailabilityHint>
) {

    suspend fun evaluate(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>,
        manifest: DeviceFirmwareManifest
    ): DeviceFirmwareAvailabilityCheckOutcome {
        var ownerActive = isOwnerActive(ownerUid)
        var evaluationFailed = false
        for (snapshot in snapshots) {
            if (ownerActive) {
                evaluationFailed = !evaluateDevice(ownerUid, snapshot, manifest) ||
                    evaluationFailed
                ownerActive = isOwnerActive(ownerUid)
            }
        }
        return when {
            !ownerActive -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            evaluationFailed ->
                retryOutcome(DeviceFirmwareAvailabilityFailureStage.DEVICE_EVALUATION)
            else -> DeviceFirmwareAvailabilityCheckOutcome.Completed
        }
    }

    private suspend fun evaluateDevice(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Boolean {
        return if (!snapshot.capabilities.ota) {
            notifications.clearAvailability(ownerUid, snapshot.deviceUid.value)
            true
        } else {
            evaluateHint(ownerUid, snapshot, manifest)
        }
    }

    private suspend fun evaluateHint(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Boolean {
        val hint = hintEvaluator(snapshot, manifest).getOrNull()
        if (hint != null && isOwnerActive(ownerUid)) {
            applyHint(ownerUid, hint)
        }
        return hint != null
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

    private fun isOwnerActive(ownerUid: String): Boolean {
        return ownerProvider.currentOwnerUid() == ownerUid
    }
}
