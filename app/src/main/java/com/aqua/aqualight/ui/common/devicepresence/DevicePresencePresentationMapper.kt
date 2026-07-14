package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * User-facing presence contract.
 *
 * "Online" means the authenticated control surface is available, not merely
 * that a UDP announcement was seen on the LAN. This prevents the card from
 * promising menu access while WebSocket authentication is still reconnecting.
 */
object DevicePresencePresentationMapper {

    fun availabilityLabel(state: DeviceOnlineState): String = when {
        isReachable(state) -> "Online"
        isConnecting(state) -> "Connecting"
        else -> "Offline"
    }

    fun isReachable(state: DeviceOnlineState): Boolean {
        return when (state) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> true

            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR -> false
        }
    }

    fun isConnecting(state: DeviceOnlineState): Boolean {
        return when (state) {
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.DISCOVERING -> true

            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR -> false
        }
    }
}
