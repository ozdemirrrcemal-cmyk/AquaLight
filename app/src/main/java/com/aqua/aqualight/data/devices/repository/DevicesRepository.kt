package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Canonical Devices V2 repository boundary.
 *
 * UI code should use this class instead of reaching into UDP, BLE or WebSocket layers.
 * UDP discovery, runtime WebSocket state and later BLE provisioning all converge into
 * the same device registry.
 */
class DevicesRepository(
    private val discoveryRepository: DeviceDiscoveryRepository = DeviceDiscoveryRepository(),
    private val registryStore: DeviceRegistryStore = DeviceRegistryStore(),
    private val knownStore: DeviceKnownStore? = null,
    private val runtimeRepository: DeviceRuntimeRepository? = null,
    private val runtimeMetadataReducer: DeviceRuntimeMetadataReducer = DeviceRuntimeMetadataReducer()
) {

    @Volatile
    private var startJob: Job? = null

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
     * Runtime WebSocket state and metadata are also mirrored into the same registry when runtime
     * support is configured by the provider. UDP discovery is treated as LAN presence only; runtime
     * metadata is persisted through DeviceKnownStore and is not erased by later UDP announces.
     */
    fun start(scope: CoroutineScope): Job {
        val activeJob = startJob
        if (activeJob?.isActive == true) {
            return activeJob
        }

        return synchronized(this) {
            val currentJob = startJob
            if (currentJob?.isActive == true) {
                currentJob
            } else {
                scope.launch {
                    val knownDevices = filterIgnoredDevices(knownStore?.loadSnapshots().orEmpty())
                    if (knownDevices.isNotEmpty()) {
                        registryStore.upsertAll(knownDevices)
                    }

                    val scannerJob = discoveryRepository.start(this)
                    val collectorJob = launch {
                        discoveryRepository.devices.collect { discoveredDevices ->
                            registryStore.upsertAll(filterIgnoredDevices(discoveredDevices))
                        }
                    }
                    val runtimeStateJob = runtimeRepository?.let { runtime ->
                        launch {
                            runtime.connectionState.collect { state ->
                                applyRuntimeConnectionState(state)
                            }
                        }
                    }
                    val runtimeMetadataJob = runtimeRepository?.let { runtime ->
                        launch {
                            runtime.events.collect { event ->
                                applyRuntimeMetadataEvent(event)
                            }
                        }
                    }
                    val runtimeReconnectJob = runtimeRepository?.let { runtime ->
                        launch {
                            knownDevices
                                .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
                                .forEach { snapshot ->
                                    runtime.connect(snapshot)
                                }
                        }
                    }

                    try {
                        awaitCancellation()
                    } finally {
                        runtimeReconnectJob?.cancel()
                        runtimeStateJob?.cancel()
                        runtimeMetadataJob?.cancel()
                        collectorJob.cancel()
                        scannerJob.cancel()
                    }
                }.also { job ->
                    startJob = job
                    job.invokeOnCompletion {
                        if (startJob == job) {
                            startJob = null
                        }
                    }
                }
            }
        }
    }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult =
        discoveryRepository.refreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoveryRepository.refreshForegroundBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        discoveryRepository.reevaluatePresence(localNetworkAvailable = localNetworkAvailable)
    }

    fun connectRuntime(deviceUid: DeviceUid): Result<Unit> {
        val runtime = runtimeRepository
            ?: return Result.failure(IllegalStateException("Device runtime is not configured."))

        val snapshot = registryStore.currentDevice(deviceUid)
            ?: return Result.failure(IllegalArgumentException("Device ${deviceUid.value} is not registered."))

        return runtime.connect(snapshot)
    }

    fun commandClient(): AqlWsCommandClient? {
        return runtimeRepository?.commandClient()
    }

    fun commandClient(deviceUid: DeviceUid): AqlWsCommandClient? {
        return runtimeRepository?.commandClient(deviceUid)
    }

    fun runtimeModules(): DeviceRuntimeModuleProvider? {
        return runtimeRepository?.runtimeModules
    }

    fun runtimeEvents(): SharedFlow<AqlWsEvent>? {
        return runtimeRepository?.events
    }

    suspend fun saveRuntimeToken(
        deviceUid: DeviceUid,
        token: String
    ) {
        runtimeRepository?.saveToken(
            deviceUid = deviceUid,
            token = token
        )
    }

    suspend fun clearRuntimeToken(deviceUid: DeviceUid) {
        runtimeRepository?.clearToken(deviceUid)
    }

    suspend fun registerSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        knownStore?.allowDevice(snapshot.deviceUid)
        val registered = registryStore.upsert(snapshot)
        knownStore?.saveSnapshot(registered)
        return registered
    }

    suspend fun registerSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        val snapshotList = snapshots.toList()
        snapshotList.forEach { snapshot ->
            knownStore?.allowDevice(snapshot.deviceUid)
        }
        registryStore.upsertAll(snapshotList)
        knownStore?.saveSnapshots(snapshotList)
    }

    fun updateConnectionState(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ): DeviceSnapshot? = registryStore.updateConnectionState(deviceUid, update)

    suspend fun removeProvisioningRegistration(deviceUid: DeviceUid): Boolean {
        runtimeRepository?.clearTokenAsync(deviceUid)
        val removed = registryStore.remove(deviceUid)
        runtimeRepository?.close(deviceUid)
        knownStore?.remove(deviceUid)
        return removed
    }

    suspend fun forgetDevice(deviceUid: DeviceUid): Boolean {
        runtimeRepository?.clearTokenAsync(deviceUid)
        val removed = registryStore.remove(deviceUid)
        runtimeRepository?.close(deviceUid)
        knownStore?.remove(deviceUid)
        knownStore?.ignoreDevice(deviceUid)
        return removed
    }

    fun clearInMemoryRegistry() {
        registryStore.clear()
    }

    suspend fun clearKnownDevices() {
        knownStore?.clear()
        knownStore?.clearIgnoredDevices()
        registryStore.clear()
    }

    private suspend fun filterIgnoredDevices(snapshots: Iterable<DeviceSnapshot>): List<DeviceSnapshot> {
        val ignoredDeviceUids = knownStore?.ignoredDeviceUidValues().orEmpty()
        if (ignoredDeviceUids.isEmpty()) return snapshots.toList()

        return snapshots.filterNot { snapshot ->
            snapshot.deviceUid.value in ignoredDeviceUids
        }
    }

    private suspend fun applyRuntimeMetadataEvent(event: AqlWsEvent) {
        val message = (event as? AqlWsEvent.Message)
            ?.parsed as? AqlWsIncomingMessage.Response
            ?: return

        val currentSnapshot = registryStore.currentDevice(event.deviceUid) ?: return
        val reduced = runCatching {
            runtimeMetadataReducer.reduce(
                snapshot = currentSnapshot,
                response = message
            )
        }.getOrNull() ?: return

        val registered = registryStore.upsert(reduced)
        knownStore?.saveSnapshot(registered)
    }

    private fun applyRuntimeConnectionState(state: AqlWsConnectionState) {
        when (state) {
            AqlWsConnectionState.Disconnected -> Unit

            is AqlWsConnectionState.Connecting -> {
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.CONNECTING_WS,
                        lastErrorMessage = null
                    )
                }
            }

            is AqlWsConnectionState.Connected -> {
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.CONNECTING_WS,
                        lastWsConnectedAtMillis = state.connectedAtMillis,
                        lastErrorMessage = null
                    )
                }
            }

            is AqlWsConnectionState.Authenticated -> {
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.AUTHENTICATED,
                        lastWsConnectedAtMillis = previous.lastWsConnectedAtMillis
                            ?: state.authenticatedAtMillis,
                        lastAuthenticatedAtMillis = state.authenticatedAtMillis,
                        lastErrorMessage = null
                    )
                }
            }

            is AqlWsConnectionState.AuthRequired -> {
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.AUTH_REQUIRED,
                        lastErrorMessage = state.message.ifBlank { "Authentication required." }
                    )
                }
            }

            is AqlWsConnectionState.Failed -> {
                val deviceUid = state.deviceUid ?: return
                registryStore.updateConnectionState(deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.ERROR,
                        lastErrorMessage = state.message.ifBlank { "WebSocket connection failed." }
                    )
                }
            }
        }
    }
}
