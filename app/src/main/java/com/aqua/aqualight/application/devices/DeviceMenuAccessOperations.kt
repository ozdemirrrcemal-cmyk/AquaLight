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

/**
 * Family-aware application preparation performed after liveness and catalog validation but before
 * a control surface is opened. A successful result guarantees that the destination can render its
 * first frame from current authoritative state instead of replaying an earlier screen snapshot.
 */
fun interface DeviceMenuPresentationPreparationOperations {
    suspend fun prepare(access: DeviceMenuAccessResult.Available): Boolean
}

sealed interface DeviceMenuAccessResult {
    data class Available(
        val deviceUid: String,
        val title: String,
        val family: OwnerDeviceFamily,
        val presentationPrepared: Boolean = false
    ) : DeviceMenuAccessResult

    data class Unavailable(
        val title: String,
        val reason: DeviceMenuUnavailableReason
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
    CURRENT_DATA_NOT_READY,
    COMMERCIAL_PRODUCT_MISMATCH
}
