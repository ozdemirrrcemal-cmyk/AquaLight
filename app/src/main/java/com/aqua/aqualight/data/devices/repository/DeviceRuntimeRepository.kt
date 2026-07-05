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
    private val wsClientFactory: () -> AqlWsClient = { AqlWsClient() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private data class RuntimeSession(
        val deviceUid: DeviceUid,
        val wsClient: AqlWsClient,
        val commandClient: AqlWsCommandClient
    )

    private val sessions = ConcurrentHashMap<DeviceUid, RuntimeSession>()
    private val authRequiredMessages = ConcurrentHashMap<DeviceUid, String>()

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

        return session.wsClient.connect(
            deviceUid = deviceUid,
            endpoint = snapshot.endpoint
        )
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
        tokenProvider?.saveToken(
            deviceUid = deviceUid,
            token = token
        )
    }

    suspend fun clearToken(deviceUid: DeviceUid) {
        tokenProvider?.clearToken(deviceUid)
        authRequiredMessages.remove(deviceUid)
    }

    fun clearTokenAsync(deviceUid: DeviceUid) {
        scope.launch {
            tokenProvider?.clearToken(deviceUid)
            authRequiredMessages.remove(deviceUid)
        }
    }

    fun close(deviceUid: DeviceUid) {
        timeSyncCoordinator.clearSessionMemory(deviceUid)
        sessions.remove(deviceUid)?.wsClient?.close()
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
        val wsClient = wsClientFactory()
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
                _connectionState.emit(state.toUserMeaningfulRuntimeState())
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

    private fun AqlWsConnectionState.toUserMeaningfulRuntimeState(): AqlWsConnectionState {
        return when (this) {
            is AqlWsConnectionState.AuthRequired -> {
                authRequiredMessages[deviceUid] = message.ifBlank { AUTH_REQUIRED_MESSAGE }
                this
            }

            is AqlWsConnectionState.Authenticated -> {
                authRequiredMessages.remove(deviceUid)
                this
            }

            is AqlWsConnectionState.Failed -> {
                val uid = deviceUid
                val authMessage = if (uid != null) authRequiredMessages[uid] else null
                if (uid != null && authMessage != null) {
                    AqlWsConnectionState.AuthRequired(
                        deviceUid = uid,
                        message = authMessage
                    )
                } else {
                    this
                }
            }

            else -> this
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
                        AqlWsAuthAttemptResult.NoToken -> {
                            markAuthRequired(
                                session = session,
                                deviceUid = event.deviceUid,
                                message = "Runtime token is missing. Pair the device again."
                            )
                        }
                        AqlWsAuthAttemptResult.SendFailed -> {
                            markAuthRequired(
                                session = session,
                                deviceUid = event.deviceUid,
                                message = "Runtime authentication could not be sent."
                            )
                        }
                        null -> Unit
                    }
                }

                when (val authStateChange = authManager?.handleIncomingMessage(
                    deviceUid = event.deviceUid,
                    message = event.parsed,
                    wsClient = session.wsClient
                )) {
                    is AqlWsAuthStateChange.Authenticated -> {
                        session.commandClient.deviceIdentity()
                        session.commandClient.networkStatus()
                        timeSyncCoordinator.syncPhoneNowIfNeeded(
                            deviceUid = event.deviceUid
                        )
                    }

                    is AqlWsAuthStateChange.Rejected -> {
                        markAuthRequired(
                            session = session,
                            deviceUid = event.deviceUid,
                            message = authStateChange.message
                        )
                    }

                    null -> Unit
                }
            }

            else -> Unit
        }
    }

    private fun markAuthRequired(
        session: RuntimeSession,
        deviceUid: DeviceUid,
        message: String
    ) {
        val safeMessage = message.ifBlank { AUTH_REQUIRED_MESSAGE }
        authRequiredMessages[deviceUid] = safeMessage
        session.wsClient.markAuthRequired(
            deviceUid = deviceUid,
            message = safeMessage
        )
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
        private const val AUTH_REQUIRED_MESSAGE = "Authentication required. Pair the device again."

        fun withCredentialStore(
            context: Context
        ): DeviceRuntimeRepository {
            return DeviceRuntimeRepository(
                tokenProvider = DeviceCredentialStore(context)
            )
        }
    }
}
