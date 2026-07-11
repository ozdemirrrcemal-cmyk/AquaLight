package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow<AqlWsConnectionState>(
        AqlWsConnectionState.Disconnected
    )
    val connectionState: StateFlow<AqlWsConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AqlWsEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val events: SharedFlow<AqlWsEvent> = _events.asSharedFlow()

    @Volatile
    private var activeSocket: WebSocket? = null

    @Volatile
    private var activeDeviceUid: DeviceUid? = null

    private val pendingTokenInvalidationCommandIds = Collections.newSetFromMap(
        ConcurrentHashMap<String, Boolean>()
    )

    fun connect(
        deviceUid: DeviceUid,
        endpoint: DeviceRuntimeEndpoint
    ): Result<Unit> {
        return runCatching {
            val url = endpoint.toWebSocketUrl()
                ?: error("Device ${deviceUid.value} has no WebSocket endpoint")

            close()
            activeDeviceUid = deviceUid
            _connectionState.value = AqlWsConnectionState.Connecting(
                deviceUid = deviceUid,
                url = url
            )

            val request = Request.Builder()
                .url(url)
                .build()

            activeSocket = okHttpClient.newWebSocket(
                request,
                listenerFor(
                    deviceUid = deviceUid,
                    url = url
                )
            )
        }
    }

    fun send(message: AqlWsOutgoingMessage): Boolean {
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

    fun sendRaw(raw: String): Boolean {
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

    fun markAuthenticated(deviceUid: DeviceUid) {
        _connectionState.value = AqlWsConnectionState.Authenticated(
            deviceUid = deviceUid,
            authenticatedAtMillis = clockMillis()
        )
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

    fun markAuthRequired(
        deviceUid: DeviceUid,
        message: String
    ) {
        _connectionState.value = AqlWsConnectionState.AuthRequired(
            deviceUid = deviceUid,
            message = message
        )
    }

    fun close(
        code: Int = NORMAL_CLOSE_CODE,
        reason: String = NORMAL_CLOSE_REASON
    ) {
        activeSocket?.close(code, reason)
        activeSocket = null
        activeDeviceUid = null
        pendingTokenInvalidationCommandIds.clear()
        _connectionState.value = AqlWsConnectionState.Disconnected
    }

    private fun handleAuthMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?
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
                        reason = "runtime token rejected by device"
                    )
                }
            }

            is AqlWsIncomingMessage.Error -> {
                if (message.isAuthReply() || message.statusCode in AUTH_FAILURE_STATUS_CODES) {
                    clearTokenAndRequireAuth(
                        deviceUid = deviceUid,
                        reason = message.message.ifBlank { "runtime token rejected by device" }
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleTokenLifecycleMessage(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage?
    ) {
        when (message) {
            is AqlWsIncomingMessage.Response -> {
                val wasPending = pendingTokenInvalidationCommandIds.remove(message.id)
                if (wasPending && message.ok) {
                    clearTokenAndRequireAuth(
                        deviceUid = deviceUid,
                        reason = "runtime token cleared after security lifecycle change"
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
        reason: String
    ) {
        scope.launch {
            tokenProvider?.clearToken(deviceUid)
            markAuthRequired(
                deviceUid = deviceUid,
                message = reason
            )
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
        url: String
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (activeSocket != webSocket) return

                _connectionState.value = AqlWsConnectionState.Connected(
                    deviceUid = deviceUid,
                    url = url,
                    connectedAtMillis = clockMillis()
                )
                emit(AqlWsEvent.Opened(deviceUid = deviceUid))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (activeSocket != webSocket) return

                val parsed = messageParser.parse(text).getOrNull()
                handleAuthMessage(
                    deviceUid = deviceUid,
                    message = parsed
                )
                handleTokenLifecycleMessage(
                    deviceUid = deviceUid,
                    message = parsed
                )
                emit(
                    AqlWsEvent.Message(
                        deviceUid = deviceUid,
                        parsed = parsed
                    )
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket != webSocket) return

                activeSocket = null
                activeDeviceUid = null
                pendingTokenInvalidationCommandIds.clear()
                _connectionState.value = AqlWsConnectionState.Disconnected
                emit(
                    AqlWsEvent.Closed(
                        deviceUid = deviceUid,
                        code = code,
                        reason = reason
                    )
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (activeSocket != webSocket) return

                activeSocket = null
                activeDeviceUid = null
                pendingTokenInvalidationCommandIds.clear()
                _connectionState.value = AqlWsConnectionState.Failed(
                    deviceUid = deviceUid,
                    message = t.message.orEmpty(),
                    cause = t
                )
                emit(
                    AqlWsEvent.Failure(
                        deviceUid = deviceUid,
                        message = t.message.orEmpty(),
                        throwable = t
                    )
                )
            }
        }
    }

    private fun emit(event: AqlWsEvent) {
        scope.launch {
            _events.emit(event)
        }
    }

    companion object {
        private const val EVENT_BUFFER_CAPACITY = 128
        private const val NORMAL_CLOSE_CODE = 1000
        private const val NORMAL_CLOSE_REASON = "client closed"
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
                .pingInterval(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
