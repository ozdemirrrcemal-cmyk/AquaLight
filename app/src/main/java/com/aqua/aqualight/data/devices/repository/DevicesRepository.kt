package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
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
 * UI code should use this class instead of reaching into UDP, BLE or WebSocket layers.
 * UDP discovery, runtime WebSocket state and later BLE provisioning all converge into
 * the same device registry.
 */
class DevicesRepository(
    private val discoveryRepository: DeviceDiscoveryRepository = DeviceDiscoveryRepository(),
    private val registryStore: DeviceRegistryStore = DeviceRegistryStore(),
    private val knownStore: DeviceKnownStore? = null,
    private val runtimeRepository: DeviceRuntimeRepository? = null
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
     * Runtime WebSocket state is also mirrored into the same registry when runtime support
     * is configured by the provider.
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
                    val knownDevices = knownStore?.loadSnapshots().orEmpty()
                    if (knownDevices.isNotEmpty()) {
                        registryStore.upsertAll(knownDevices)
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

                    val scannerJob = discoveryRepository.start(this)
                    val collectorJob = launch {
                        discoveryRepository.devices.collect { discoveredDevices ->
                            registryStore.upsertAll(discoveredDevices)
                        }
                    }
                    val runtimeStateJob = runtimeRepository?.let { runtime ->
                        launch {
                            runtime.connectionState.collect { state ->
                                applyRuntimeConnectionState(state)
                            }
                        }
                    }

                    try {
                        awaitCancellation()
                    } finally {
                        runtimeReconnectJob?.cancel()
                        runtimeStateJob?.cancel()
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

    fun registerSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        val registered = registryStore.upsert(snapshot)
        knownStore?.saveSnapshot(registered)
        return registered
    }

    fun registerSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        registryStore.upsertAll(snapshots)
        knownStore?.saveSnapshots(snapshots)
    }

    fun updateConnectionState(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ): DeviceSnapshot? = registryStore.updateConnectionState(deviceUid, update)

    fun forgetDevice(deviceUid: DeviceUid): Boolean {
        val removed = registryStore.remove(deviceUid)
        runtimeRepository?.close(deviceUid)
        knownStore?.remove(deviceUid)
        return removed
    }

    fun clearInMemoryRegistry() {
        registryStore.clear()
    }

    fun clearKnownDevices() {
        knownStore?.clear()
        registryStore.clear()
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
