package com.aqua.aqualight.data.devices.monitor

/**
 * Central timing policy for device presence decisions.
 *
 * Authentication establishes a secure session, but sustained Online state is renewed by decoded
 * runtime traffic and correlated command responses. UDP remains a LAN-discovery signal only.
 */
data class DeviceHeartbeatPolicy(
    val udpFreshMillis: Long = 20_000L,
    val udpStaleMillis: Long = 35_000L,
    val wsFreshMillis: Long = 8_000L,
    val authenticationBootstrapFreshMillis: Long = 5_000L,
    val runtimeProofFreshMillis: Long = 15_000L
) {
    init {
        require(udpFreshMillis > 0L)
        require(udpStaleMillis >= udpFreshMillis)
        require(wsFreshMillis > 0L)
        require(authenticationBootstrapFreshMillis > 0L)
        require(runtimeProofFreshMillis >= authenticationBootstrapFreshMillis)
    }
}
