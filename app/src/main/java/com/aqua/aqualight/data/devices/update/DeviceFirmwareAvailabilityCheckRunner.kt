@file:Suppress("LongParameterList")

package com.aqua.aqualight.data.devices.update

import android.content.Context
import com.aqua.aqualight.application.devices.DEVICE_FIRMWARE_MANIFEST_URL
import com.aqua.aqualight.application.notifications.NotificationCategory
import com.aqua.aqualight.application.notifications.NotificationPreferenceUseCase
import com.aqua.aqualight.data.auth.AuthenticatedOwnerProvider
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAvailabilityHint
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareBackgroundAvailabilityProbe
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareManifest
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.platform.notifications.DeviceFirmwareUpdateNotificationOperations

internal class DeviceFirmwareAvailabilityCheckRunner(
    context: Context,
    private val ownerProvider: AuthenticatedOwnerProvider,
    private val preferenceUseCase: NotificationPreferenceUseCase,
    private val notifications: DeviceFirmwareUpdateNotificationOperations,
    private val isProcessForeground: () -> Boolean,
    private val probe: DeviceFirmwareBackgroundAvailabilityProbe =
        DeviceFirmwareBackgroundAvailabilityProbe(),
    private val snapshotLoader: suspend (String) -> List<DeviceSnapshot> = { ownerUid ->
        DeviceKnownStore(context.applicationContext, ownerUid).loadSnapshots()
    }
) {

    suspend fun execute(ownerUid: String): DeviceFirmwareAvailabilityCheckOutcome {
        val owner = ownerUid.trim()
        return when {
            owner.isBlank() -> DeviceFirmwareAvailabilityCheckOutcome.Completed
            !isOwnerActive(owner) -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
            isProcessForeground() -> DeviceFirmwareAvailabilityCheckOutcome.Foreground
            !canDeliver(owner) -> {
                DeviceFirmwareAvailabilityCheckOutcome.NotificationsUnavailable
            }
            else -> executeEligible(owner)
        }
    }

    private suspend fun executeEligible(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome {
        val snapshots = snapshotLoader(ownerUid)
        val interruption = interruptionOutcome(ownerUid)
        return interruption ?: reconcileAndEvaluate(ownerUid, snapshots)
    }

    private suspend fun reconcileAndEvaluate(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>
    ): DeviceFirmwareAvailabilityCheckOutcome {
        notifications.reconcileDevices(ownerUid, snapshots.deviceUids())
        if (snapshots.isEmpty()) {
            return DeviceFirmwareAvailabilityCheckOutcome.Completed
        }

        val manifest = probe.loadManifest(DEVICE_FIRMWARE_MANIFEST_URL).getOrNull()
        return if (manifest == null) {
            DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure(
                DeviceFirmwareAvailabilityFailureStage.MANIFEST
            )
        } else {
            evaluateDevices(ownerUid, snapshots, manifest)
        }
    }

    private suspend fun evaluateDevices(
        ownerUid: String,
        snapshots: List<DeviceSnapshot>,
        manifest: DeviceFirmwareManifest
    ): DeviceFirmwareAvailabilityCheckOutcome {
        var interrupted: DeviceFirmwareAvailabilityCheckOutcome? = null
        var evaluationFailed = false
        for (snapshot in snapshots) {
            interrupted = interruptionOutcome(ownerUid)
            if (interrupted != null) break
            if (!evaluateDevice(ownerUid, snapshot, manifest)) {
                evaluationFailed = true
            }
        }
        return interrupted ?: if (evaluationFailed) {
            DeviceFirmwareAvailabilityCheckOutcome.RetryableFailure(
                DeviceFirmwareAvailabilityFailureStage.DEVICE_EVALUATION
            )
        } else {
            DeviceFirmwareAvailabilityCheckOutcome.Completed
        }
    }

    private suspend fun evaluateDevice(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Boolean {
        val deviceUid = snapshot.deviceUid.value
        return if (!snapshot.capabilities.ota) {
            notifications.clearAvailability(ownerUid, deviceUid)
            true
        } else {
            evaluateSupportedDevice(ownerUid, snapshot, manifest)
        }
    }

    private suspend fun evaluateSupportedDevice(
        ownerUid: String,
        snapshot: DeviceSnapshot,
        manifest: DeviceFirmwareManifest
    ): Boolean {
        val hint = probe.evaluate(snapshot, manifest).getOrNull()
        if (hint != null) {
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

    private suspend fun canDeliver(ownerUid: String): Boolean {
        val preference = preferenceUseCase.snapshot(ownerUid)
        return preference.ownerPreferenceEnabled &&
            preference.readiness(NotificationCategory.DEVICE_UPDATES).canDeliver
    }

    private fun interruptionOutcome(
        ownerUid: String
    ): DeviceFirmwareAvailabilityCheckOutcome? = when {
        !isOwnerActive(ownerUid) -> DeviceFirmwareAvailabilityCheckOutcome.OwnerChanged
        isProcessForeground() -> DeviceFirmwareAvailabilityCheckOutcome.Foreground
        else -> null
    }

    private fun isOwnerActive(ownerUid: String): Boolean {
        return ownerProvider.currentOwnerUid() == ownerUid
    }
}

internal sealed interface DeviceFirmwareAvailabilityCheckOutcome {
    data object Completed : DeviceFirmwareAvailabilityCheckOutcome
    data object OwnerChanged : DeviceFirmwareAvailabilityCheckOutcome
    data object Foreground : DeviceFirmwareAvailabilityCheckOutcome
    data object NotificationsUnavailable : DeviceFirmwareAvailabilityCheckOutcome

    data class RetryableFailure(
        val stage: DeviceFirmwareAvailabilityFailureStage
    ) : DeviceFirmwareAvailabilityCheckOutcome
}

internal enum class DeviceFirmwareAvailabilityFailureStage {
    MANIFEST,
    DEVICE_EVALUATION
}

private fun List<DeviceSnapshot>.deviceUids(): Set<String> {
    return mapTo(mutableSetOf()) { snapshot -> snapshot.deviceUid.value }
}
