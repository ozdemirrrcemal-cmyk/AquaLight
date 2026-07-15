package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-explicit WebSocket transport used by the device runtime layer.
 *
 * [disconnect] ends only the current socket session and keeps the transport reusable.
 * [shutdown] is the suspending terminal barrier: it must cancel the active socket,
 * finish every coroutine/pending operation owned by the transport and return only
 * after no callback can publish another state or event.
 * [close] is the immediate terminal fallback required by [AutoCloseable].
 */
interface AqlWsTransport : AutoCloseable {
    val connectionState: StateFlow<AqlWsConnectionState>

    /**
     * Reliable single-consumer runtime event stream. Implementations must not drop
     * lifecycle events merely because the consumer has not started collecting yet.
     */
    val events: Flow<AqlWsEvent>

    fun connect(
        deviceUid: DeviceUid,
        endpoint: DeviceRuntimeEndpoint
    ): Result<Unit>

    fun send(message: AqlWsOutgoingMessage): Boolean

    fun sendRaw(raw: String): Boolean

    fun markAuthenticated(deviceUid: DeviceUid)

    fun markAuthRequired(
        deviceUid: DeviceUid,
        message: String
    )

    fun disconnect(
        code: Int = 1000,
        reason: String = "client disconnected"
    )

    /**
     * Terminal, awaitable shutdown used at device deletion and owner teardown.
     */
    suspend fun shutdown() {
        close()
    }

    override fun close()
}
