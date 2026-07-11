package com.aqua.aqualight.data.devices.model

data class DeviceConnectionState(
    val onlineState: DeviceOnlineState = DeviceOnlineState.UNKNOWN,
    val lastUdpSeenAtMillis: Long? = null,
    val lastWsConnectedAtMillis: Long? = null,
    val lastAuthenticatedAtMillis: Long? = null,
    val lastErrorMessage: String? = null,
    val runtimeConnected: Boolean = false,
    val runtimeAuthenticated: Boolean = false
) {
    val isUsableOnline: Boolean
        get() = onlineState == DeviceOnlineState.AUTHENTICATED
}
