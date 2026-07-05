package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * User-facing presence contract.
 *
 * Runtime/auth/socket states are engineering details. Product UI surfaces should answer the only
 * question that matters to users: can the app reach this device right now?
 */
object DevicePresencePresentationMapper {

    fun availabilityLabel(state: DeviceOnlineState): String = when {
        isReachable(state) -> "Online"
        else -> "Offline"
    }

    fun accessLabel(state: DeviceOnlineState): String = when {
        isReachable(state) -> "Ready"
        else -> "Unavailable"
    }

    fun isReachable(state: DeviceOnlineState): Boolean {
        return when (state) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> true

            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR -> false
        }
    }
}
