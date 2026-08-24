package com.aqua.aqualight.application.devices.dosing

import kotlinx.coroutines.flow.Flow

/**
 * Read-only application port for the tank-detail Dosing device card.
 *
 * The UI observes a typed preparation state only. Runtime/session preparation remains owned by the
 * production Dosing adapter, and implementations must convert non-cancellation runtime failures to
 * [DeviceDosingCardState.Unavailable] instead of leaking transport/data exceptions to presentation.
 */
interface DeviceDosingCardOperations {
    fun observe(deviceUid: String): Flow<DeviceDosingCardState>
}

sealed interface DeviceDosingCardState {
    data object Preparing : DeviceDosingCardState

    data class Ready(
        val summary: DeviceDosingCardSummary
    ) : DeviceDosingCardState

    data class Unavailable(
        val reason: DeviceDosingCardUnavailableReason
    ) : DeviceDosingCardState
}

enum class DeviceDosingCardUnavailableReason {
    INVALID_DEVICE_UID,
    DEVICE_NOT_REGISTERED,
    DEVICE_FAMILY_MISMATCH,
    RUNTIME_CONNECTION_FAILED,
    AUTHORITATIVE_REFRESH_FAILED,
    OBSERVATION_FAILED
}
