package com.aqua.aqualight.application.devices.dosing

/**
 * Revision-origin-aware reservoir mutation capability for the long-lived reservoir editor.
 *
 * Firmware reservoir configuration and Android-owned low-level notification intent stay separate:
 * changing notification intent must never re-apply the physical reservoir baseline. The central
 * data coordinator rebases retry-safe configuration assignments on the latest firmware revision.
 */
data class DeviceDosingReservoirMutationOrigin(
    val revision: Long,
    val trackingEnabled: Boolean,
    val capacityMicroliters: Long?
) {
    init {
        require(revision >= 0L)
        require(trackingEnabled == (capacityMicroliters != null))
    }
}

interface DeviceDosingReservoirRevisionOperations {
    suspend fun applyReservoirSettingsAtOrigin(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        origin: DeviceDosingReservoirMutationOrigin
    ): DeviceDosingChannelOperationResult

    suspend fun setReservoirLowLevelAlertEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult
}

suspend fun DeviceDosingChannelOperations.applyReservoirSettingsAgainstOrigin(
    deviceUid: String,
    slotId: String,
    settings: DeviceDosingReservoirSettings,
    origin: DeviceDosingReservoirMutationOrigin
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingReservoirRevisionOperations)?.applyReservoirSettingsAtOrigin(
        deviceUid = deviceUid,
        slotId = slotId,
        settings = settings,
        origin = origin
    ) ?: DeviceDosingChannelOperationResult.Failed

suspend fun DeviceDosingChannelOperations.setReservoirLowLevelAlertPreference(
    deviceUid: String,
    slotId: String,
    enabled: Boolean
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingReservoirRevisionOperations)?.setReservoirLowLevelAlertEnabled(
        deviceUid = deviceUid,
        slotId = slotId,
        enabled = enabled
    ) ?: DeviceDosingChannelOperationResult.Failed
