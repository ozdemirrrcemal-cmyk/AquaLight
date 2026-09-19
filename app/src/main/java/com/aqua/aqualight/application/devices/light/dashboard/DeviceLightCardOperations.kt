package com.aqua.aqualight.application.devices.light.dashboard

import kotlinx.coroutines.flow.Flow

/** Read-only application boundary for the tank-detail Light information card. */
interface DeviceLightCardOperations {
    fun observe(deviceUid: String): Flow<DeviceLightCardState>
}

sealed interface DeviceLightCardState {
    data object Preparing : DeviceLightCardState
    data class Ready(val snapshot: DeviceLightControlSnapshot) : DeviceLightCardState
    data class Unavailable(val reason: DeviceLightCardUnavailableReason) : DeviceLightCardState
}

enum class DeviceLightCardUnavailableReason {
    INVALID_DEVICE_UID,
    DEVICE_NOT_REGISTERED,
    DEVICE_FAMILY_MISMATCH,
    RUNTIME_CONNECTION_FAILED,
    AUTHORITATIVE_REFRESH_FAILED,
    OBSERVATION_FAILED
}
