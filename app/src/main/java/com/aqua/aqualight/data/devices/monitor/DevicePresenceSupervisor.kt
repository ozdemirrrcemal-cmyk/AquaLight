package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveredDevice
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceSnapshotMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Raw discovery collector.
 *
 * This class stamps UDP evidence and preserves discovery metadata only. It deliberately does not
 * calculate time-based product presence. [DevicePresenceRuntimeMonitor] is the single reducer that
 * combines UDP, authenticated runtime proof, Android local-network state and elapsed time into the
 * canonical registry state consumed by every UI surface.
 */
class DevicePresenceSupervisor(
    private val elapsedRealtimeMillis: () -> Long = DeviceElapsedRealtimeClock::nowMillis
) {

    private val _snapshots = MutableStateFlow<Map<DeviceUid, DeviceSnapshot>>(emptyMap())

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = _snapshots.asStateFlow()

    val devices: Flow<List<DeviceSnapshot>> = snapshots.map { byUid ->
        byUid.values.sortedWith(
            compareBy<DeviceSnapshot> { it.title.lowercase() }
                .thenBy { it.deviceUid.value }
        )
    }

    fun onDiscoveredDevice(device: AqlDiscoveredDevice) {
        val incoming = device.snapshot
        val uid = incoming.deviceUid
        val receivedAtMillis = device.receivedAtMillis
        val receivedAtElapsedMillis = elapsedRealtimeMillis()

        _snapshots.update { current ->
            val previous = current[uid]
            val merged = mergeDiscovery(
                previous = previous,
                incoming = incoming,
                receivedAtMillis = receivedAtMillis,
                receivedAtElapsedMillis = receivedAtElapsedMillis
            )
            current + (uid to merged)
        }
    }

    fun reevaluate(localNetworkAvailable: Boolean = true) {
        if (localNetworkAvailable) return

        _snapshots.update { current ->
            current.mapValues { (_, snapshot) ->
                snapshot.copy(
                    connectionState = snapshot.connectionState.copy(
                        onlineState = DeviceOnlineState.LOCAL_NETWORK_OFFLINE
                    )
                )
            }
        }
    }

    fun remove(deviceUid: DeviceUid) {
        _snapshots.update { current -> current - deviceUid }
    }

    private fun mergeDiscovery(
        previous: DeviceSnapshot?,
        incoming: DeviceSnapshot,
        receivedAtMillis: Long,
        receivedAtElapsedMillis: Long
    ): DeviceSnapshot {
        val previousState = previous?.connectionState ?: DeviceConnectionState()
        val incomingWithPresence = incoming.copy(
            connectionState = previousState.copy(
                onlineState = DeviceOnlineState.ONLINE_LAN,
                lastUdpSeenAtMillis = receivedAtMillis,
                lastUdpSeenElapsedMillis = receivedAtElapsedMillis,
                lastErrorMessage = null
            ),
            lastSeenAtMillis = receivedAtMillis
        )

        return DeviceSnapshotMerger.merge(
            previous = previous,
            incoming = incomingWithPresence
        )
    }
}
