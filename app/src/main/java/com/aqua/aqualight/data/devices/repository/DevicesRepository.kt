package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.monitor.DevicePresenceRuntimeMonitor
import com.aqua.aqualight.data.devices.monitor.DeviceStatusAggregator
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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DevicesRepository(
    private val discoveryRepository: DeviceDiscoveryRepository = DeviceDiscoveryRepository(),
    private val registryStore: DeviceRegistryStore = DeviceRegistryStore(),
    private val knownStore: DeviceKnownStore? = null,
    private val runtimeRepository: DeviceRuntimeRepository? = null,
    private val runtimeMetadataReducer: DeviceRuntimeMetadataReducer = DeviceRuntimeMetadataReducer(),
    private val statusAggregator: DeviceStatusAggregator = DeviceStatusAggregator(),
    connectivityObserver: DeviceConnectivityObserver? = null
) {

    private val presenceRuntimeMonitor = DevicePresenceRuntimeMonitor(
        discoveryRepository = discoveryRepository,
        registryStore = registryStore,
        runtimeRepository = runtimeRepository,
        statusAggregator = statusAggregator,
        connectivityObserver = connectivityObserver
    )

    @Volatile
    private var startJob: Job? = null

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = registryStore.snapshots
    val devices: Flow<List<DeviceSnapshot>> = registryStore.devices

    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> =
        registryStore.observeDevice(deviceUid)

    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? =
        registryStore.currentDevice(deviceUid)

    fun currentDevices(): List<DeviceSnapshot> = registryStore.currentDevices()

    suspend fun knownDeviceUids(): Set<DeviceUid> {
        val durableSnapshots = knownStore?.loadSnapshots()

        return (durableSnapshots ?: currentDevices())
            .map { snapshot -> snapshot.deviceUid }
            .toSet()
    }

    fun start(scope: CoroutineScope): Job {
        val activeJob = startJob
        if (activeJob?.isActive == true) return activeJob

        return synchronized(this) {
            val currentJob = startJob
            if (currentJob?.isActive == true) {
                currentJob
            } else {
                scope.launch {
                    val knownDevices = filterIgnoredDevices(
                        knownStore?.loadSnapshots().orEmpty()
                    )
                    if (knownDevices.isNotEmpty()) {
                        registryStore.upsertAll(knownDevices)
                    }

                    val scannerJob = discoveryRepository.start(this)
                    val collectorJob = launch {
                        discoveryRepository.devices.collect { discoveredDevices ->
                            registryStore.upsertAll(
                                filterIgnoredDevices(discoveredDevices)
                            )
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
                    val presenceMonitorJob = presenceRuntimeMonitor.start(this)

                    try {
                        awaitCancellation()
                    } finally {
                        presenceMonitorJob.cancel()
                        runtimeStateJob?.cancel()
                        runtimeMetadataJob?.cancel()
                        collectorJob.cancel()
                        scannerJob.cancel()
                    }
                }.also { job ->
                    startJob = job
                    job.invokeOnCompletion {
                        if (startJob == job) startJob = null
                    }
                }
            }
        }
    }

    suspend fun stopSession() {
        val activeJob = synchronized(this) {
            startJob.also {
                startJob = null
            }
        }

        activeJob?.cancelAndJoin()
        runtimeRepository?.close()
        registryStore.clear()
    }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult =
        discoveryRepository.refreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoveryRepository.refreshForegroundBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        presenceRuntimeMonitor.reevaluateNow(localNetworkAvailable = localNetworkAvailable)
    }

    fun refreshVisibleDevices(localNetworkAvailable: Boolean = true) {
        presenceRuntimeMonitor.refreshVisibleDevices(localNetworkAvailable = localNetworkAvailable)
    }

    fun isLocalNetworkAvailable(): Boolean =
        presenceRuntimeMonitor.isLocalNetworkAvailable()

    fun connectRuntime(deviceUid: DeviceUid): Result<Unit> {
        val runtime = runtimeRepository
            ?: return Result.failure(IllegalStateException("Device runtime is not configured."))
        val snapshot = registryStore.currentDevice(deviceUid)
            ?: return Result.failure(IllegalArgumentException("Device is not registered."))
        return runtime.connect(snapshot)
    }

    fun commandClient(): AqlWsCommandClient? = runtimeRepository?.commandClient()

    fun commandClient(deviceUid: DeviceUid): AqlWsCommandClient? =
        runtimeRepository?.commandClient(deviceUid)

    fun runtimeModules(): DeviceRuntimeModuleProvider? = runtimeRepository?.runtimeModules

    fun runtimeEvents(): SharedFlow<AqlWsEvent>? = runtimeRepository?.events

    suspend fun saveRuntimeToken(deviceUid: DeviceUid, token: String) {
        runtimeRepository?.saveToken(deviceUid = deviceUid, token = token)
    }

    suspend fun clearRuntimeToken(deviceUid: DeviceUid) {
        runtimeRepository?.clearToken(deviceUid)
    }

    suspend fun stageProvisioningSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        val registered = registryStore.upsert(snapshot)
        if (registered.endpoint.hasWebSocketEndpoint) {
            runtimeRepository?.connect(registered)
        }
        return registered
    }

    suspend fun commitProvisioningSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        knownStore?.allowDevice(snapshot.deviceUid)
        val registered = registryStore.upsert(snapshot)
        knownStore?.saveSnapshot(registered)
        if (registered.endpoint.hasWebSocketEndpoint) {
            runtimeRepository?.connect(registered)
        }
        return registered
    }

    suspend fun registerSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        knownStore?.allowDevice(snapshot.deviceUid)
        val registered = registryStore.upsert(snapshot)
        knownStore?.saveSnapshot(registered)
        if (registered.endpoint.hasWebSocketEndpoint) {
            runtimeRepository?.connect(registered)
        }
        return registered
    }

    suspend fun registerSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        val snapshotList = snapshots.toList()
        snapshotList.forEach { snapshot ->
            knownStore?.allowDevice(snapshot.deviceUid)
        }
        registryStore.upsertAll(snapshotList)
        knownStore?.saveSnapshots(snapshotList)
        snapshotList
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .forEach { snapshot -> runtimeRepository?.connect(snapshot) }
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
        knownStore?.ignoreDevice(deviceUid)
        runtimeRepository?.clearTokenAsync(deviceUid)
        knownStore?.remove(deviceUid)
        val removed = registryStore.remove(deviceUid)
        runtimeRepository?.close(deviceUid)
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

    private suspend fun filterIgnoredDevices(
        snapshots: Iterable<DeviceSnapshot>
    ): List<DeviceSnapshot> {
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
        val failed = state as? AqlWsConnectionState.Failed ?: return
        val deviceUid = failed.deviceUid ?: return
        applyRuntimeUnavailable(
            deviceUid = deviceUid,
            message = failed.message.ifBlank { "Connection failed." }
        )
    }

    private fun applyRuntimeUnavailable(deviceUid: DeviceUid, message: String? = null) {
        val nowMillis = System.currentTimeMillis()
        registryStore.updateConnectionState(deviceUid) { previous ->
            val clearedRuntimeState = previous.copy(
                lastWsConnectedAtMillis = null,
                lastAuthenticatedAtMillis = null,
                lastErrorMessage = message?.ifBlank { null }
            )
            val resolved = statusAggregator.resolve(
                state = clearedRuntimeState,
                nowMillis = nowMillis
            )
            val visibleState = when {
                resolved == DeviceOnlineState.UNKNOWN && !message.isNullOrBlank() -> DeviceOnlineState.OFFLINE
                else -> resolved
            }
            clearedRuntimeState.copy(onlineState = visibleState)
        }
    }
}
