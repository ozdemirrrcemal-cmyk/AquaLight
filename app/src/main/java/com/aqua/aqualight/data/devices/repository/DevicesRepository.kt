@file:Suppress("LongParameterList")

package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.discovery.udp.AqlDiscoveryRefreshSender
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeNameStatus
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.monitor.DeviceConnectivityObserver
import com.aqua.aqualight.data.devices.monitor.DeviceElapsedRealtimeClock
import com.aqua.aqualight.data.devices.monitor.DevicePresenceRuntimeMonitor
import com.aqua.aqualight.data.devices.monitor.DeviceStatusAggregator
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPipeline
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.store.DeviceKnownStore
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import com.aqua.aqualight.data.devices.store.DeviceSnapshotMerger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class DevicesRepository(
    private val discoveryRepository: DeviceDiscoveryRepository = DeviceDiscoveryRepository(),
    private val registryStore: DeviceRegistryStore = DeviceRegistryStore(),
    private val knownStore: DeviceKnownStore? = null,
    private val runtimeRepository: DeviceRuntimeRepository? = null,
    private val statusAggregator: DeviceStatusAggregator = DeviceStatusAggregator(),
    connectivityObserver: DeviceConnectivityObserver? = null,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = DeviceElapsedRealtimeClock::nowMillis
) {

    private val ownerScopedResources = ConcurrentHashMap.newKeySet<AutoCloseable>()

    private val _typedRuntimeEvents = MutableSharedFlow<DeviceRuntimeTypedEvent>(
        extraBufferCapacity = TYPED_RUNTIME_EVENT_BUFFER_CAPACITY
    )
    private val typedRuntimeEvents: SharedFlow<DeviceRuntimeTypedEvent> = _typedRuntimeEvents

    private val presenceRuntimeMonitor = DevicePresenceRuntimeMonitor(
        discoveryRepository = discoveryRepository,
        registryStore = registryStore,
        runtimeRepository = runtimeRepository,
        statusAggregator = statusAggregator,
        connectivityObserver = connectivityObserver,
        elapsedRealtimeMillis = elapsedRealtimeMillis
    )

    @Volatile
    private var startJob: Job? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val snapshots: StateFlow<Map<DeviceUid, DeviceSnapshot>> = registryStore.snapshots
    val devices: Flow<List<DeviceSnapshot>> = registryStore.devices

    fun observeDevice(deviceUid: DeviceUid): Flow<DeviceSnapshot?> =
        registryStore.observeDevice(deviceUid)

    fun currentDevice(deviceUid: DeviceUid): DeviceSnapshot? =
        registryStore.currentDevice(deviceUid)

    fun currentDevices(): List<DeviceSnapshot> = registryStore.currentDevices()

    fun start(scope: CoroutineScope): Job {
        val activeJob = startJob
        if (activeJob?.isActive == true) return activeJob
        return synchronized(this) {
            val currentJob = startJob
            if (currentJob?.isActive == true) {
                currentJob
            } else {
                scope.launch {
                    _ready.value = false
                    restoreKnownDevices()

                    val scannerJob = discoveryRepository.start(this)
                    val collectorJob = launch {
                        discoveryRepository.devices.collect { discoveredDevices ->
                            registryStore.updateExistingAll(filterIgnoredDevices(discoveredDevices))
                        }
                    }
                    val runtimeStateJob = runtimeRepository?.let { runtime ->
                        launch {
                            runtime.connectionState.collect(::applyRuntimeConnectionState)
                        }
                    }
                    val runtimeEventsJob = runtimeRepository?.let { runtime ->
                        launch {
                            runtime.events.collect(::applyRuntimeEvent)
                        }
                    }
                    val typedEventPipeline = runtimeRepository?.let { runtime ->
                        DeviceRuntimeEventPipeline(runtime, this)
                    }
                    val typedEventsJob = typedEventPipeline?.let { pipeline ->
                        launch {
                            pipeline.events.collect(::applyTypedRuntimeEvent)
                        }
                    }
                    val presenceMonitorJob = presenceRuntimeMonitor.start(this)
                    _ready.value = true

                    try {
                        awaitCancellation()
                    } finally {
                        _ready.value = false
                        presenceMonitorJob.cancel()
                        typedEventsJob?.cancel()
                        typedEventPipeline?.shutdown()
                        runtimeStateJob?.cancel()
                        runtimeEventsJob?.cancel()
                        collectorJob.cancel()
                        scannerJob.cancel()
                    }
                }.also { job ->
                    startJob = job
                    job.invokeOnCompletion {
                        if (startJob == job) startJob = null
                        _ready.value = false
                    }
                }
            }
        }
    }

    private suspend fun restoreKnownDevices() {
        val knownDevices = filterIgnoredDevices(knownStore?.loadSnapshots().orEmpty())
            .map(DeviceRuntimeMetadataProjector::invalidate)
        if (knownDevices.isNotEmpty()) {
            knownDevices.forEach { snapshot ->
                runtimeRepository?.activate(snapshot.deviceUid)
            }
            registryStore.upsertAll(knownDevices)
        }
    }

    fun stop() {
        val job = detachStartJob()
        _ready.value = false
        job?.cancel()
        try {
            closeOwnerScopedResources()
        } finally {
            runtimeRepository?.close()
            registryStore.clear()
        }
    }

    suspend fun shutdown() {
        val job = detachStartJob()
        _ready.value = false
        job?.cancelAndJoin()
        try {
            closeOwnerScopedResources()
        } finally {
            runtimeRepository?.shutdown()
            registryStore.clear()
        }
    }

    /** Closes owner-scoped adapters before their repository/runtime identity is retired. */
    internal fun registerOwnerScopedResource(resource: AutoCloseable) {
        ownerScopedResources += resource
    }

    private fun closeOwnerScopedResources() {
        val resources = ownerScopedResources.toList()
        ownerScopedResources.removeAll(resources.toSet())
        var firstFailure: Throwable? = null
        resources.forEach { resource ->
            runCatching(resource::close).exceptionOrNull()?.let { error ->
                if (firstFailure == null) firstFailure = error
                else firstFailure?.addSuppressed(error)
            }
        }
        firstFailure?.let { throw it }
    }

    private fun detachStartJob(): Job? = synchronized(this) {
        startJob.also { startJob = null }
    }

    suspend fun refreshNow(): AqlDiscoveryRefreshSender.SendResult = discoveryRepository.refreshNow()

    suspend fun refreshForegroundBurst(): List<AqlDiscoveryRefreshSender.SendResult> =
        discoveryRepository.refreshForegroundBurst()

    fun reevaluatePresence(localNetworkAvailable: Boolean = true) {
        presenceRuntimeMonitor.reevaluateNow(localNetworkAvailable)
    }

    fun refreshVisibleDevices(localNetworkAvailable: Boolean = true) {
        presenceRuntimeMonitor.refreshVisibleDevices(localNetworkAvailable)
    }

    fun setAppForeground(isForeground: Boolean) {
        presenceRuntimeMonitor.setAppForeground(isForeground)
    }

    fun isLocalNetworkAvailable(): Boolean = presenceRuntimeMonitor.isLocalNetworkAvailable()

    fun connectRuntime(deviceUid: DeviceUid): Result<Unit> = runCatching {
        val runtime = checkNotNull(runtimeRepository) {
            "Device runtime is not configured."
        }
        val snapshot = requireNotNull(registryStore.currentDevice(deviceUid)) {
            "Device is not registered."
        }
        runtime.connect(snapshot).getOrThrow()
    }

    fun replaceRuntimeAfterControlFailure(deviceUid: DeviceUid): Result<Unit> = runCatching {
        val runtime = checkNotNull(runtimeRepository) { "Device runtime is not configured." }
        val snapshot = requireNotNull(registryStore.currentDevice(deviceUid)) {
            "Device is not registered."
        }
        invalidateRuntimeMetadata(deviceUid)
        runtime.reconnectAfterNetworkRestore(snapshot).getOrThrow()
    }

    fun commandClient(): AqlWsCommandClient? = runtimeRepository?.commandClient()

    fun commandClient(deviceUid: DeviceUid): AqlWsCommandClient? =
        runtimeRepository?.commandClient(deviceUid)

    fun runtimeModules(): DeviceRuntimeModuleProvider? = runtimeRepository?.runtimeModules

    fun runtimeEvents(): SharedFlow<AqlWsEvent>? = runtimeRepository?.events

    fun typedRuntimeEvents(): SharedFlow<DeviceRuntimeTypedEvent> = typedRuntimeEvents

    fun runtimeConnectionStates(): SharedFlow<AqlWsConnectionState>? =
        runtimeRepository?.connectionState

    fun currentRuntimeConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        runtimeRepository?.currentConnectionState(deviceUid)

    fun recordControlProof(deviceUid: DeviceUid): DeviceSnapshot? =
        recordRuntimeProof(deviceUid = deviceUid, isControlProof = true)

    suspend fun saveRuntimeToken(deviceUid: DeviceUid, token: String) {
        runtimeRepository?.saveToken(deviceUid, token)
    }

    suspend fun clearRuntimeToken(deviceUid: DeviceUid) {
        runtimeRepository?.clearToken(deviceUid)
    }

    suspend fun stageProvisioningSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        runtimeRepository?.activate(snapshot.deviceUid)
        val registered = registerUntrustedSnapshot(snapshot)
        if (registered.endpoint.hasWebSocketEndpoint) runtimeRepository?.connect(registered)
        return registered
    }

    suspend fun commitProvisioningSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot =
        persistThenRegister(snapshot, preserveCurrentRuntimeTrust = true)

    suspend fun registerSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot =
        persistThenRegister(snapshot, preserveCurrentRuntimeTrust = false)

    suspend fun registerSnapshots(snapshots: Iterable<DeviceSnapshot>) {
        val mergedSnapshots = mergeIncomingSnapshots(snapshots)
        if (mergedSnapshots.isEmpty()) return
        mergedSnapshots.forEach { snapshot -> runtimeRepository?.activate(snapshot.deviceUid) }
        knownStore?.saveSnapshots(mergedSnapshots)
        val registeredSnapshots = mergedSnapshots.map(::registerUntrustedSnapshot)
        registeredSnapshots
            .filter { snapshot -> snapshot.endpoint.hasWebSocketEndpoint }
            .forEach { snapshot -> runtimeRepository?.connect(snapshot) }
    }

    fun updateConnectionState(
        deviceUid: DeviceUid,
        update: (DeviceConnectionState) -> DeviceConnectionState
    ): DeviceSnapshot? = registryStore.updateConnectionState(deviceUid, update)

    suspend fun removeProvisioningRegistration(deviceUid: DeviceUid): Boolean {
        val rollbackSnapshot = registryStore.currentDevice(deviceUid)
        knownStore?.remove(deviceUid)
        try {
            runtimeRepository?.clearToken(deviceUid)
        } catch (error: Throwable) {
            throw rollbackKnownDeviceOrWrap(
                "remove provisioning registration",
                error,
                rollbackSnapshot
            )
        }
        runtimeRepository?.retire(deviceUid)
        return registryStore.remove(deviceUid)
    }

    suspend fun forgetDevice(deviceUid: DeviceUid): Boolean {
        val rollbackSnapshot = registryStore.currentDevice(deviceUid)
        knownStore?.forgetDevice(deviceUid)
        try {
            runtimeRepository?.clearToken(deviceUid)
        } catch (error: Throwable) {
            throw rollbackKnownDeviceOrWrap("forget device", error, rollbackSnapshot)
        }
        runtimeRepository?.retire(deviceUid)
        return registryStore.remove(deviceUid)
    }

    fun clearInMemoryRegistry() {
        registryStore.clear()
    }

    suspend fun clearKnownDevices() {
        val deviceUids = registryStore.currentDevices().map(DeviceSnapshot::deviceUid)
        knownStore?.clearOwnerData()
        deviceUids.forEach { deviceUid -> runtimeRepository?.retire(deviceUid) }
        registryStore.clear()
    }

    private suspend fun persistThenRegister(
        snapshot: DeviceSnapshot,
        preserveCurrentRuntimeTrust: Boolean
    ): DeviceSnapshot {
        val merged = DeviceSnapshotMerger.merge(
            previous = registryStore.currentDevice(snapshot.deviceUid),
            incoming = snapshot
        )
        runtimeRepository?.activate(merged.deviceUid)
        knownStore?.saveSnapshot(merged)
        val preserveTrust = preserveCurrentRuntimeTrust &&
            runtimeRepository?.isCurrentValidatedMetadata(merged) == true
        val registered = if (preserveTrust) {
            registryStore.updateSnapshot(merged.deviceUid) { merged }
                ?: registryStore.upsert(merged)
        } else {
            registerUntrustedSnapshot(merged)
        }
        if (registered.endpoint.hasWebSocketEndpoint) runtimeRepository?.connect(registered)
        return registered
    }

    private fun registerUntrustedSnapshot(snapshot: DeviceSnapshot): DeviceSnapshot {
        val merged = DeviceSnapshotMerger.merge(
            previous = registryStore.currentDevice(snapshot.deviceUid),
            incoming = snapshot
        )
        val untrusted = DeviceRuntimeMetadataProjector.invalidate(merged)
        return registryStore.updateSnapshot(snapshot.deviceUid) { untrusted }
            ?: registryStore.upsert(untrusted)
    }

    private fun mergeIncomingSnapshots(snapshots: Iterable<DeviceSnapshot>): List<DeviceSnapshot> {
        val mergedByUid = registryStore.currentDevices()
            .associateBy(DeviceSnapshot::deviceUid)
            .toMutableMap()
        val incomingUids = linkedSetOf<DeviceUid>()
        snapshots.forEach { incoming ->
            incomingUids += incoming.deviceUid
            mergedByUid[incoming.deviceUid] = DeviceSnapshotMerger.merge(
                previous = mergedByUid[incoming.deviceUid],
                incoming = incoming
            )
        }
        return incomingUids.mapNotNull(mergedByUid::get)
    }

    private suspend fun rollbackKnownDeviceOrWrap(
        operation: String,
        originalError: Throwable,
        rollbackSnapshot: DeviceSnapshot?
    ): Throwable {
        val durableStore = knownStore
        if (rollbackSnapshot == null || durableStore == null) {
            return DevicePersistenceTransactionException(
                "$operation failed and no durable rollback snapshot was available.",
                originalError
            )
        }
        val rollbackError = runCatching {
            durableStore.saveSnapshot(rollbackSnapshot)
        }.exceptionOrNull()
        return DevicePersistenceTransactionException(
            if (rollbackError == null) {
                "$operation failed; durable known-device state was restored."
            } else {
                "$operation failed and durable known-device rollback also failed."
            },
            originalError,
            rollbackError
        )
    }

    private suspend fun filterIgnoredDevices(
        snapshots: Iterable<DeviceSnapshot>
    ): List<DeviceSnapshot> {
        val ignoredDeviceUids = knownStore?.ignoredDeviceUidValues().orEmpty()
        return if (ignoredDeviceUids.isEmpty()) {
            snapshots.toList()
        } else {
            snapshots.filterNot { snapshot -> snapshot.deviceUid.value in ignoredDeviceUids }
        }
    }

    private suspend fun applyRuntimeEvent(event: AqlWsEvent) {
        when (event) {
            is AqlWsEvent.Message -> {
                recordRuntimeProof(
                    deviceUid = event.deviceUid,
                    isControlProof = (event.parsed as? AqlWsIncomingMessage.Response)?.ok == true
                )
                applyRuntimeMetadataMessage(event)
            }
            is AqlWsEvent.Closed -> applyRuntimeClosed(event)
            is AqlWsEvent.Opened,
            is AqlWsEvent.Authenticated,
            is AqlWsEvent.Failure -> Unit
        }
    }

    private suspend fun applyTypedRuntimeEvent(event: DeviceRuntimeTypedEvent) {
        event.deviceNameStatusOrNull()?.let { nameStatus ->
            val updated = registryStore.updateSnapshot(event.deviceUid) { current ->
                current.copy(
                    identity = current.identity.copy(
                        displayName = nameStatus.productDisplayName,
                        customName = nameStatus.customName
                    )
                )
            }
            updated?.let { snapshot -> knownStore?.saveSnapshot(snapshot) }
        }
        _typedRuntimeEvents.emit(event)
    }

    private fun DeviceRuntimeTypedEvent.deviceNameStatusOrNull(): DeviceRuntimeNameStatus? =
        takeIf { event -> event.type == DeviceRuntimeTypedEvent.Type.DEVICE_STATUS_CHANGED }
            ?.let { event ->
                when (val payload = event.payload) {
                    is DeviceRuntimeEventPayload.CommandResult ->
                        payload.result.optJSONObject("status")
                    is DeviceRuntimeEventPayload.Snapshot ->
                        payload.data.optJSONObject("status") ?: payload.data
                }
            }
            ?.let { statusData ->
                DeviceRuntimeNameStatusParser.parse(
                    statusData,
                    "device.status.changed.data.status"
                ).getOrNull()
            }

    private suspend fun applyRuntimeMetadataMessage(event: AqlWsEvent.Message) {
        val response = event.parsed as? AqlWsIncomingMessage.Response
        val update = response?.let { message ->
            runtimeRepository?.processMetadataResponse(event.deviceUid, message)
        } ?: DeviceRuntimeMetadataUpdate.Unmatched

        when (update) {
            DeviceRuntimeMetadataUpdate.Unmatched,
            is DeviceRuntimeMetadataUpdate.Collecting -> Unit
            is DeviceRuntimeMetadataUpdate.Ready -> {
                val registered = registryStore.updateSnapshot(event.deviceUid) { current ->
                    DeviceRuntimeMetadataProjector.applyReady(current, update.state)
                }
                registered?.let { snapshot -> knownStore?.saveSnapshot(snapshot) }
            }
            is DeviceRuntimeMetadataUpdate.Rejected -> {
                invalidateRuntimeMetadata(event.deviceUid)
            }
        }
    }

    private fun invalidateRuntimeMetadata(deviceUid: DeviceUid): DeviceSnapshot? =
        registryStore.updateSnapshot(deviceUid, DeviceRuntimeMetadataProjector::invalidate)

    private fun recordRuntimeProof(
        deviceUid: DeviceUid,
        isControlProof: Boolean
    ): DeviceSnapshot? {
        val nowWallMillis = wallClockMillis()
        val nowElapsedMillis = elapsedRealtimeMillis()
        return registryStore.updateConnectionState(deviceUid) { previous ->
            previous.copy(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastRuntimeMessageAtMillis = nowWallMillis,
                lastRuntimeMessageElapsedMillis = nowElapsedMillis,
                lastControlProofAtMillis = if (isControlProof) {
                    nowWallMillis
                } else {
                    previous.lastControlProofAtMillis
                },
                lastControlProofElapsedMillis = if (isControlProof) {
                    nowElapsedMillis
                } else {
                    previous.lastControlProofElapsedMillis
                },
                lastErrorMessage = null
            )
        }
    }

    private fun applyRuntimeClosed(event: AqlWsEvent.Closed) {
        val currentState = runtimeRepository?.currentConnectionState(event.deviceUid)
        if (RuntimeClosedEventPolicy.shouldClearRuntimeProof(currentState)) {
            applyRuntimeUnavailable(event.deviceUid)
        }
    }

    @Suppress("LongMethod")
    private fun applyRuntimeConnectionState(state: AqlWsConnectionState) {
        val nowElapsedMillis = elapsedRealtimeMillis()
        when (state) {
            AqlWsConnectionState.Disconnected -> Unit
            is AqlWsConnectionState.Connecting -> {
                invalidateRuntimeMetadata(state.deviceUid)
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = presenceRuntimeMonitor.visibleStateDuringForegroundVerification(
                            previous.onlineState,
                            DeviceOnlineState.CONNECTING_WS
                        ),
                        lastWsConnectedAtMillis = null,
                        lastWsConnectedElapsedMillis = null,
                        lastAuthenticatedAtMillis = null,
                        lastAuthenticatedElapsedMillis = null,
                        lastRuntimeMessageAtMillis = null,
                        lastRuntimeMessageElapsedMillis = null,
                        lastControlProofAtMillis = null,
                        lastControlProofElapsedMillis = null,
                        lastErrorMessage = null
                    )
                }
            }
            is AqlWsConnectionState.Connected -> {
                invalidateRuntimeMetadata(state.deviceUid)
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = presenceRuntimeMonitor.visibleStateDuringForegroundVerification(
                            previous.onlineState,
                            DeviceOnlineState.CONNECTING_WS
                        ),
                        lastWsConnectedAtMillis = state.connectedAtMillis,
                        lastWsConnectedElapsedMillis = nowElapsedMillis,
                        lastAuthenticatedAtMillis = null,
                        lastAuthenticatedElapsedMillis = null,
                        lastRuntimeMessageAtMillis = null,
                        lastRuntimeMessageElapsedMillis = null,
                        lastControlProofAtMillis = null,
                        lastControlProofElapsedMillis = null,
                        lastErrorMessage = null
                    )
                }
            }
            is AqlWsConnectionState.Authenticated -> {
                invalidateRuntimeMetadata(state.deviceUid)
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.AUTHENTICATED,
                        lastAuthenticatedAtMillis = state.authenticatedAtMillis,
                        lastAuthenticatedElapsedMillis = nowElapsedMillis,
                        lastRuntimeMessageAtMillis = null,
                        lastRuntimeMessageElapsedMillis = null,
                        lastControlProofAtMillis = null,
                        lastControlProofElapsedMillis = null,
                        lastErrorMessage = null
                    )
                }
            }
            is AqlWsConnectionState.AuthRequired -> {
                invalidateRuntimeMetadata(state.deviceUid)
                registryStore.updateConnectionState(state.deviceUid) { previous ->
                    previous.copy(
                        onlineState = DeviceOnlineState.AUTH_REQUIRED,
                        lastAuthenticatedAtMillis = null,
                        lastAuthenticatedElapsedMillis = null,
                        lastRuntimeMessageAtMillis = null,
                        lastRuntimeMessageElapsedMillis = null,
                        lastControlProofAtMillis = null,
                        lastControlProofElapsedMillis = null,
                        lastErrorMessage = state.message.trim().takeIf(String::isNotBlank)
                    )
                }
            }
            is AqlWsConnectionState.Failed -> state.deviceUid?.let { deviceUid ->
                applyRuntimeUnavailable(
                    deviceUid,
                    state.message.ifBlank { "Connection failed." }
                )
            }
        }
    }

    private fun applyRuntimeUnavailable(deviceUid: DeviceUid, message: String? = null) {
        invalidateRuntimeMetadata(deviceUid)
        val nowElapsedMillis = elapsedRealtimeMillis()
        registryStore.updateConnectionState(deviceUid) { previous ->
            val clearedRuntimeState = previous.copy(
                lastWsConnectedAtMillis = null,
                lastWsConnectedElapsedMillis = null,
                lastAuthenticatedAtMillis = null,
                lastAuthenticatedElapsedMillis = null,
                lastRuntimeMessageAtMillis = null,
                lastRuntimeMessageElapsedMillis = null,
                lastControlProofAtMillis = null,
                lastControlProofElapsedMillis = null,
                lastErrorMessage = message?.ifBlank { null }
            )
            val resolved = statusAggregator.resolve(
                state = clearedRuntimeState,
                nowElapsedMillis = nowElapsedMillis,
                localNetworkAvailable = presenceRuntimeMonitor.isLocalNetworkAvailable()
            )
            val visibleState = if (
                resolved == DeviceOnlineState.UNKNOWN && !message.isNullOrBlank()
            ) {
                DeviceOnlineState.OFFLINE
            } else {
                resolved
            }
            clearedRuntimeState.copy(onlineState = visibleState)
        }
    }
}

private const val TYPED_RUNTIME_EVENT_BUFFER_CAPACITY = 256

class DevicePersistenceTransactionException(
    message: String,
    cause: Throwable,
    val rollbackError: Throwable? = null
) : IllegalStateException(message, cause)
