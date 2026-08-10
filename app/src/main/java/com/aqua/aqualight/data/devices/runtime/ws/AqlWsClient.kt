package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class AqlWsClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val tokenProvider: AqlWsTokenProvider? = null,
    private val webSocketFactory: WebSocket.Factory? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val handshakeTimeoutMillis: Long = DEFAULT_HANDSHAKE_TIMEOUT_MILLIS
) : AqlWsTransport {

    private val wireCodec = AqlWsWireCodec()

    private data class DetachedConnection(
        val socket: WebSocket?,
        val job: Job?,
        val pendingAuthentication: AqlWsPendingAuthentication?,
        val secureSession: AqlWsSecureSession?
    ) {
        fun destroySecurityMaterial() {
            pendingAuthentication?.close()
            secureSession?.close()
        }
    }

    private val lifecycleLock = Any()
    private val clientJob = SupervisorJob()
    private val eventChannel = Channel<AqlWsEvent>(capacity = Channel.UNLIMITED)

    private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
        AqlWsConnectionState.Disconnected
    )
    override val connectionState: StateFlow<AqlWsConnectionState> = _connectionState.asStateFlow()
    override val events: Flow<AqlWsEvent> = eventChannel.receiveAsFlow()

    @Volatile
    private var activeSocket: WebSocket? = null

    @Volatile
    private var activeDeviceUid: DeviceUid? = null

    @Volatile
    private var activeConnectionJob: Job? = null

    @Volatile
    private var activeConnectionScope: CoroutineScope? = null

    @Volatile
    private var activeSecureSession: AqlWsSecureSession? = null

    @Volatile
    private var pendingAuthentication: AqlWsPendingAuthentication? = null

    @Volatile
    private var helloReceived = false

    @Volatile
    private var connectionGeneration: Long = 0L

    @Volatile
    private var closed: Boolean = false

    private val pendingTokenInvalidationCommandIds = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>()
    )

    override fun connect(
        deviceUid: DeviceUid,
        endpoint: DeviceRuntimeEndpoint
    ): Result<Unit> = runCatching {
        synchronized(lifecycleLock) {
            check(!closed) { "WebSocket client is closed." }
        }

        val route = AqlPrivateLanEndpoint.route(deviceUid, endpoint)
            ?: error("Device has no compatible private-LAN WebSocket endpoint.")
        val request = Request.Builder().url(route.url).build()

        disconnect(code = NORMAL_CLOSE_CODE, reason = RECONNECT_CLOSE_REASON)

        val generation: Long
        val listener: WebSocketListener
        synchronized(lifecycleLock) {
            check(!closed) { "WebSocket client is closed." }

            val connectionJob = SupervisorJob(clientJob)
            activeConnectionJob = connectionJob
            activeConnectionScope = CoroutineScope(connectionJob + dispatcher)
            generation = ++connectionGeneration
            activeDeviceUid = deviceUid
            helloReceived = false
            _connectionState.value = AqlWsConnectionState.Connecting(
                deviceUid = deviceUid,
                url = route.url
            )
            listener = listenerFor(
                deviceUid = deviceUid,
                url = route.url,
                generation = generation
            )
        }

        val factory = webSocketFactory ?: okHttpClient.newBuilder()
            .dns(AqlPrivateLanDns(route))
            .build()
        val socket = try {
            factory.newWebSocket(request, listener)
        } catch (error: Throwable) {
            failConnectionSetup(deviceUid, generation, error)
            throw error
        }

        val rejectSocket = synchronized(lifecycleLock) {
            if (closed || connectionGeneration != generation || activeDeviceUid != deviceUid) {
                true
            } else {
                if (activeSocket == null) activeSocket = socket
                activeSocket !== socket
            }
        }
        if (rejectSocket) socket.cancel()
    }

    override fun send(message: AqlWsOutgoingMessage): Boolean {
        val sendResult = try {
            synchronized(lifecycleLock) {
                if (closed || !canSendLocked(message)) return@synchronized SendResult.Rejected
                val raw = wireCodec.encode(message, activeSecureSession)
                val sent = activeSocket?.send(raw) == true
                if (sent) {
                    message.lifecycleInvalidatingCommandId()?.let(
                        pendingTokenInvalidationCommandIds::add
                    )
                    SendResult.Sent
                } else {
                    SendResult.SocketFailure
                }
            }
        } catch (_: AqlWsProtocolException) {
            SendResult.ProtocolFailure
        } catch (_: Throwable) {
            SendResult.ProtocolFailure
        }

        return when (sendResult) {
            SendResult.Sent -> true
            SendResult.Rejected -> false
            SendResult.SocketFailure -> {
                disconnect(code = INTERNAL_ERROR_CLOSE_CODE, reason = SEND_FAILURE_CLOSE_REASON)
                false
            }
            SendResult.ProtocolFailure -> {
                disconnect(code = PROTOCOL_ERROR_CLOSE_CODE, reason = PROTOCOL_CLOSE_REASON)
                false
            }
        }
    }

    override fun disconnect(code: Int, reason: String) {
        val detached = synchronized(lifecycleLock) {
            if (closed) return
            detachConnectionLocked(AqlWsConnectionState.Disconnected)
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        detached.socket?.let { socket ->
            if (!socket.close(code, reason.take(MAX_CLOSE_REASON_CHARS))) socket.cancel()
        }
    }

    override fun close() {
        val detached = synchronized(lifecycleLock) {
            detachForTerminalCloseLocked() ?: return
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        detached.socket?.cancel()
        clientJob.cancel()
        eventChannel.close()
    }

    override suspend fun shutdown() {
        val detached = synchronized(lifecycleLock) { detachForTerminalCloseLocked() }
        detached?.destroySecurityMaterial()
        detached?.job?.cancel()
        detached?.socket?.cancel()
        clientJob.cancel()
        eventChannel.close()
        detached?.job?.join()
        clientJob.join()
    }

    private fun listenerFor(
        deviceUid: DeviceUid,
        url: String,
        generation: Long
    ): WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!publishOpenedIfCurrent(webSocket, generation, deviceUid, url)) {
                webSocket.cancel()
                return
            }
            launchHandshakeTimeout(webSocket, generation, deviceUid)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleTextFrame(webSocket, generation, deviceUid, text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.UNSUPPORTED_TYPE
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            finishConnection(
                webSocket = webSocket,
                generation = generation,
                deviceUid = deviceUid,
                nextState = AqlWsConnectionState.Disconnected,
                event = AqlWsEvent.Closed(deviceUid, code, sanitizedCloseReason(reason))
            )
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            finishConnection(
                webSocket = webSocket,
                generation = generation,
                deviceUid = deviceUid,
                nextState = AqlWsConnectionState.Failed(
                    deviceUid = deviceUid,
                    message = CONNECTION_FAILURE_MESSAGE,
                    cause = t
                ),
                event = AqlWsEvent.Failure(
                    deviceUid = deviceUid,
                    message = CONNECTION_FAILURE_MESSAGE,
                    throwable = t
                )
            )
        }
    }

    private fun handleTextFrame(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        text: String
    ) {
        val state = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            DecodeState(
                pendingAuthentication = pendingAuthentication,
                secureSession = activeSecureSession
            )
        }

        val decoded = try {
            wireCodec.decode(
                raw = text,
                expectedDeviceUid = deviceUid.value,
                pendingAuthentication = state.pendingAuthentication,
                secureSession = state.secureSession
            )
        } catch (error: AqlWsProtocolException) {
            failProtocolConnection(
                webSocket = webSocket,
                generation = generation,
                deviceUid = deviceUid,
                error = error.protocolError,
                frameBytes = text.toByteArray(StandardCharsets.UTF_8).size
            )
            return
        } catch (_: Throwable) {
            failProtocolConnection(
                webSocket = webSocket,
                generation = generation,
                deviceUid = deviceUid,
                error = AqlWsProtocolError.MALFORMED_JSON,
                frameBytes = text.toByteArray(StandardCharsets.UTF_8).size
            )
            return
        }

        when (decoded) {
            is AqlWsDecodedFrame.Hello -> handleHello(
                webSocket,
                generation,
                deviceUid,
                decoded.challenge
            )
            is AqlWsDecodedFrame.Authenticated -> completeAuthentication(
                webSocket,
                generation,
                deviceUid,
                decoded.secureSession
            )
            is AqlWsDecodedFrame.AuthRejected -> rejectAuthentication(
                webSocket,
                generation,
                deviceUid
            )
            is AqlWsDecodedFrame.Runtime -> publishRuntimeMessage(
                webSocket,
                generation,
                deviceUid,
                decoded.message
            )
        }
    }

    private fun handleHello(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        hello: AqlWsHelloChallenge
    ) {
        val scope = synchronized(lifecycleLock) {
            if (
                !isCurrentConnectionLocked(webSocket, generation, deviceUid) ||
                helloReceived ||
                pendingAuthentication != null ||
                activeSecureSession != null
            ) {
                null
            } else {
                helloReceived = true
                activeConnectionScope
            }
        }
        if (scope == null) {
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_OUT_OF_SEQUENCE
            )
            return
        }

        scope.launch {
            authenticateCurrentConnection(webSocket, generation, deviceUid, hello)
        }
    }

    private suspend fun authenticateCurrentConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        hello: AqlWsHelloChallenge
    ) {
        val token = try {
            tokenProvider?.getToken(deviceUid)?.trim().orEmpty()
        } catch (_: Throwable) {
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_FAILED
            )
            return
        }
        if (token.isBlank()) {
            markAuthenticationRequiredIfCurrent(webSocket, generation, deviceUid)
            return
        }

        val pending = try {
            wireCodec.prepareAuthentication(hello, token)
        } catch (_: Throwable) {
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_FAILED
            )
            return
        }
        val raw = try {
            wireCodec.encodeAuthenticationRequest(pending)
        } catch (_: Throwable) {
            pending.close()
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_FAILED
            )
            return
        }

        val sent = synchronized(lifecycleLock) {
            if (
                !isCurrentConnectionLocked(webSocket, generation, deviceUid) ||
                pendingAuthentication != null ||
                activeSecureSession != null
            ) {
                false
            } else {
                pendingAuthentication = pending
                webSocket.send(raw)
            }
        }
        if (!sent) {
            synchronized(lifecycleLock) {
                if (pendingAuthentication === pending) pendingAuthentication = null
            }
            pending.close()
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_FAILED
            )
        }
    }

    private fun completeAuthentication(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        secureSession: AqlWsSecureSession
    ) {
        val oldPending = synchronized(lifecycleLock) {
            if (
                !isCurrentConnectionLocked(webSocket, generation, deviceUid) ||
                pendingAuthentication == null ||
                activeSecureSession != null
            ) {
                null
            } else {
                pendingAuthentication.also {
                    pendingAuthentication = null
                    activeSecureSession = secureSession
                    _connectionState.value = AqlWsConnectionState.Authenticated(
                        deviceUid = deviceUid,
                        authenticatedAtMillis = clockMillis()
                    )
                    publishEventLocked(AqlWsEvent.Authenticated(deviceUid))
                }
            }
        }
        if (oldPending == null) {
            secureSession.close()
            failProtocolConnection(
                webSocket,
                generation,
                deviceUid,
                AqlWsProtocolError.AUTHENTICATION_OUT_OF_SEQUENCE
            )
        } else {
            oldPending.close()
        }
    }

    private fun rejectAuthentication(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ) {
        val detached = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            detachConnectionLocked(
                AqlWsConnectionState.AuthRequired(
                    deviceUid,
                    AqlWsProtocolError.AUTHENTICATION_FAILED.safeMessage
                )
            )
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        detached.socket?.close(POLICY_VIOLATION_CLOSE_CODE, AUTH_FAILURE_CLOSE_REASON)
    }

    private fun markAuthenticationRequiredIfCurrent(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ) {
        val detached = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            detachConnectionLocked(
                AqlWsConnectionState.AuthRequired(deviceUid, MISSING_CREDENTIAL_MESSAGE)
            )
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        detached.socket?.close(POLICY_VIOLATION_CLOSE_CODE, AUTH_FAILURE_CLOSE_REASON)
    }

    private fun publishRuntimeMessage(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage
    ) {
        val shouldInvalidateToken = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            val invalidate = message is AqlWsIncomingMessage.Response &&
                pendingTokenInvalidationCommandIds.remove(message.id) &&
                message.ok
            publishEventLocked(AqlWsEvent.Message(deviceUid, message))
            invalidate
        }
        if (shouldInvalidateToken) {
            val scope = synchronized(lifecycleLock) {
                if (isCurrentConnectionLocked(webSocket, generation, deviceUid)) {
                    activeConnectionScope
                } else {
                    null
                }
            }
            scope?.launch {
                tokenProvider?.clearToken(deviceUid)
                rejectAuthentication(webSocket, generation, deviceUid)
            }
        }
    }

    private fun launchHandshakeTimeout(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ) {
        val scope = synchronized(lifecycleLock) {
            if (isCurrentConnectionLocked(webSocket, generation, deviceUid)) {
                activeConnectionScope
            } else {
                null
            }
        } ?: return
        scope.launch {
            delay(handshakeTimeoutMillis)
            val authenticated = synchronized(lifecycleLock) {
                isCurrentConnectionLocked(webSocket, generation, deviceUid) &&
                    activeSecureSession != null &&
                    _connectionState.value is AqlWsConnectionState.Authenticated
            }
            if (!authenticated) {
                failProtocolConnection(
                    webSocket,
                    generation,
                    deviceUid,
                    AqlWsProtocolError.AUTHENTICATION_FAILED
                )
            }
        }
    }

    private fun publishOpenedIfCurrent(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        url: String
    ): Boolean = synchronized(lifecycleLock) {
        if (!claimOrVerifyCurrentConnectionLocked(webSocket, generation, deviceUid)) {
            return@synchronized false
        }
        _connectionState.value = AqlWsConnectionState.Connected(
            deviceUid = deviceUid,
            url = url,
            connectedAtMillis = clockMillis()
        )
        publishEventLocked(AqlWsEvent.Opened(deviceUid))
        true
    }

    private fun failProtocolConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        error: AqlWsProtocolError,
        frameBytes: Int? = null
    ) {
        val detached = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            val failure = AqlWsConnectionState.Failed(
                deviceUid = deviceUid,
                message = error.safeMessage,
                cause = AqlWsProtocolException(error)
            )
            detachConnectionLocked(failure).also {
                publishEventLocked(
                    AqlWsEvent.Failure(
                        deviceUid = deviceUid,
                        message = error.safeMessage,
                        throwable = AqlWsProtocolException(error),
                        frameBytes = frameBytes,
                        protocolError = error.name
                    )
                )
            }
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        if (detached.socket?.close(PROTOCOL_ERROR_CLOSE_CODE, PROTOCOL_CLOSE_REASON) != true) {
            detached.socket?.cancel()
        }
    }

    private fun finishConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        nextState: AqlWsConnectionState,
        event: AqlWsEvent
    ) {
        val detached = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) return
            detachConnectionLocked(nextState).also { publishEventLocked(event) }
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
    }

    private fun failConnectionSetup(
        deviceUid: DeviceUid,
        generation: Long,
        error: Throwable
    ) {
        val detached = synchronized(lifecycleLock) {
            if (closed || connectionGeneration != generation || activeDeviceUid != deviceUid) return
            val failure = AqlWsConnectionState.Failed(
                deviceUid = deviceUid,
                message = CONNECTION_FAILURE_MESSAGE,
                cause = error
            )
            detachConnectionLocked(failure).also {
                publishEventLocked(AqlWsEvent.Failure(deviceUid, CONNECTION_FAILURE_MESSAGE, error))
            }
        }
        detached.destroySecurityMaterial()
        detached.job?.cancel()
        detached.socket?.cancel()
    }

    private fun canSendLocked(message: AqlWsOutgoingMessage): Boolean = when (message) {
        is AqlWsOutgoingMessage.Command -> activeSocket != null &&
            activeSecureSession != null &&
            _connectionState.value is AqlWsConnectionState.Authenticated
    }

    private fun AqlWsOutgoingMessage.lifecycleInvalidatingCommandId(): String? =
        (this as? AqlWsOutgoingMessage.Command)?.id?.takeIf { commandId ->
            commandId.isNotBlank() &&
                module == AqlWsContract.MODULE_SECURITY &&
                action in TOKEN_INVALIDATING_SECURITY_ACTIONS
        }

    private fun detachForTerminalCloseLocked(): DetachedConnection? {
        if (closed) return null
        closed = true
        return detachConnectionLocked(AqlWsConnectionState.Disconnected)
    }

    private fun detachConnectionLocked(nextState: AqlWsConnectionState): DetachedConnection {
        val detached = DetachedConnection(
            socket = activeSocket,
            job = activeConnectionJob,
            pendingAuthentication = pendingAuthentication,
            secureSession = activeSecureSession
        )
        connectionGeneration += 1L
        activeSocket = null
        activeDeviceUid = null
        activeConnectionJob = null
        activeConnectionScope = null
        pendingAuthentication = null
        activeSecureSession = null
        helloReceived = false
        pendingTokenInvalidationCommandIds.clear()
        _connectionState.value = nextState
        return detached
    }

    private fun claimOrVerifyCurrentConnectionLocked(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ): Boolean {
        if (closed || connectionGeneration != generation || activeDeviceUid != deviceUid) return false
        if (activeSocket == null) activeSocket = webSocket
        return activeSocket === webSocket
    }

    private fun isCurrentConnectionLocked(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ): Boolean = !closed &&
        connectionGeneration == generation &&
        activeDeviceUid == deviceUid &&
        activeSocket === webSocket

    private fun publishEventLocked(event: AqlWsEvent) {
        eventChannel.trySend(event)
    }

    private fun sanitizedCloseReason(reason: String): String = reason
        .filterNot { char -> char.code < 0x20 || char.code == 0x7f }
        .take(MAX_CLOSE_REASON_CHARS)

    private data class DecodeState(
        val pendingAuthentication: AqlWsPendingAuthentication?,
        val secureSession: AqlWsSecureSession?
    )

    private enum class SendResult {
        Sent,
        Rejected,
        SocketFailure,
        ProtocolFailure
    }

    companion object {
        private const val NORMAL_CLOSE_CODE = 1000
        private const val PROTOCOL_ERROR_CLOSE_CODE = 1002
        private const val POLICY_VIOLATION_CLOSE_CODE = 1008
        private const val INTERNAL_ERROR_CLOSE_CODE = 1011
        private const val RECONNECT_CLOSE_REASON = "client reconnecting"
        private const val PROTOCOL_CLOSE_REASON = "protocol validation failed"
        private const val AUTH_FAILURE_CLOSE_REASON = "authentication required"
        private const val SEND_FAILURE_CLOSE_REASON = "message send failed"
        private const val CONNECTION_FAILURE_MESSAGE = "WebSocket connection failed."
        private const val MISSING_CREDENTIAL_MESSAGE = "Device authentication is required."
        private const val MAX_CLOSE_REASON_CHARS = 96
        private const val DEFAULT_HANDSHAKE_TIMEOUT_MILLIS = 5_000L
        private val TOKEN_INVALIDATING_SECURITY_ACTIONS = setOf(
            AqlWsContract.ACTION_SECURITY_UNPAIR,
            AqlWsContract.ACTION_SECURITY_RESET
        )

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
