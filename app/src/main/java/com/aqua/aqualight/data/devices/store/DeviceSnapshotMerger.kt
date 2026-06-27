package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/**
 * Central merge rules for one logical device.
 *
 * Firmware identity is keyed by deviceUid. IP/endpoints can change, but custom user naming and
 * runtime timestamps should not be lost just because a fresh UDP announce arrived.
 */
object DeviceSnapshotMerger {

    fun merge(previous: DeviceSnapshot?, incoming: DeviceSnapshot): DeviceSnapshot {
        if (previous == null) return incoming

        return incoming.copy(
            identity = incoming.identity.copy(
                customName = incoming.identity.customName.ifBlank { previous.identity.customName }
            ),
            connectionState = mergeConnectionState(previous.connectionState, incoming.connectionState),
            lastSeenAtMillis = maxOf(previous.lastSeenAtMillis, incoming.lastSeenAtMillis)
        )
    }

    private fun mergeConnectionState(
        previous: DeviceConnectionState,
        incoming: DeviceConnectionState
    ): DeviceConnectionState {
        val resolvedOnlineState = when {
            incoming.onlineState != DeviceOnlineState.UNKNOWN -> incoming.onlineState
            previous.onlineState != DeviceOnlineState.UNKNOWN -> previous.onlineState
            else -> DeviceOnlineState.UNKNOWN
        }

        return incoming.copy(
            onlineState = resolvedOnlineState,
            lastUdpSeenAtMillis = maxNullable(previous.lastUdpSeenAtMillis, incoming.lastUdpSeenAtMillis),
            lastWsConnectedAtMillis = maxNullable(
                previous.lastWsConnectedAtMillis,
                incoming.lastWsConnectedAtMillis
            ),
            lastAuthenticatedAtMillis = maxNullable(
                previous.lastAuthenticatedAtMillis,
                incoming.lastAuthenticatedAtMillis
            ),
            lastErrorMessage = incoming.lastErrorMessage ?: previous.lastErrorMessage
        )
    }

    private fun maxNullable(left: Long?, right: Long?): Long? = when {
        left == null -> right
        right == null -> left
        else -> maxOf(left, right)
    }
}
