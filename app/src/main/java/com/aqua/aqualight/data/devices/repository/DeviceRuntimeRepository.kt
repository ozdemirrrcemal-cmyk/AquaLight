package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGeneration
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataReduction
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeSyncCoordinator
import com.aqua.aqualight.data.devices.runtime.ws.AqlPrivateLanEndpoint
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTokenProvider
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsTransport
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface DeviceRuntimeMetadataUpdate {
    data object Unmatched : DeviceRuntimeMetadataUpdate
    data class Collecting(
        val state: DeviceRuntimeMetadataGenerationState.Collecting
    ) : DeviceRuntimeMetadataUpdate
    data class Ready(
        val state: DeviceRuntimeMetadataGenerationState.Ready
    ) : DeviceRuntimeMetadataUpdate
    data class Rejected(
        val state: DeviceRuntimeMetadataGenerationState.Rejected
    ) : DeviceRuntimeMetadataUpdate
}

/**
 * Owner-scoped runtime session orchestrator.
 *
 * The public and private operations intentionally remain together because session ownership,
 * authenticated bootstrap correlation, token lifecycle and socket teardown share one lock domain.
 */
@Suppress("TooManyFunctions")
class DeviceRuntimeRepository(
    private val tokenProvider: AqlWsTokenProvider? = null,
    private val wsClientFactory: (AqlWsTokenProvider?) -> AqlWsTransport = { provider ->
        AqlWsClient(tokenProvider = provider)
    },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {

    private class RuntimeSession(
        val deviceUid: DeviceUid,
        val wsClient: AqlWsTransport,
        val commandClient: AqlWsCommandClient,
        val sessionJob: CompletableJob,
        val sessionScope: CoroutineScope,
        @Volatile var endpointUrl: String? = null
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)
        private val collectorJobs = CopyOnWriteArrayList<Job>()

        fun track(job: Job) {
            if (closed.get()) {
                job.cancel()
                return
            }
            collectorJobs += job
            if (closed.get() && collectorJobs.remove(job)) job.cancel()
        }

        override fun close() {
            val jobs = synchronized(this) { beginCloseLocked() } ?: return
            jobs.forEach(Job::cancel)
            sessionJob.cancel()
            wsClient.close()
        }

        suspend fun shutdown() {
            val jobs = synchronized(this) { beginCloseLocked() } ?: return
            jobs.forEach(Job::cancel)
            sessionJob.cancel()
            try {
                wsClient.shutdown()
            } finally {
                jobs.joinAll()
                sessionJob.join()
            }
        }

        private fun beginCloseLocked(): List<Job>? {
            if (!closed.compareAndSet(false, true)) return null
            return collectorJobs.toList().also { collectorJobs.clear() }
        }
    }

    private val lifecycleLock = Any()
    private val repositoryJob = SupervisorJob()
    private val tokenLifecycleMutex = Mutex()
    private val sessions = ConcurrentHashMap<DeviceUid, RuntimeSession>()
    private val retiredDeviceUids = ConcurrentHashMap.newKeySet<DeviceUid>()
    private val metadataTimeoutJobs = ConcurrentHashMap<DeviceUid, Job>()

    internal val metadataBootstrapCoordinator = DeviceRuntimeMetadataBootstrapCoordinator()

    val runtimeModules: DeviceRuntimeModuleProvider = DeviceRuntimeModuleProvider { deviceUid ->
        sessions[deviceUid]?.commandClient
    }

    private val timeSyncCoordinator = DeviceTimeSyncCoordinator(repository = runtimeModules.time)

    private val _connectionState = MutableSharedFlow<AqlWsConnectionState>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val connectionState: SharedFlow<AqlWsConnectionState> = _connectionState.asSharedFlow()

    private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

    @Volatile
    private var lastActiveDeviceUid: DeviceUid? = null

    @Volatile
    private var closed: Boolean = false

    fun connect(snapshot: DeviceSnapshot): Result<Unit> {
        val deviceUid = snapshot.deviceUid
        val session = runCatching {
            synchronized(lifecycleLock) {
                check(!closed) { "Device runtime repository is closed." }
                check(deviceUid !in retiredDeviceUids) {
                    "Device runtime is retired and cannot be reopened without explicit registration."
                }
                sessions[deviceUid] ?: createSession(deviceUid).also { created ->
                    sessions[deviceUid] = created
                    observeSession(created)
                }.also { lastActiveDeviceUid = deviceUid }
            }
        }.getOrElse { return Result.failure(it) }

        return synchronized(session) {
            if (!isCurrentSession(session)) {
                return@synchronized Result.failure(
                    IllegalStateException("Device runtime session is no longer active.")
                )
            }
            lastActiveDeviceUid = deviceUid
            val endpointUrl = AqlPrivateLanEndpoint.route(deviceUid, snapshot.endpoint)?.url
            val endpointMatches = session.endpointUrl == endpointUrl
            val currentState = session.wsClient.connectionState.value
            if (!RuntimeConnectionReusePolicy.shouldReconnect(currentState, deviceUid, endpointMatches)) {
                return@synchronized ensureCurrentMetadataBootstrap(
                    session = session,
                    snapshot = snapshot,
                    connectionState = currentState
                )
            }
            session.endpointUrl = endpointUrl
            session.wsClient.connect(deviceUid = deviceUid, endpoint = snapshot.endpoint)
        }
    }

    fun reconnectAfterNetworkRestore(snapshot: DeviceSnapshot): Result<Unit> {
        val detached = synchronized(lifecycleLock) {
            if (closed || snapshot.deviceUid in retiredDeviceUids) return@synchronized null
            sessions.remove(snapshot.deviceUid).also {
                if (lastActiveDeviceUid == snapshot.deviceUid) lastActiveDeviceUid = null
            }
        }
        cancelMetadataTimeout(snapshot.deviceUid)
        detached?.close()
        metadataBootstrapCoordinator.clear(snapshot.deviceUid)
        return connect(snapshot)
    }

    fun activate(deviceUid: DeviceUid) {
        synchronized(lifecycleLock) {
            check(!closed) { "Device runtime repository is closed." }
            retiredDeviceUids.remove(deviceUid)
        }
    }

    fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        sessions[deviceUid]?.wsClient?.connectionState?.value

    internal fun isCurrentValidatedMetadata(snapshot: DeviceSnapshot): Boolean {
        val ready = metadataBootstrapCoordinator.currentState(snapshot.deviceUid) as?
            DeviceRuntimeMetadataGenerationState.Ready
        return snapshot.hasValidatedRuntimeMetadata &&
            ready?.generation?.value == snapshot.runtimeMetadataGeneration
    }

    internal fun processMetadataResponse(
        deviceUid: DeviceUid,
        response: AqlWsIncomingMessage.Response
    ): DeviceRuntimeMetadataUpdate {
        return when (val processing = metadataBootstrapCoordinator.process(deviceUid, response)) {
            DeviceRuntimeMetadataBootstrapProcessing.Unmatched -> DeviceRuntimeMetadataUpdate.Unmatched
            is DeviceRuntimeMetadataBootstrapProcessing.Reduced ->
                mapMetadataReduction(deviceUid, processing.reduction)
        }
    }

    private fun ensureCurrentMetadataBootstrap(
        session: RuntimeSession,
        snapshot: DeviceSnapshot,
        connectionState: AqlWsConnectionState
    ): Result<Unit> {
        val metadataState = metadataBootstrapCoordinator.currentState(snapshot.deviceUid)
        return when {
            isCurrentValidatedMetadata(snapshot) -> Result.success(Unit)
            metadataState is DeviceRuntimeMetadataGenerationState.Collecting -> Result.success(Unit)
            connectionState !is AqlWsConnectionState.Authenticated -> Result.success(Unit)
            sendAuthenticatedBootstrap(session) -> Result.success(Unit)
            else -> Result.failure(
                IllegalStateException("Authenticated metadata bootstrap could not be restarted.")
            )
        }
    }

    fun disconnectForLocalNetworkLoss() {
        val activeSessions = synchronized(lifecycleLock) {
            if (closed) return
            lastActiveDeviceUid = null
            sessions.values.toList()
        }
        activeSessions.forEach { session ->
            rejectActiveGeneration(session.deviceUid, "localNetwork")
            synchronized(session) {
                session.wsClient.disconnect(reason = LOCAL_NETWORK_UNAVAILABLE_REASON)
            }
        }
    }

    fun commandClient(): AqlWsCommandClient? {
        val activeUid = lastActiveDeviceUid ?: return null
        return sessions[activeUid]?.commandClient
    }

    fun commandClient(deviceUid: DeviceUid): AqlWsCommandClient? =
        sessions[deviceUid]?.commandClient

    suspend fun saveToken(deviceUid: DeviceUid, token: String) {
        val provider = tokenProvider ?: return
        tokenLifecycleMutex.withLock {
            ensureOpen()
            provider.saveToken(deviceUid = deviceUid, token = token)
            ensureOpen()
        }
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        val provider = tokenProvider ?: return
        tokenLifecycleMutex.withLock {
            ensureOpen()
            provider.clearToken(deviceUid)
            ensureOpen()
        }
    }

    suspend fun retire(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        cancelMetadataTimeout(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.shutdown()
    }

    fun close(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        cancelMetadataTimeout(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.close()
    }

    override fun close() {
        val activeSessions = beginRepositoryClose() ?: return
        cancelAllMetadataTimeouts()
        metadataBootstrapCoordinator.clearAll()
        repositoryJob.cancel()
        activeSessions.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.close()
        }
    }

    suspend fun shutdown() {
        val activeSessions = beginRepositoryClose()
        cancelAllMetadataTimeouts()
        metadataBootstrapCoordinator.clearAll()
        repositoryJob.cancel()
        activeSessions?.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.shutdown()
        }
        tokenLifecycleMutex.withLock { }
        repositoryJob.join()
    }

    private fun mapMetadataReduction(
        deviceUid: DeviceUid,
        reduction: DeviceRuntimeMetadataReduction
    ): DeviceRuntimeMetadataUpdate = when (reduction) {
        is DeviceRuntimeMetadataReduction.IgnoredStale -> DeviceRuntimeMetadataUpdate.Unmatched
        is DeviceRuntimeMetadataReduction.Rejected -> {
            cancelMetadataTimeout(deviceUid)
            disconnectMetadataFailure(deviceUid)
            DeviceRuntimeMetadataUpdate.Rejected(reduction.state)
        }
        is DeviceRuntimeMetadataReduction.Accepted -> when (val state = reduction.state) {
            is DeviceRuntimeMetadataGenerationState.Collecting ->
                DeviceRuntimeMetadataUpdate.Collecting(state)
            is DeviceRuntimeMetadataGenerationState.Ready -> {
                cancelMetadataTimeout(deviceUid)
                validateReadyState(state)
            }
            is DeviceRuntimeMetadataGenerationState.Rejected -> {
                cancelMetadataTimeout(deviceUid)
                disconnectMetadataFailure(deviceUid)
                DeviceRuntimeMetadataUpdate.Rejected(state)
            }
        }
    }

    private fun validateReadyState(
        state: DeviceRuntimeMetadataGenerationState.Ready
    ): DeviceRuntimeMetadataUpdate {
        return when (val validation = AqlCommercialDeviceCatalog.validate(state.metadata)) {
            is AqlCommercialCatalogValidation.Valid -> {
                timeSyncCoordinator.syncPhoneNowIfNeeded(state.deviceUid)
                DeviceRuntimeMetadataUpdate.Ready(state)
            }
            is AqlCommercialCatalogValidation.Invalid -> {
                val rejected = checkNotNull(
                    metadataBootstrapCoordinator.reject(
                        deviceUid = state.deviceUid,
                        generation = state.generation,
                        code = DeviceRuntimeMetadataFailureCode.CATALOG_VALIDATION_FAILED,
                        field = "${validation.failure.code}:${validation.failure.field}"
                    )
                )
                disconnectMetadataFailure(state.deviceUid)
                DeviceRuntimeMetadataUpdate.Rejected(rejected)
            }
        }
    }

    private fun disconnectMetadataFailure(deviceUid: DeviceUid) {
        val session = sessions[deviceUid] ?: return
        synchronized(session) {
            if (isCurrentSession(session)) {
                session.wsClient.disconnect(reason = METADATA_BOOTSTRAP_FAILED_REASON)
            }
        }
    }

    private fun rejectActiveGeneration(deviceUid: DeviceUid, field: String) {
        cancelMetadataTimeout(deviceUid)
        val state = metadataBootstrapCoordinator.currentState(deviceUid) ?: return
        if (state is DeviceRuntimeMetadataGenerationState.Rejected) return
        metadataBootstrapCoordinator.reject(
            deviceUid = deviceUid,
            generation = state.generation,
            code = DeviceRuntimeMetadataFailureCode.RUNTIME_UNAVAILABLE,
            field = field
        )
    }

    private fun scheduleMetadataTimeout(
        session: RuntimeSession,
        generation: DeviceRuntimeMetadataGeneration
    ) {
        cancelMetadataTimeout(session.deviceUid)
        val timeoutJob = session.sessionScope.launch {
            delay(METADATA_BOOTSTRAP_TIMEOUT_MILLIS)
            val rejected = metadataBootstrapCoordinator.expire(
                deviceUid = session.deviceUid,
                generation = generation
            )
            if (rejected != null) {
                disconnectMetadataFailure(session.deviceUid)
            }
        }
        metadataTimeoutJobs[session.deviceUid] = timeoutJob
        timeoutJob.invokeOnCompletion {
            metadataTimeoutJobs.remove(session.deviceUid, timeoutJob)
        }
    }

    private fun cancelMetadataTimeout(deviceUid: DeviceUid) {
        metadataTimeoutJobs.remove(deviceUid)?.cancel()
    }

    private fun cancelAllMetadataTimeouts() {
        metadataTimeoutJobs.values.forEach(Job::cancel)
        metadataTimeoutJobs.clear()
    }

    private fun detachSessionForRetirement(deviceUid: DeviceUid): RuntimeSession? =
        synchronized(lifecycleLock) {
            if (closed) return@synchronized null
            retiredDeviceUids.add(deviceUid)
            sessions.remove(deviceUid).also {
                if (lastActiveDeviceUid == deviceUid) lastActiveDeviceUid = null
            }
        }

    private fun beginRepositoryClose(): List<RuntimeSession>? = synchronized(lifecycleLock) {
        if (closed) return@synchronized null
        closed = true
        lastActiveDeviceUid = null
        retiredDeviceUids.clear()
        sessions.values.toList().also { sessions.clear() }
    }

    private fun createSession(deviceUid: DeviceUid): RuntimeSession {
        val wsClient = wsClientFactory(tokenProvider)
        val sessionJob = SupervisorJob(repositoryJob)
        return RuntimeSession(
            deviceUid = deviceUid,
            wsClient = wsClient,
            commandClient = AqlWsCommandClient(wsClient),
            sessionJob = sessionJob,
            sessionScope = CoroutineScope(sessionJob + dispatcher)
        )
    }

    private fun observeSession(session: RuntimeSession) {
        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.connectionState.collect { state ->
                    if (isCurrentSession(session)) _connectionState.emit(state)
                }
            }
        )
        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.events.collect { event ->
                    if (!isCurrentSession(session)) return@collect
                    handleAuthLifecycle(session, event)
                    if (isCurrentSession(session)) _events.emit(event)
                }
            }
        )
    }

    private fun isCurrentSession(session: RuntimeSession): Boolean = synchronized(lifecycleLock) {
        !closed &&
            session.deviceUid !in retiredDeviceUids &&
            sessions[session.deviceUid] === session
    }

    private fun ensureOpen() {
        synchronized(lifecycleLock) { check(!closed) { "Device runtime repository is closed." } }
    }

    private suspend fun handleAuthLifecycle(session: RuntimeSession, event: AqlWsEvent) {
        when (event) {
            is AqlWsEvent.Opened -> Unit
            is AqlWsEvent.Authenticated -> {
                if (!sendAuthenticatedBootstrap(session)) {
                    session.wsClient.disconnect(reason = METADATA_BOOTSTRAP_FAILED_REASON)
                }
            }
            is AqlWsEvent.Message -> Unit
            is AqlWsEvent.Closed -> rejectActiveGeneration(event.deviceUid, "closed")
            is AqlWsEvent.Failure -> rejectActiveGeneration(event.deviceUid, "failure")
        }
    }

    private fun sendAuthenticatedBootstrap(session: RuntimeSession): Boolean {
        val generation = metadataBootstrapCoordinator.beginAndDispatch(
            deviceUid = session.deviceUid,
            send = session.wsClient::send
        ).getOrNull() ?: return false
        scheduleMetadataTimeout(session, generation)
        return true
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val LOCAL_NETWORK_UNAVAILABLE_REASON = "local network unavailable"
        private const val METADATA_BOOTSTRAP_FAILED_REASON = "metadata bootstrap failed"
        private const val METADATA_BOOTSTRAP_TIMEOUT_MILLIS = 10_000L

        fun withCredentialStore(context: Context, ownerUid: String): DeviceRuntimeRepository =
            DeviceRuntimeRepository(
                tokenProvider = DeviceCredentialStore(context = context, ownerUid = ownerUid)
            )
    }
}

internal object RuntimeConnectionReusePolicy {
    fun shouldReconnect(
        state: AqlWsConnectionState,
        requestedDeviceUid: DeviceUid,
        endpointMatches: Boolean
    ): Boolean {
        if (!endpointMatches) return true
        return when (state) {
            is AqlWsConnectionState.Connecting -> state.deviceUid != requestedDeviceUid
            is AqlWsConnectionState.Connected -> state.deviceUid != requestedDeviceUid
            is AqlWsConnectionState.Authenticated -> state.deviceUid != requestedDeviceUid
            AqlWsConnectionState.Disconnected,
            is AqlWsConnectionState.AuthRequired,
            is AqlWsConnectionState.Failed -> true
        }
    }
}
