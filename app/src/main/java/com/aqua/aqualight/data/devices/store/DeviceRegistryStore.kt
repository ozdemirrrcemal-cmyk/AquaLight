package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory canonical registry for Devices V2. */
class DeviceRegistryStore {

    private val _snapshots = MutableStateFlow<Map<DeviceUid, DeviceSnapshot>>(emptyMap())

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = _snapshots.asStateFlow()

    val devices: Flow<List<DeviceSnapshot>> = snapshots.map { byUid ->
        byUid.values.sortedWith(
            compareBy<DeviceSnapshot> { it.title.lowercase() }
                .thenBy { it.deviceUid.value }
        )
    }

    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> = snapshots
        .map { byUid -> byUid[deviceUid] }
        .distinctUntilChanged()

    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? = snapshots.value[deviceUid]

    fun currentDevices(): List<DeviceSnapshot> = snapshots.value.values.sortedWith(
        compareBy<DeviceSnapshot> { it.title.lowercase() }
            .thenBy { it.deviceUid.value }
    )

    fun upsert(snapshot: DeviceSnapshot): DeviceSnapshot {
        var mergedSnapshot = snapshot
        _snapshots.update { current ->
            mergedSnapshot = DeviceSnapshotMerger.merge(
                previous = current[snapshot.deviceUid],
                incoming = snapshot
            )
            current + (snapshot.deviceUid to mergedSnapshot)
        }
        return mergedSnapshot
    }

    /**
     * Applies one authoritative atomic replacement for an already registered device.
     *
     * Runtime metadata publication and invalidation use this path intentionally so the generic merge
     * policy cannot retain fields that the authenticated generation explicitly withdrew.
     */
    fun updateSnapshot(
        deviceUid: DeviceUid,
        transform: (DeviceSnapshot) -> DeviceSnapshot
    ): DeviceSnapshot? {
        var updatedSnapshot: DeviceSnapshot? = null
        _snapshots.update { current ->
            val snapshot = current[deviceUid] ?: return@update current
            val updated = transform(snapshot)
            require(updated.deviceUid == deviceUid) {
                "Authoritative snapshot replacement cannot change deviceUid."
            }
            updatedSnapshot = updated
            current + (deviceUid to updated)
        }
        return updatedSnapshot
    }

    fun upsertAll(devices: Iterable<DeviceSnapshot>) {
        _snapshots.update { current ->
            devices.fold(current) { acc, incoming ->
                val merged = DeviceSnapshotMerger.merge(
                    previous = acc[incoming.deviceUid],
                    incoming = incoming
                )
                acc + (incoming.deviceUid to merged)
            }
        }
    }

    fun updateExistingAll(devices: Iterable<DeviceSnapshot>) {
        _snapshots.update { current ->
            devices.fold(current) { acc, incoming ->
                val previous = acc[incoming.deviceUid] ?: return@fold acc
                val merged = DeviceSnapshotMerger.merge(previous, incoming)
                acc + (incoming.deviceUid to merged)
            }
        }
    }

    fun updateConnectionState(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ): DeviceSnapshot? = updateSnapshot(deviceUid) { snapshot ->
        snapshot.copy(connectionState = update(snapshot.connectionState))
    }

    fun remove(deviceUid: DeviceUid): Boolean {
        val existed = _snapshots.value.containsKey(deviceUid)
        _snapshots.update { current -> current - deviceUid }
        return existed
    }

    fun clear() {
        _snapshots.value = emptyMap()
    }
}
