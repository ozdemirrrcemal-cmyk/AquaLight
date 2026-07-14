package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-explicit WebSocket transport used by the device runtime layer.
 *
 * [disconnect] ends only the current socket session and keeps the transport reusable.
 * [close] is terminal and must release every socket, coroutine and pending operation owned
 * by the transport.
 */
interface AqlWsTransport : AutoCloseable {
    val connectionState: StateFlow<AqlWsConnectionState>
    val events: SharedFlow<AqlWsEvent>

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

    override fun close()
}
