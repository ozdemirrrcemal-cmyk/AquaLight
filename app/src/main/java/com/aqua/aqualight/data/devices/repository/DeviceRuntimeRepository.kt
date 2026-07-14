package com.aqua.aqualight.data.devices.repository

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.time.DeviceTimeSyncCoordinator
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsAuthManager
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsAuthAttemptResult
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsAuthStateChange
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DeviceRuntimeRepository(
    private val tokenProvider: AqlWsTokenProvider? = null,
    private val wsClientFactory: (AqlWsTokenProvider?) -> AqlWsTransport = { provider ->
        AqlWsClient(tokenProvider = provider)
    },
    dispatcher: CoroutineDispatcher = Dispatchers.IO
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
            if (!closed.compareAndSet(false, true)) {
                return
            }
            collectorJobs.forEach(Job::cancel)
            collectorJobs.clear()
            sessionJob.cancel()
            wsClient.close()
        }
    }

    private val lifecycleLock = Any()
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + dispatcher)
    private val sessions = ConcurrentHashMap<DeviceUid, RuntimeSession>()

    val runtimeModules: DeviceRuntimeModuleProvider = DeviceRuntimeModuleProvider { deviceUid ->
        sessions[deviceUid]?.commandClient
    }

    private val timeSyncCoordinator = DeviceTimeSyncCoordinator(
        repository = runtimeModules.time
    )

    private val authManager = tokenProvider?.let { provider ->
        AqlWsAuthManager(provider)
    }

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
            sessionFor(deviceUid)
        }.getOrElse { error ->
            return Result.failure(error)
        }
        lastActiveDeviceUid = deviceUid

        return synchronized(session) {
            val endpointUrl = snapshot.endpoint.toWebSocketUrl()
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
            authManager?.clear(session.deviceUid)
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
        ensureOpen()
        tokenProvider?.saveToken(
            deviceUid = deviceUid,
            token = token
        )
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        ensureOpen()
        tokenProvider?.clearToken(deviceUid)
    }

    fun close(deviceUid: DeviceUid) {
        val session = synchronized(lifecycleLock) {
            val removed = sessions.remove(deviceUid)
            if (lastActiveDeviceUid == deviceUid) {
                lastActiveDeviceUid = null
            }
            removed
        }
        authManager?.clear(deviceUid)
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        session?.close()
    }

    override fun close() {
        val activeSessions = synchronized(lifecycleLock) {
            if (closed) {
                return
            }
            closed = true
            lastActiveDeviceUid = null
            sessions.values.toList().also {
                sessions.clear()
            }
        }

        repositoryScope.cancel()
        activeSessions.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.close()
        }
        authManager?.close()
    }

    private fun sessionFor(deviceUid: DeviceUid): RuntimeSession {
        return synchronized(lifecycleLock) {
            check(!closed) { "Device runtime repository is closed." }
            sessions[deviceUid]?.let { existing ->
                return@synchronized existing
            }

            val created = createSession(deviceUid)
            sessions[deviceUid] = created
            observeSession(created)
            created
        }
    }

    private fun createSession(deviceUid: DeviceUid): RuntimeSession {
        val wsClient = wsClientFactory(tokenProvider)
        val commandClient = AqlWsCommandClient(wsClient)
        val sessionJob = SupervisorJob(repositoryJob)
        val sessionScope = CoroutineScope(
            repositoryScope.coroutineContext + sessionJob
        )

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
            session.sessionScope.launch {
                session.wsClient.connectionState.collect { state ->
                    if (isCurrentSession(session)) {
                        _connectionState.emit(state)
                    }
                }
            }
        )

        session.track(
            session.sessionScope.launch {
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
            !closed && sessions[session.deviceUid] === session
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Device runtime repository is closed." }
    }

    private suspend fun handleAuthLifecycle(
        session: RuntimeSession,
        event: AqlWsEvent
    ) {
        when (event) {
            is AqlWsEvent.Opened -> Unit

            is AqlWsEvent.Message -> {
                if (event.parsed is AqlWsIncomingMessage.Hello) {
                    sendFirmwarePublicBootstrap(session.commandClient)
                    when (authManager?.authenticateIfTokenExists(
                        deviceUid = event.deviceUid,
                        commandClient = session.commandClient
                    )) {
                        is AqlWsAuthAttemptResult.AuthMessageSent -> Unit
                        AqlWsAuthAttemptResult.NoToken -> Unit
                        AqlWsAuthAttemptResult.SendFailed -> Unit
                        null -> Unit
                    }
                }

                val authStateChange = authManager?.handleIncomingMessage(
                    deviceUid = event.deviceUid,
                    message = event.parsed,
                    wsClient = session.wsClient
                )

                if (authStateChange is AqlWsAuthStateChange.Authenticated) {
                    session.commandClient.deviceIdentity()
                    session.commandClient.networkStatus()
                    timeSyncCoordinator.syncPhoneNowIfNeeded(
                        deviceUid = event.deviceUid
                    )
                }
            }

            is AqlWsEvent.Closed,
            is AqlWsEvent.Failure -> authManager?.clear(event.deviceUid)
        }
    }

    private fun sendFirmwarePublicBootstrap(commandClient: AqlWsCommandClient) {
        commandClient.securityStatus()
        commandClient.deviceIdentity()
        commandClient.deviceStatus()
        commandClient.deviceCapabilities()
        commandClient.networkStatus()
        commandClient.timeStatus()
        commandClient.firmwareStatus()
        commandClient.lightStatus()
        commandClient.coolingStatus()
        commandClient.timerStatus()
        commandClient.dosingStatus()
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val LOCAL_NETWORK_UNAVAILABLE_REASON = "local network unavailable"

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
