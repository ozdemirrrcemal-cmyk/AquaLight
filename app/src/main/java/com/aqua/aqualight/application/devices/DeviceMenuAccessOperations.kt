package com.aqua.aqualight.application.devices

/**
 * Application boundary for deciding whether a device control surface can be opened.
 *
 * Implementations prove current device liveness. UI receives only application values and never
 * observes discovery, WebSocket, command-client or repository implementation types.
 */
interface DeviceMenuAccessOperations {
    suspend fun resolve(deviceUid: String): DeviceMenuAccessResult
}

sealed interface DeviceMenuAccessResult {
    data class Available(
        val deviceUid: String,
        val title: String,
        val family: OwnerDeviceFamily
    ) : DeviceMenuAccessResult

    data class Unavailable(
        val title: String,
        val reason: DeviceMenuUnavailableReason,
        val diagnostic: DeviceOperationDiagnostic? = null
    ) : DeviceMenuAccessResult
}

enum class DeviceMenuUnavailableReason {
    INVALID_DEVICE_UID,
    DEVICE_NOT_REGISTERED,
    LOCAL_NETWORK_UNAVAILABLE,
    AUTHENTICATION_REQUIRED,
    DEVICE_UNRESPONSIVE,
    VERIFICATION_TIMED_OUT,
    CURRENT_LIVENESS_NOT_PROVEN,
    COMMERCIAL_PRODUCT_MISMATCH
}
