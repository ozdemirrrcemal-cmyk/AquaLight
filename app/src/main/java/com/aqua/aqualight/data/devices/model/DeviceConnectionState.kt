package com.aqua.aqualight.data.devices.model

data class DeviceConnectionState(
    val onlineState: DeviceOnlineState = DeviceOnlineState.UNKNOWN,
    val lastUdpSeenAtMillis: Long? = null,
    val lastWsConnectedAtMillis: Long? = null,
    val lastAuthenticatedAtMillis: Long? = null,
    val lastRuntimeMessageAtMillis: Long? = null,
    val lastControlProofAtMillis: Long? = null,
    val lastUdpSeenElapsedMillis: Long? = null,
    val lastWsConnectedElapsedMillis: Long? = null,
    val lastAuthenticatedElapsedMillis: Long? = null,
    val lastRuntimeMessageElapsedMillis: Long? = null,
    val lastControlProofElapsedMillis: Long? = null,
    val lastErrorMessage: String? = null
) {
    val isUsableOnline: Boolean
        get() = when (onlineState) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> true
            else -> false
        }

    val latestRuntimeProofElapsedMillis: Long?
        get() = listOfNotNull(
            lastControlProofElapsedMillis,
            lastRuntimeMessageElapsedMillis,
            lastAuthenticatedElapsedMillis
        ).maxOrNull()
}
