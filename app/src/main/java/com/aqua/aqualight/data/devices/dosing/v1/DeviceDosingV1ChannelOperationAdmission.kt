package com.aqua.aqualight.data.devices.dosing.v1

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny per-channel admission boundary between foreground writes and background read tokens.
 *
 * It never spans network I/O. A mutation marks the channel active before resolving its baseline;
 * background refreshes admitted earlier may finish, but later mutation request tokens make their
 * results stale. New background reads are skipped until the mutation leaves. Mutation-critical
 * readback bypasses this admission and remains part of the single-writer transaction.
 */
internal class DeviceDosingV1ChannelOperationAdmission {
    private val channels = ConcurrentHashMap<DeviceDosingV1Address, ChannelAdmission>()

    fun beginMutation(address: DeviceDosingV1Address) {
        val channel = channel(address)
        synchronized(channel.lock) {
            check(!channel.mutationActive) { "Dosing channel already has an active mutation" }
            channel.mutationActive = true
        }
    }

    fun endMutation(address: DeviceDosingV1Address) {
        val channel = channel(address)
        synchronized(channel.lock) {
            check(channel.mutationActive) { "Dosing channel mutation boundary is not active" }
            channel.mutationActive = false
        }
    }

    fun <T : Any> admitBackgroundRead(
        address: DeviceDosingV1Address,
        createToken: () -> T
    ): T? {
        val channel = channel(address)
        return synchronized(channel.lock) {
            if (channel.mutationActive) null else createToken()
        }
    }

    private fun channel(address: DeviceDosingV1Address): ChannelAdmission =
        channels.computeIfAbsent(address) { ChannelAdmission() }

    private class ChannelAdmission(
        val lock: Any = Any(),
        var mutationActive: Boolean = false
    )
}
