package com.aqua.aqualight.application.devices

/**
 * Application boundary for preparing a device control surface before navigation.
 *
 * Menu access already proves current device reachability and commercial validity. Preparation
 * handles only family-specific navigation prerequisites without turning a feature-data failure
 * into a second, contradictory device-offline decision.
 */
interface DeviceControlSurfacePreparationOperations {
    suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult

    /**
     * Consumes the one-shot handoff created by successful family preparation.
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
