package com.aqua.aqualight.data.devices.model

data class DeviceConnectionState(
    val onlineState: DeviceOnlineState = DeviceOnlineState.UNKNOWN,
    val lastUdpSeenAtMillis: Long? = null,
    val lastWsConnectedAtMillis: Long? = null,
    val lastAuthenticatedAtMillis: Long? = null,
    val lastErrorMessage: String? = null
) {
    val isUsableOnline: Boolean
        get() = onlineState == DeviceOnlineState.AUTHENTICATED || onlineState == DeviceOnlineState.ONLINE_LAN
}
