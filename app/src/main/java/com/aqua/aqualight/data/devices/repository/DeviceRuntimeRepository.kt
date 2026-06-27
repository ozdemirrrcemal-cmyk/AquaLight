package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsEvent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceRuntimeRepository(
    private val wsClient: AqlWsClient = AqlWsClient()
) {
    val connectionState: StateFlow<AqlWsConnectionState> = wsClient.connectionState
    val events: SharedFlow<AqlWsEvent> = wsClient.events

    private val commandClient = AqlWsCommandClient(wsClient)

    fun connect(snapshot: DeviceSnapshot): Result<Unit> {
        return wsClient.connect(
            deviceUid = snapshot.deviceUid,
            endpoint = snapshot.endpoint
        )
    }

    fun commandClient(): AqlWsCommandClient {
        return commandClient
    }

    fun close() {
        wsClient.close()
    }
}
