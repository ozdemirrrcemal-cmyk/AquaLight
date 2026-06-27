package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Canonical Devices V2 repository boundary.
 *
 * UI code should use this class instead of reaching into UDP, BLE or WebSocket layers. At this
 * stage it bridges UDP discovery into a shared in-memory registry. WebSocket runtime and BLE
 * provisioning will plug into the same registry in later steps.
 */
class DevicesRepository(
    private val discoveryRepository: DeviceDiscoveryRepository = DeviceDiscoveryRepository(),
    private val registryStore: DeviceRegistryStore = DeviceRegistryStore()
) {

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = registryStore.snapshots

    val devices: Flow<List<DeviceSnapshot>> = registryStore.devices

    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> =
        registryStore.observeDevice(deviceUid)

    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? =
        registryStore.currentDevice(deviceUid)

    fun currentDevices(): List<DeviceSnapshot> = registryStore.currentDevices()

    /**
     * Starts UDP discovery and mirrors discovered snapshots into the canonical registry.
     *
     * The returned job is lifecycle-owned by the caller. Cancelling it stops the scanner and the
     * registry collector together.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
        val scannerJob = discoveryRepository.start(this)
        val collectorJob = launch {
            discoveryRepository.devices.collect { discoveredDevices ->
                registryStore.upsertAll(discoveredDevices)
            }
        }

        try {
            awaitCancellation()
        } finally {
            collectorJob.cancel()
            scannerJob.cancel()
        }
    }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult =
        discoveryRepository.refreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoveryRepository.refreshForegroundBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        discoveryRepository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable)
    }

    fun registerSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot =
        registryStore.upsert(snapshot)

    fun registerSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        registryStore.upsertAll(snapshots)
    }

    fun updateConnectionState(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ): DeviceSnapshot? = registryStore.updateConnectionState(deviceUid, update)

    fun forgetDevice(deviceUid: DeviceUid): Boolean = registryStore.remove(deviceUid)

    fun clearInMemoryRegistry() {
        registryStore.clear()
    }
}
