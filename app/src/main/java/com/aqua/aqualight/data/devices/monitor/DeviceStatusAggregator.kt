package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState

/**
 * Produces the single canonical device-availability state.
 *
 * UDP discovery is only LAN evidence and may trigger a runtime reconnect. A device is user-usable
 * only while its authenticated WebSocket runtime is live. UI surfaces still expose only Online or
 * Offline through DevicePresencePresentationMapper.
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

        if (state.runtimeConnected && state.runtimeAuthenticated) {
            return DeviceOnlineState.AUTHENTICATED
        }

        if (state.runtimeConnected) {
            return when (state.onlineState) {
                DeviceOnlineState.AUTH_REQUIRED -> DeviceOnlineState.AUTH_REQUIRED
                else -> DeviceOnlineState.CONNECTING_WS
            }
        }

        when (state.onlineState) {
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> return state.onlineState

            else -> Unit
        }

        val lastUdpSeenAt = state.lastUdpSeenAtMillis
        if (lastUdpSeenAt != null) {
            val udpAgeMillis = nowMillis - lastUdpSeenAt
            if (udpAgeMillis <= policy.udpFreshMillis) {
                return DeviceOnlineState.ONLINE_LAN
            }
        }

        val authenticatedWithinGrace = state.lastAuthenticatedAtMillis
            ?.let { authenticatedAt -> nowMillis - authenticatedAt <= policy.authFreshMillis }
            ?: false
        val webSocketWithinGrace = state.lastWsConnectedAtMillis
            ?.let { connectedAt -> nowMillis - connectedAt <= policy.wsFreshMillis }
            ?: false

        if (authenticatedWithinGrace || webSocketWithinGrace) {
            return DeviceOnlineState.STALE
        }

        if (lastUdpSeenAt != null) {
            val udpAgeMillis = nowMillis - lastUdpSeenAt
            if (udpAgeMillis <= policy.udpStaleMillis) {
                return DeviceOnlineState.STALE
            }
        }

        val hasPreviousPresenceEvidence =
            lastUdpSeenAt != null ||
                state.lastWsConnectedAtMillis != null ||
                state.lastAuthenticatedAtMillis != null

        return when {
            hasPreviousPresenceEvidence -> DeviceOnlineState.OFFLINE
            state.onlineState == DeviceOnlineState.ERROR -> DeviceOnlineState.ERROR
            state.onlineState == DeviceOnlineState.DISCOVERING -> DeviceOnlineState.DISCOVERING
            else -> DeviceOnlineState.UNKNOWN
        }
    }
}
