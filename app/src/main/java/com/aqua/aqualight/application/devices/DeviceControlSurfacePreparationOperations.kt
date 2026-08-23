package com.aqua.aqualight.application.devices

/**
 * Application boundary for preparing a device control surface before navigation.
 *
 * Menu access proves that the device is currently reachable and commercially valid. Preparation
 * then proves that the family-specific control surface can render a complete authoritative first
 * frame without leaking device-feature state into the Devices UI layer.
 */
interface DeviceControlSurfacePreparationOperations {
    suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult

    /**
     * Consumes the one-shot handoff created by a successful authoritative preparation.
     * Destinations must still re-check their central authoritative state before using this marker
     * to skip a duplicate initial refresh.
     */
    fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = false

    /**
     * Discards a one-shot handoff when the UI cannot commit the prepared navigation attempt.
     * Implementations must keep this idempotent so lifecycle teardown can safely call it as well.
     */
    fun discardFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ) = Unit
}

data class DeviceControlSurfacePreparationRequest(
    val deviceUid: String,
    val family: OwnerDeviceFamily
)

sealed interface DeviceControlSurfacePreparationResult {
    data object Ready : DeviceControlSurfacePreparationResult

    data class Unavailable(
        val reason: DeviceMenuUnavailableReason
    ) : DeviceControlSurfacePreparationResult
}
