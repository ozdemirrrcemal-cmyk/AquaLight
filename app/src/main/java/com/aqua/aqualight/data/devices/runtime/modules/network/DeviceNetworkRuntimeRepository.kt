package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceNetworkRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid,
        timeoutMillis: Long = DEVICE_RUNTIME_DEFAULT_TIMEOUT_MILLIS
    ): DeviceRuntimeCommandOutcome<DeviceNetworkStatus> = commandGateway.execute(
        deviceUid = deviceUid,
        command = DeviceNetworkStatusGetCommand,
        timeoutMillis = timeoutMillis
    )
}

private data object DeviceNetworkStatusGetCommand : DeviceRuntimeCommand<DeviceNetworkStatus> {
    override val module: String = AqlWsContract.MODULE_NETWORK
    override val action: String = AqlWsContract.ACTION_NETWORK_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceNetworkStatus {
        require(response.statusCode == 200)
        return DeviceNetworkStatusParser.parse(response.data)
    }
}
