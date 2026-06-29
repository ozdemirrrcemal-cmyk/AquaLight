package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
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
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
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
        return activeSocket?.send(message.toJsonString()) == true
    }

    fun sendRaw(raw: String): Boolean {
        if (!canSendRaw(raw)) {
            return false
        }
        return activeSocket?.send(raw) == true
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
            is AqlWsOutgoingMessage.Command -> isActiveDeviceAuthenticated()
        }
    }

    private fun canSendRaw(raw: String): Boolean {
        val type = runCatching {
            JSONObject(raw).optString("type").trim()
        }.getOrNull().orEmpty()

        return when (type) {
            AqlWsContract.TYPE_AUTH,
            AqlWsContract.TYPE_PING -> activeSocket != null
            AqlWsContract.TYPE_COMMAND -> isActiveDeviceAuthenticated()
            else -> false
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
        _connectionState.value = AqlWsConnectionState.Disconnected
    }

    private fun listenerFor(
        deviceUid: DeviceUid,
        url: String
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = AqlWsConnectionState.Connected(
                    deviceUid = deviceUid,
                    url = url,
                    connectedAtMillis = clockMillis()
                )
                emit(AqlWsEvent.Opened(deviceUid = deviceUid))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = messageParser.parse(text).getOrNull()
                emit(
                    AqlWsEvent.Message(
                        deviceUid = deviceUid,
                        parsed = parsed
                    )
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (activeSocket == webSocket) {
                    activeSocket = null
                    activeDeviceUid = null
                    _connectionState.value = AqlWsConnectionState.Disconnected
                }
                emit(
                    AqlWsEvent.Closed(
                        deviceUid = deviceUid,
                        code = code,
                        reason = reason
                    )
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (activeSocket == webSocket) {
                    activeSocket = null
                    _connectionState.value = AqlWsConnectionState.Failed(
                        deviceUid = deviceUid,
                        message = t.message.orEmpty(),
                        cause = t
                    )
                }
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
