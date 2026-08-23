package com.aqua.aqualight.application.devices

/**
 * Application boundary for preparing a device control surface before navigation.
 *
 * Menu access proves that a device can be opened. Preparation then ensures any family-specific
 * presentation state required for a stable first frame is available without leaking that state
 * into the Devices UI layer.
 */
interface DeviceControlSurfacePreparationOperations {
    suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult

    /**
     * Consumes a one-shot marker when preparation already performed the initial authoritative
     * refresh. The destination may skip only that duplicate refresh; normal warm-cache openings
     * still revalidate in the background.
     */
    fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = false
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
