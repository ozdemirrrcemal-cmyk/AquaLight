package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class AqlWsClient(
    private val okHttpClient: OkHttpClient = defaultOkHttpClient(),
    private val messageParser: AqlWsMessageParser = AqlWsMessageParser(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val tokenProvider: AqlWsTokenProvider? = defaultTokenProvider
) : AqlWsTransport {
    private data class DetachedConnection(
        val socket: WebSocket?,
        val job: Job?
    )

    private val lifecycleLock = Any()
    private val clientJob = SupervisorJob()

    private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
        AqlWsConnectionState.Disconnected
    )
    override val connectionState: StateFlow<AqlWsConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    override val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

    @Volatile
    private var activeSocket: WebSocket? = null

    @Volatile
    private var activeDeviceUid: DeviceUid? = null

    @Volatile
    private var activeConnectionJob: Job? = null

    @Volatile
    private var activeConnectionScope: CoroutineScope? = null

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
    ): Result<Unit> {
        return runCatching {
            val url = endpoint.toWebSocketUrl()
                ?: error("Device ${deviceUid.value} has no WebSocket endpoint")
            val request = Request.Builder()
                .url(url)
                .build()

            disconnect(
                code = NORMAL_CLOSE_CODE,
                reason = RECONNECT_CLOSE_REASON
            )

            val generation: Long
            val listener: WebSocketListener

            synchronized(lifecycleLock) {
                check(!closed) { "WebSocket client is closed." }

                val connectionJob = SupervisorJob(clientJob)
                activeConnectionJob = connectionJob
                activeConnectionScope = CoroutineScope(connectionJob + Dispatchers.IO)
                generation = ++connectionGeneration
                activeDeviceUid = deviceUid
                _connectionState.value = AqlWsConnectionState.Connecting(
                    deviceUid = deviceUid,
                    url = url
                )
                listener = listenerFor(
                    deviceUid = deviceUid,
                    url = url,
                    generation = generation
                )
            }

            val socket = okHttpClient.newWebSocket(request, listener)
            val rejectSocket = synchronized(lifecycleLock) {
                if (
                    closed ||
                    connectionGeneration != generation ||
                    activeDeviceUid != deviceUid
                ) {
                    true
                } else {
                    if (activeSocket == null) {
                        activeSocket = socket
                    }
                    activeSocket !== socket
                }
            }

            if (rejectSocket) {
                socket.close(NORMAL_CLOSE_CODE, STALE_SOCKET_CLOSE_REASON)
            }
        }
    }

    override fun send(message: AqlWsOutgoingMessage): Boolean {
        if (!canSend(message)) {
            return false
        }

        val sent = activeSocket?.send(message.toJsonString()) == true
        if (sent) {
            message.lifecycleInvalidatingCommandId()?.let { commandId ->
                pendingTokenInvalidationCommandIds.add(commandId)
            }
        }
        return sent
    }

    override fun sendRaw(raw: String): Boolean {
        if (!canSendRaw(raw)) {
            return false
        }

        val lifecycleInvalidatingCommandId = raw.lifecycleInvalidatingCommandId()
        val sent = activeSocket?.send(raw) == true
        if (sent && lifecycleInvalidatingCommandId != null) {
            pendingTokenInvalidationCommandIds.add(lifecycleInvalidatingCommandId)
        }
        return sent
    }

    override fun markAuthenticated(deviceUid: DeviceUid) {
        synchronized(lifecycleLock) {
            if (closed || activeDeviceUid != deviceUid || activeSocket == null) {
                return
            }
            _connectionState.value = AqlWsConnectionState.Authenticated(
                deviceUid = deviceUid,
                authenticatedAtMillis = clockMillis()
            )
        }
    }

    private fun canSend(message: AqlWsOutgoingMessage): Boolean {
        return when (message) {
            is AqlWsOutgoingMessage.Auth -> activeSocket != null
            is AqlWsOutgoingMessage.Ping -> activeSocket != null
            is AqlWsOutgoingMessage.Command -> activeSocket != null &&
                (
                    message.isPublicCommand() ||
                        (message.isAuthenticatedCommand() && isActiveDeviceAuthenticated())
                    )
        }
    }

    private fun canSendRaw(raw: String): Boolean {
        val json = runCatching {
            JSONObject(raw)
        }.getOrNull() ?: return false

        val type = json.optString("type").trim()
        val module = json.optString("module").trim()
        val action = json.optString("action").trim()

        return when (type) {
            AqlWsContract.TYPE_AUTH,
            AqlWsContract.TYPE_PING -> activeSocket != null
            AqlWsContract.TYPE_COMMAND -> activeSocket != null &&
                (
                    AqlWsContract.isPublicCommand(module, action) ||
                        (AqlWsContract.isAuthenticatedCommand(module, action) && isActiveDeviceAuthenticated())
                    )
            else -> false
        }
    }

    private fun AqlWsOutgoingMessage.Command.isPublicCommand(): Boolean {
        return AqlWsContract.isPublicCommand(
            module = module,
            action = action
        )
    }

    private fun AqlWsOutgoingMessage.Command.isAuthenticatedCommand(): Boolean {
        return AqlWsContract.isAuthenticatedCommand(
            module = module,
            action = action
        )
    }

    private fun AqlWsOutgoingMessage.lifecycleInvalidatingCommandId(): String? {
        return when (this) {
            is AqlWsOutgoingMessage.Command -> id.takeIf { commandId ->
                commandId.isNotBlank() &&
                    module == AqlWsContract.MODULE_SECURITY &&
                    action in TOKEN_INVALIDATING_SECURITY_ACTIONS
            }

            else -> null
        }
    }

    private fun String.lifecycleInvalidatingCommandId(): String? {
        val json = runCatching {
            JSONObject(this)
        }.getOrNull() ?: return null

        val id = json.optString("id").trim()
        val type = json.optString("type").trim()
        val module = json.optString("module").trim()
        val action = json.optString("action").trim()

        return id.takeIf { commandId ->
            commandId.isNotBlank() &&
                type == AqlWsContract.TYPE_COMMAND &&
                module == AqlWsContract.MODULE_SECURITY &&
                action in TOKEN_INVALIDATING_SECURITY_ACTIONS
        }
    }

    private fun isActiveDeviceAuthenticated(): Boolean {
        val deviceUid = activeDeviceUid ?: return false
        val state = _connectionState.value
        return state is AqlWsConnectionState.Authenticated && state.deviceUid == deviceUid
    }

    override fun markAuthRequired(
        deviceUid: DeviceUid,
        message: String
    ) {
        synchronized(lifecycleLock) {
            if (closed || activeDeviceUid != deviceUid || activeSocket == null) {
                return
            }
            _connectionState.value = AqlWsConnectionState.AuthRequired(
                deviceUid = deviceUid,
                message = message
            )
        }
    }

    override fun disconnect(
        code: Int,
        reason: String
    ) {
        val detached = synchronized(lifecycleLock) {
            detachConnectionLocked(
                nextState = AqlWsConnectionState.Disconnected
            )
        }
        detached.job?.cancel()
        detached.socket?.close(code, reason)
    }

    override fun close() {
        val detached = synchronized(lifecycleLock) {
            if (closed) {
                return
            }
            closed = true
            detachConnectionLocked(
                nextState = AqlWsConnectionState.Disconnected
            )
        }
        detached.job?.cancel()
        detached.socket?.close(NORMAL_CLOSE_CODE, NORMAL_CLOSE_REASON)
        clientJob.cancel()
    }

    private fun detachConnectionLocked(
        nextState: AqlWsConnectionState
    ): DetachedConnection {
        val detached = DetachedConnection(
            socket = activeSocket,
            job = activeConnectionJob
        )
        connectionGeneration += 1L
        activeSocket = null
        activeDeviceUid = null
        activeConnectionJob = null
        activeConnectionScope = null
        pendingTokenInvalidationCommandIds.clear()
        _connectionState.value = nextState
        return detached
    }

    private fun handleAuthMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?,
        webSocket: WebSocket,
        generation: Long
    ) {
        when (message) {
            is AqlWsIncomingMessage.Response -> {
                if (!message.isAuthReply()) {
                    return
                }

                if (message.ok) {
                    markAuthenticated(deviceUid)
                } else if (message.statusCode in AUTH_FAILURE_STATUS_CODES) {
                    clearTokenAndRequireAuth(
                        deviceUid = deviceUid,
                        reason = "runtime token rejected by device",
                        webSocket = webSocket,
                        generation = generation
                    )
                }
            }

            is AqlWsIncomingMessage.Error -> {
                if (message.isAuthReply() || message.statusCode in AUTH_FAILURE_STATUS_CODES) {
                    clearTokenAndRequireAuth(
                        deviceUid = deviceUid,
                        reason = message.message.ifBlank { "runtime token rejected by device" },
                        webSocket = webSocket,
                        generation = generation
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleTokenLifecycleMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?,
        webSocket: WebSocket,
        generation: Long
    ) {
        when (message) {
            is AqlWsIncomingMessage.Response -> {
                val wasPending = pendingTokenInvalidationCommandIds.remove(message.id)
                if (wasPending && message.ok) {
                    clearTokenAndRequireAuth(
                        deviceUid = deviceUid,
                        reason = "runtime token cleared after security lifecycle change",
                        webSocket = webSocket,
                        generation = generation
                    )
                }
            }

            is AqlWsIncomingMessage.Error -> {
                pendingTokenInvalidationCommandIds.remove(message.id)
            }

            else -> Unit
        }
    }

    private fun clearTokenAndRequireAuth(
        deviceUid: DeviceUid,
        reason: String,
        webSocket: WebSocket,
        generation: Long
    ) {
        val connectionScope = synchronized(lifecycleLock) {
            if (isCurrentConnectionLocked(webSocket, generation, deviceUid)) {
                activeConnectionScope
            } else {
                null
            }
        } ?: return

        connectionScope.launch {
            tokenProvider?.clearToken(deviceUid)
            if (isCurrentConnection(webSocket, generation, deviceUid)) {
                markAuthRequired(
                    deviceUid = deviceUid,
                    message = reason
                )
            }
        }
    }

    private fun AqlWsIncomingMessage.Response.isAuthReply(): Boolean {
        return id.startsWith(AUTH_ID_PREFIX) ||
            (module == AqlWsContract.MODULE_SECURITY && action == AqlWsContract.TYPE_AUTH)
    }

    private fun AqlWsIncomingMessage.Error.isAuthReply(): Boolean {
        return id.startsWith(AUTH_ID_PREFIX) || field.equals(AUTH_FIELD_TOKEN, ignoreCase = true)
    }

    private fun listenerFor(
        deviceUid: DeviceUid,
        url: String,
        generation: Long
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!claimOrVerifyCurrentConnection(webSocket, generation, deviceUid)) {
                    webSocket.close(NORMAL_CLOSE_CODE, STALE_SOCKET_CLOSE_REASON)
                    return
                }
                _connectionState.value = AqlWsConnectionState.Connected(
                    deviceUid = deviceUid,
                    url = url,
                    connectedAtMillis = clockMillis()
                )
                _events.tryEmit(AqlWsEvent.Opened(deviceUid = deviceUid))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrentConnection(webSocket, generation, deviceUid)) {
                    return
                }
                val parsed = messageParser.parse(text).getOrNull()
                handleAuthMessage(
                    deviceUid = deviceUid,
                    message = parsed,
                    webSocket = webSocket,
                    generation = generation
                )
                handleTokenLifecycleMessage(
                    deviceUid = deviceUid,
                    message = parsed,
                    webSocket = webSocket,
                    generation = generation
                )
                _events.tryEmit(
                    AqlWsEvent.Message(
                        deviceUid = deviceUid,
                        parsed = parsed
                    )
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finishConnection(
                    webSocket = webSocket,
                    generation = generation,
                    deviceUid = deviceUid,
                    nextState = AqlWsConnectionState.Disconnected,
                    event = AqlWsEvent.Closed(
                        deviceUid = deviceUid,
                        code = code,
                        reason = reason
                    )
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finishConnection(
                    webSocket = webSocket,
                    generation = generation,
                    deviceUid = deviceUid,
                    nextState = AqlWsConnectionState.Failed(
                        deviceUid = deviceUid,
                        message = t.message.orEmpty(),
                        cause = t
                    ),
                    event = AqlWsEvent.Failure(
                        deviceUid = deviceUid,
                        message = t.message.orEmpty(),
                        throwable = t
                    )
                )
            }
        }
    }

    private fun claimOrVerifyCurrentConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ): Boolean {
        return synchronized(lifecycleLock) {
            if (
                closed ||
                connectionGeneration != generation ||
                activeDeviceUid != deviceUid
            ) {
                false
            } else {
                if (activeSocket == null) {
                    activeSocket = webSocket
                }
                activeSocket === webSocket
            }
        }
    }

    private fun isCurrentConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ): Boolean {
        return synchronized(lifecycleLock) {
            isCurrentConnectionLocked(webSocket, generation, deviceUid)
        }
    }

    private fun isCurrentConnectionLocked(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid
    ): Boolean {
        return !closed &&
            connectionGeneration == generation &&
            activeDeviceUid == deviceUid &&
            activeSocket === webSocket
    }

    private fun finishConnection(
        webSocket: WebSocket,
        generation: Long,
        deviceUid: DeviceUid,
        nextState: AqlWsConnectionState,
        event: AqlWsEvent
    ) {
        val detached = synchronized(lifecycleLock) {
            if (!isCurrentConnectionLocked(webSocket, generation, deviceUid)) {
                return
            }
            _connectionState.value = nextState
            _events.tryEmit(event)
            detachConnectionLocked(nextState = nextState)
        }
        detached.job?.cancel()
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 128
        private const val NORMAL_CLOSE_CODE = 1000
        private const val NORMAL_CLOSE_REASON = "client closed"
        private const val RECONNECT_CLOSE_REASON = "client reconnecting"
        private const val STALE_SOCKET_CLOSE_REASON = "stale socket"
        private const val AUTH_ID_PREFIX = "auth-"
        private const val AUTH_FIELD_TOKEN = "token"
        private val AUTH_FAILURE_STATUS_CODES = setOf(401, 403)
        private val TOKEN_INVALIDATING_SECURITY_ACTIONS = setOf(
            AqlWsContract.ACTION_SECURITY_UNPAIR,
            AqlWsContract.ACTION_SECURITY_RESET
        )

        @Volatile
        private var defaultTokenProvider: AqlWsTokenProvider? = null

        fun installDefaultTokenProvider(tokenProvider: AqlWsTokenProvider) {
            defaultTokenProvider = tokenProvider
        }

        fun defaultOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
