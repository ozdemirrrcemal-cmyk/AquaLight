package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataFailureCode
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeSyncCoordinator
import com.aqua.aqualight.data.devices.runtime.ws.AqlPrivateLanEndpoint
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
            if (closed.get() && collectorJobs.remove(job)) {
                job.cancel()
            }
        }

        override fun close() {
            val jobs = synchronized(this) {
                beginCloseLocked()
            } ?: return

            jobs.forEach(Job::cancel)
            sessionJob.cancel()
            wsClient.close()
        }

        suspend fun shutdown() {
            val jobs = synchronized(this) {
                beginCloseLocked()
            } ?: return

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
            if (!closed.compareAndSet(false, true)) {
                return null
            }
            return collectorJobs.toList().also {
                collectorJobs.clear()
            }
        }
    }

    private val lifecycleLock = Any()
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + dispatcher)
    private val tokenLifecycleMutex = Mutex()
    private val sessions = ConcurrentHashMap<DeviceUid, RuntimeSession>()
    private val retiredDeviceUids = ConcurrentHashMap.newKeySet<DeviceUid>()

    internal val metadataBootstrapCoordinator = DeviceRuntimeMetadataBootstrapCoordinator()

    val runtimeModules: DeviceRuntimeModuleProvider = DeviceRuntimeModuleProvider { deviceUid ->
        sessions[deviceUid]?.commandClient
    }

    private val timeSyncCoordinator = DeviceTimeSyncCoordinator(
        repository = runtimeModules.time
    )

    private val _connectionState = MutableSharedFlow<AqlWsConnectionState>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
    val connectionState: SharedFlow<AqlWsConnectionState> = _connectionState.asSharedFlow()

    private val _events = MutableSharedFlow<AqlWsEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY
    )
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

                val current = sessions[deviceUid] ?: createSession(deviceUid).also { created ->
                    sessions[deviceUid] = created
                    observeSession(created)
                }
                lastActiveDeviceUid = deviceUid
                current
            }
        }.getOrElse { error ->
            return Result.failure(error)
        }

        return synchronized(session) {
            val currentSession = synchronized(lifecycleLock) {
                !closed &&
                    deviceUid !in retiredDeviceUids &&
                    sessions[deviceUid] === session
            }
            if (!currentSession) {
                return@synchronized Result.failure(
                    IllegalStateException("Device runtime session is no longer active.")
                )
            }

            val endpointUrl = AqlPrivateLanEndpoint.route(
                deviceUid = deviceUid,
                endpoint = snapshot.endpoint
            )?.url
            val endpointMatches = session.endpointUrl == endpointUrl
            val currentState = session.wsClient.connectionState.value

            if (
                !RuntimeConnectionReusePolicy.shouldReconnect(
                    state = currentState,
                    requestedDeviceUid = deviceUid,
                    endpointMatches = endpointMatches
                )
            ) {
                return@synchronized Result.success(Unit)
            }

            session.endpointUrl = endpointUrl
            session.wsClient.connect(
                deviceUid = deviceUid,
                endpoint = snapshot.endpoint
            )
        }
    }

    fun activate(deviceUid: DeviceUid) {
        synchronized(lifecycleLock) {
            check(!closed) { "Device runtime repository is closed." }
            retiredDeviceUids.remove(deviceUid)
        }
    }

    fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
        sessions[deviceUid]?.wsClient?.connectionState?.value

    fun disconnectForLocalNetworkLoss() {
        val activeSessions = synchronized(lifecycleLock) {
            if (closed) {
                return
            }
            lastActiveDeviceUid = null
            sessions.values.toList()
        }
        activeSessions.forEach { session ->
            metadataBootstrapCoordinator.currentState(session.deviceUid)?.let { state ->
                metadataBootstrapCoordinator.reject(
                    deviceUid = session.deviceUid,
                    generation = state.generation,
                    code = DeviceRuntimeMetadataFailureCode.RUNTIME_UNAVAILABLE,
                    field = "localNetwork"
                )
            }
            synchronized(session) {
                session.wsClient.disconnect(reason = LOCAL_NETWORK_UNAVAILABLE_REASON)
            }
        }
    }

    fun commandClient(): AqlWsCommandClient? {
        val activeUid = lastActiveDeviceUid ?: return null
        return sessions[activeUid]?.commandClient
    }

    fun commandClient(deviceUid: DeviceUid): AqlWsCommandClient? {
        return sessions[deviceUid]?.commandClient
    }

    suspend fun saveToken(
        deviceUid: DeviceUid,
        token: String
    ) {
        val provider = tokenProvider ?: return
        tokenLifecycleMutex.withLock {
            ensureOpen()
            provider.saveToken(
                deviceUid = deviceUid,
                token = token
            )
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

    /**
     * Permanently retires a device runtime inside this owner repository. Background
     * probes cannot recreate the session until an explicit registration calls [activate].
     */
    suspend fun retire(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.shutdown()
    }

    /** Immediate terminal fallback; deletion flows should prefer [retire]. */
    fun close(deviceUid: DeviceUid) {
        val session = detachSessionForRetirement(deviceUid)
        metadataBootstrapCoordinator.clear(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.close()
    }

    override fun close() {
        val activeSessions = beginRepositoryClose() ?: return
        metadataBootstrapCoordinator.clearAll()
        repositoryJob.cancel()
        activeSessions.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.close()
        }
    }

    /**
     * Terminal owner barrier. Returns only after collectors, socket jobs and token
     * operations that started under this owner have completed or been cancelled.
     */
    suspend fun shutdown() {
        val activeSessions = beginRepositoryClose()
        metadataBootstrapCoordinator.clearAll()
        repositoryJob.cancel()

        if (activeSessions != null) {
            activeSessions.forEach { session ->
                timeSyncCoordinator.clearSessionMemory(session.deviceUid)
                session.shutdown()
            }
        }

        tokenLifecycleMutex.withLock {
            // Waiting for the mutex is the owner-token access barrier.
        }
        repositoryJob.join()
    }

    private fun detachSessionForRetirement(deviceUid: DeviceUid): RuntimeSession? {
        return synchronized(lifecycleLock) {
            if (closed) {
                return@synchronized null
            }
            retiredDeviceUids.add(deviceUid)
            val removed = sessions.remove(deviceUid)
            if (lastActiveDeviceUid == deviceUid) {
                lastActiveDeviceUid = null
            }
            removed
        }
    }

    private fun beginRepositoryClose(): List<RuntimeSession>? {
        return synchronized(lifecycleLock) {
            if (closed) {
                return@synchronized null
            }
            closed = true
            lastActiveDeviceUid = null
            retiredDeviceUids.clear()
            sessions.values.toList().also {
                sessions.clear()
            }
        }
    }

    private fun createSession(deviceUid: DeviceUid): RuntimeSession {
        val wsClient = wsClientFactory(tokenProvider)
        val commandClient = AqlWsCommandClient(wsClient)
        val sessionJob = SupervisorJob(repositoryJob)
        val sessionScope = CoroutineScope(sessionJob + dispatcher)

        return RuntimeSession(
            deviceUid = deviceUid,
            wsClient = wsClient,
            commandClient = commandClient,
            sessionJob = sessionJob,
            sessionScope = sessionScope
        )
    }

    private fun observeSession(session: RuntimeSession) {
        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.connectionState.collect { state ->
                    if (isCurrentSession(session)) {
                        _connectionState.emit(state)
                    }
                }
            }
        )

        session.track(
            session.sessionScope.launch(start = CoroutineStart.UNDISPATCHED) {
                session.wsClient.events.collect { event ->
                    if (!isCurrentSession(session)) {
                        return@collect
                    }
                    handleAuthLifecycle(
                        session = session,
                        event = event
                    )
                    if (isCurrentSession(session)) {
                        _events.emit(event)
                    }
                }
            }
        )
    }

    private fun isCurrentSession(session: RuntimeSession): Boolean {
        return synchronized(lifecycleLock) {
            !closed &&
                session.deviceUid !in retiredDeviceUids &&
                sessions[session.deviceUid] === session
        }
    }

    private fun ensureOpen() {
        synchronized(lifecycleLock) {
            check(!closed) { "Device runtime repository is closed." }
        }
    }

    private suspend fun handleAuthLifecycle(
        session: RuntimeSession,
        event: AqlWsEvent
    ) {
        when (event) {
            is AqlWsEvent.Opened -> Unit

            is AqlWsEvent.Authenticated -> {
                if (!sendAuthenticatedBootstrap(session)) {
                    session.wsClient.disconnect(reason = METADATA_BOOTSTRAP_FAILED_REASON)
                }
            }

            is AqlWsEvent.Message -> Unit

            is AqlWsEvent.Closed -> {
                metadataBootstrapCoordinator.currentState(event.deviceUid)?.let { state ->
                    metadataBootstrapCoordinator.reject(
                        deviceUid = event.deviceUid,
                        generation = state.generation,
                        code = DeviceRuntimeMetadataFailureCode.RUNTIME_UNAVAILABLE,
                        field = "closed"
                    )
                }
            }

            is AqlWsEvent.Failure -> {
                metadataBootstrapCoordinator.currentState(event.deviceUid)?.let { state ->
                    metadataBootstrapCoordinator.reject(
                        deviceUid = event.deviceUid,
                        generation = state.generation,
                        code = DeviceRuntimeMetadataFailureCode.RUNTIME_UNAVAILABLE,
                        field = "failure"
                    )
                }
            }
        }
    }

    private fun sendAuthenticatedBootstrap(session: RuntimeSession): Boolean {
        return metadataBootstrapCoordinator.beginAndDispatch(
            deviceUid = session.deviceUid,
            send = session.wsClient::send
        ).isSuccess
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val LOCAL_NETWORK_UNAVAILABLE_REASON = "local network unavailable"
        private const val METADATA_BOOTSTRAP_FAILED_REASON = "metadata bootstrap failed"

        fun withCredentialStore(
            context: Context,
            ownerUid: String
        ): DeviceRuntimeRepository {
            return DeviceRuntimeRepository(
                tokenProvider = DeviceCredentialStore(
                    context = context,
                    ownerUid = ownerUid
                )
            )
        }
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
