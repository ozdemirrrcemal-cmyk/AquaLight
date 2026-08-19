package com.aqua.aqualight.application.devices.dosing

/**
 * Revision-aware reservoir mutation capability for the long-lived reservoir editor.
 *
 * Firmware reservoir configuration and Android-owned low-level notification intent stay separate:
 * changing notification intent must never re-apply the physical reservoir baseline.
 */
interface DeviceDosingReservoirRevisionOperations {
    suspend fun applyReservoirSettingsAtRevision(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult

    suspend fun setReservoirLowLevelAlertEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult
}

suspend fun DeviceDosingChannelOperations.applyReservoirSettingsAgainstBaseRevision(
    deviceUid: String,
    slotId: String,
    settings: DeviceDosingReservoirSettings,
    baseRevision: Long
): DeviceDosingChannelOperationResult =
    (this as? DeviceDosingReservoirRevisionOperations)?.applyReservoirSettingsAtRevision(
        deviceUid = deviceUid,
        slotId = slotId,
        settings = settings,
        expectedRevision = baseRevision
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
