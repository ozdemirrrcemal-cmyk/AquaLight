package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceUid

sealed interface AqlWsConnectionState {
    data object Disconnected : AqlWsConnectionState

    data class Connecting(
        val deviceUid: DeviceUid,
        val url: String
    ) : AqlWsConnectionState

    data class Connected(
        val deviceUid: DeviceUid,
        val url: String,
        val connectedAtMillis: Long
    ) : AqlWsConnectionState

    data class Authenticated(
        val deviceUid: DeviceUid,
        val authenticatedAtMillis: Long
    ) : AqlWsConnectionState

    data class AuthRequired(
        val deviceUid: DeviceUid,
        val message: String
    ) : AqlWsConnectionState

    data class Failed(
        val deviceUid: DeviceUid?,
        val message: String,
        val cause: Throwable? = null
    ) : AqlWsConnectionState
}
