package com.aqua.aqualight.data.devices.model

/**
 * Detailed internal state. UI can map this to a simpler user-facing label.
 */
enum class DeviceOnlineState {
    UNKNOWN,
    DISCOVERING,
    ONLINE_LAN,
    CONNECTING_WS,
    AUTHENTICATED,
    STALE,
    OFFLINE,
    LOCAL_NETWORK_OFFLINE,
    AUTH_REQUIRED,
    PROVISIONING,
    OTA_UPDATING,
    ERROR
}
