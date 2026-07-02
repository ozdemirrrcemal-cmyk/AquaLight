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
 * Professional replacement for the old presence monitor.
 *
 * UDP discovery only proves LAN visibility and endpoint reachability. It must not downgrade a more
 * authoritative WebSocket/authenticated runtime state or erase runtime metadata that was already
 * resolved for the same deviceUid.
 */
class DevicePresenceSupervisor(
    private val statusAggregator: DeviceStatusAggregator = DeviceStatusAggregator(),
    private val clockMillis: () -> Long = System::currentTimeMillis
) {

    private val _snapshots = MutableStateFlow<Map<DeviceUid, DeviceSnapshot>>(emptyMap())

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = _snapshots.asStateFlow()

    val devices: Flow<List<DeviceSnapshot>> = snapshots.map { byUid ->
        byUid.values.sortedWith(compareBy<DeviceSnapshot> { it.title.lowercase() }.thenBy { it.deviceUid.value })
    }

    fun onDiscoveredDevice(device: AqlDiscoveredDevice) {
        val incoming = device.snapshot
        val uid = incoming.deviceUid
        val now = device.receivedAtMillis

        _snapshots.update { current ->
            val previous = current[uid]
            val merged = mergeDiscovery(previous, incoming, now)
            current + (uid to merged)
        }
    }

    fun markWebSocketConnected(deviceUid: DeviceUid, connectedAtMillis: Long = clockMillis()) {
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.CONNECTING_WS,
                lastWsConnectedAtMillis = connectedAtMillis,
                lastErrorMessage = null
            )
        }
    }

    fun markAuthenticated(deviceUid: DeviceUid, authenticatedAtMillis: Long = clockMillis()) {
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastAuthenticatedAtMillis = authenticatedAtMillis,
                lastWsConnectedAtMillis = previous.lastWsConnectedAtMillis ?: authenticatedAtMillis,
                lastErrorMessage = null
            )
        }
    }

    fun markAuthRequired(deviceUid: DeviceUid, message: String? = null) {
        updateConnection(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.AUTH_REQUIRED,
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

    fun reevaluate(localNetworkAvailable: Boolean = true, nowMillis: Long = clockMillis()) {
        _snapshots.update { current ->
            current.mapValues { (_, snapshot) ->
                val resolved = statusAggregator.resolve(
                    state = snapshot.connectionState,
                    nowMillis = nowMillis,
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
        nowMillis: Long
    ): DeviceSnapshot {
        val previousState = previous?.connectionState ?: DeviceConnectionState()
        val incomingWithPresence = incoming.copy(
            connectionState = previousState.copy(
                onlineState = statusAggregator.resolve(
                    state = previousState.copy(lastUdpSeenAtMillis = nowMillis),
                    nowMillis = nowMillis
                ),
                lastUdpSeenAtMillis = nowMillis,
                lastErrorMessage = null
            ),
            lastSeenAtMillis = nowMillis
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
