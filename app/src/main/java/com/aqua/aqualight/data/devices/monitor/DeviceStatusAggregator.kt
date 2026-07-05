package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * Converts explicit network/runtime events into one user-facing device state.
 *
 * Authenticated runtime state is not aged offline by a timer. Foreground refresh and WebSocket
 * callbacks are responsible for producing fresh CONNECTING/ONLINE/OFFLINE transitions.
 */
class DeviceStatusAggregator(
    private val policy: DeviceHeartbeatPolicy = DeviceHeartbeatPolicy()
) {

    fun resolve(
        state: DeviceConnectionState,
        nowMillis: Long = System.currentTimeMillis(),
        localNetworkAvailable: Boolean = true
    ): DeviceOnlineState {
        if (!localNetworkAvailable) return DeviceOnlineState.LOCAL_NETWORK_OFFLINE

        return when (state.onlineState) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> state.onlineState

            DeviceOnlineState.CONNECTING_WS -> {
                if (isWithinForegroundReconnectGrace(state, nowMillis)) {
                    DeviceOnlineState.CONNECTING_WS
                } else {
                    lanPresenceState(state, nowMillis) ?: DeviceOnlineState.OFFLINE
                }
            }

            DeviceOnlineState.ERROR -> lanPresenceState(state, nowMillis) ?: DeviceOnlineState.OFFLINE

            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.UNKNOWN -> lanPresenceState(state, nowMillis) ?: DeviceOnlineState.UNKNOWN
        }
    }

    private fun isWithinForegroundReconnectGrace(
        state: DeviceConnectionState,
        nowMillis: Long
    ): Boolean {
        val lastWsConnectedAt = state.lastWsConnectedAtMillis ?: return true
        return nowMillis - lastWsConnectedAt <= policy.foregroundReconnectGraceMillis
    }

    private fun lanPresenceState(
        state: DeviceConnectionState,
        nowMillis: Long
    ): DeviceOnlineState? {
        val lastUdpSeenAt = state.lastUdpSeenAtMillis ?: return null
        val ageMillis = nowMillis - lastUdpSeenAt
        return if (ageMillis <= policy.udpFreshMillis) {
            DeviceOnlineState.ONLINE_LAN
        } else {
            DeviceOnlineState.OFFLINE
        }
    }
}
