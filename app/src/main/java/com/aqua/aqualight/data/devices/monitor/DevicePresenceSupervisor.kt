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
 * Discovery-side presence collector.
 *
 * UDP proves only current LAN visibility. Runtime/authentication evidence is preserved and all
 * freshness decisions use a monotonic clock, while wall-clock timestamps remain available for
 * human-readable "last seen" presentation.
 */
class DevicePresenceSupervisor(
    private val statusAggregator: DeviceStatusAggregator = DeviceStatusAggregator(),
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
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

    fun markWebSocketConnected(
        deviceUid: DeviceUid,
        connectedAtMillis: Long = wallClockMillis()
    ) {
        val connectedAtElapsedMillis = elapsedRealtimeMillis()
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.CONNECTING_WS,
                lastWsConnectedAtMillis = connectedAtMillis,
                lastWsConnectedElapsedMillis = connectedAtElapsedMillis,
                lastErrorMessage = null
            )
        }
    }

    fun markAuthenticated(
        deviceUid: DeviceUid,
        authenticatedAtMillis: Long = wallClockMillis()
    ) {
        val authenticatedAtElapsedMillis = elapsedRealtimeMillis()
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastAuthenticatedAtMillis = authenticatedAtMillis,
                lastAuthenticatedElapsedMillis = authenticatedAtElapsedMillis,
                lastRuntimeMessageAtMillis = authenticatedAtMillis,
                lastRuntimeMessageElapsedMillis = authenticatedAtElapsedMillis,
                lastWsConnectedAtMillis = previous.lastWsConnectedAtMillis
                    ?: authenticatedAtMillis,
                lastWsConnectedElapsedMillis = previous.lastWsConnectedElapsedMillis
                    ?: authenticatedAtElapsedMillis,
                lastErrorMessage = null
            )
        }
    }

    fun markAuthRequired(deviceUid: DeviceUid, message: String? = null) {
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.AUTH_REQUIRED,
                lastAuthenticatedAtMillis = null,
                lastAuthenticatedElapsedMillis = null,
                lastRuntimeMessageAtMillis = null,
                lastRuntimeMessageElapsedMillis = null,
                lastControlProofAtMillis = null,
                lastControlProofElapsedMillis = null,
                lastErrorMessage = message
            )
        }
    }

    fun markRuntimeError(deviceUid: DeviceUid, message: String?) {
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.ERROR,
                lastErrorMessage = message
            )
        }
    }

    fun reevaluate(
        localNetworkAvailable: Boolean = true,
        nowElapsedMillis: Long = elapsedRealtimeMillis()
    ) {
        _snapshots.update { current ->
            current.mapValues { (_, snapshot) ->
                val resolved = statusAggregator.resolve(
                    state = snapshot.connectionState,
                    nowElapsedMillis = nowElapsedMillis,
                    localNetworkAvailable = localNetworkAvailable
                )
                snapshot.copy(
                    connectionState = snapshot.connectionState.copy(onlineState = resolved)
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
        val stateWithUdpProof = previousState.copy(
            lastUdpSeenAtMillis = receivedAtMillis,
            lastUdpSeenElapsedMillis = receivedAtElapsedMillis,
            lastErrorMessage = null
        )
        val incomingWithPresence = incoming.copy(
            connectionState = stateWithUdpProof.copy(
                onlineState = statusAggregator.resolve(
                    state = stateWithUdpProof,
                    nowElapsedMillis = receivedAtElapsedMillis
                )
            ),
            lastSeenAtMillis = receivedAtMillis
        )

        return DeviceSnapshotMerger.merge(
            previous = previous,
            incoming = incomingWithPresence
        )
    }

    private fun updateConnection(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ) {
        _snapshots.update { current ->
            val snapshot = current[deviceUid] ?: return@update current
            current + (deviceUid to snapshot.copy(connectionState = update(snapshot.connectionState)))
        }
    }
}
