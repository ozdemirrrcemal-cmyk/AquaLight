package com.aqua.aqualight.data.devices.monitor

/**
 * Central timing policy for foreground device presence decisions.
 *
 * Runtime connection/auth state is event-driven. These values are used only for LAN discovery
 * freshness and the short foreground reconnect window, not for aging authenticated devices offline.
 */
data class DeviceHeartbeatPolicy(
    val udpFreshMillis: Long = 60_000L,
    val foregroundReconnectGraceMillis: Long = 12_000L
) {
    init {
        require(udpFreshMillis > 0L)
        require(foregroundReconnectGraceMillis > 0L)
    }
}
