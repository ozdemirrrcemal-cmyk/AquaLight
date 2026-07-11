package com.aqua.aqualight.ui.common.devicepresence

import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * User-facing availability contract.
 *
 * UDP, socket, authentication, reconnect and stale states are engineering details. Product UI
 * surfaces expose only whether the device can currently accept authenticated control commands.
 */
object DevicePresencePresentationMapper {

    fun availabilityLabel(state: DeviceOnlineState): String = when {
        isReachable(state) -> "Online"
        else -> "Offline"
    }

    fun isReachable(state: DeviceOnlineState): Boolean {
        return state == DeviceOnlineState.AUTHENTICATED
    }
}
