package com.aqua.aqualight.ui.common.devicepresence

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * User-facing presence contract.
 *
 * The product surface is deliberately binary: users see only Online or Offline. Internal discovery
 * and WebSocket handshake states remain available to runtime logic, but they are never exposed as a
 * third user-facing status. "Online" means the authenticated control surface is ready.
 */
object DevicePresencePresentationMapper {

    @StringRes
    fun availabilityLabelRes(state: DeviceOnlineState): Int =
        if (isReachable(state)) R.string.device_online else R.string.device_offline

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

    /** Internal diagnostic state; it must not be rendered as a user-facing label. */
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
