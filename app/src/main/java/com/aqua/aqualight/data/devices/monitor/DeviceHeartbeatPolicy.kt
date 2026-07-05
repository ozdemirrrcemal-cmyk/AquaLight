package com.aqua.aqualight.data.devices.monitor

/**
 * Central timing policy for device presence decisions.
 *
 * Firmware announces periodically and Android actively refreshes while the app process is alive.
 * These values keep online/offline decisions live without allowing UI screens to invent their own
 * timing rules or causing runtime/auth badges to flicker.
 */
data class DeviceHeartbeatPolicy(
    val udpFreshMillis: Long = 20_000L,
    val udpStaleMillis: Long = 35_000L,
    val wsFreshMillis: Long = 20_000L,
    val authFreshMillis: Long = 60_000L
) {
    init {
        require(udpFreshMillis > 0L)
        require(udpStaleMillis >= udpFreshMillis)
        require(wsFreshMillis > 0L)
        require(authFreshMillis > 0L)
    }
}
