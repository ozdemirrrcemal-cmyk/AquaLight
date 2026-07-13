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
import com.aqua.aqualight.data.devices.store.DeviceCredentialStore
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DeviceRuntimeRepository(
    private val tokenProvider: AqlWsTokenProvider? = null,
    private val wsClientFactory: (AqlWsTokenProvider?) -> AqlWsClient = { provider ->
        AqlWsClient(tokenProvider = provider)
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private data class RuntimeSession(
        val deviceUid: DeviceUid,
        val wsClient: AqlWsClient,
        val commandClient: AqlWsCommandClient,
        @Volatile var endpointUrl: String? = null
    )

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

    fun connect(snapshot: DeviceSnapshot): Result<Unit> {
        val deviceUid = snapshot.deviceUid
        val session = sessionFor(deviceUid)
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
        tokenProvider?.saveToken(
            deviceUid = deviceUid,
            token = token
        )
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        tokenProvider?.clearToken(deviceUid)
    }

    fun close(deviceUid: DeviceUid) {
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        sessions.remove(deviceUid)?.wsClient?.close()
        if (lastActiveDeviceUid == deviceUid) {
            lastActiveDeviceUid = null
        }
    }

    fun close() {
        sessions.values.forEach { session ->
            timeSyncCoordinator.clearSessionMemory(session.deviceUid)
            session.wsClient.close()
        }
        sessions.clear()
        lastActiveDeviceUid = null
    }

    private fun sessionFor(deviceUid: DeviceUid): RuntimeSession {
        return sessions.getOrPut(deviceUid) {
            createSession(deviceUid)
        }
    }

    private fun createSession(deviceUid: DeviceUid): RuntimeSession {
        val wsClient = wsClientFactory(tokenProvider)
        val commandClient = AqlWsCommandClient(wsClient)

        val session = RuntimeSession(
            deviceUid = deviceUid,
            wsClient = wsClient,
            commandClient = commandClient
        )

        observeSession(session)

        return session
    }

    private fun observeSession(session: RuntimeSession) {
        scope.launch {
            session.wsClient.connectionState.collect { state ->
                _connectionState.emit(state)
            }
        }

        scope.launch {
            session.wsClient.events.collect { event ->
                handleAuthLifecycle(
                    session = session,
                    event = event
                )
                _events.emit(event)
            }
        }
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

            else -> Unit
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
