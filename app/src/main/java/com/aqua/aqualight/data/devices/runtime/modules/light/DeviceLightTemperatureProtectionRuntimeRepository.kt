package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceLightTemperatureProtectionRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionStatus> =
        commandGateway.execute(deviceUid, DeviceLightTemperatureProtectionStatusGetCommand)

    suspend fun setThreshold(
        deviceUid: DeviceUid,
        payload: DeviceLightTemperatureProtectionSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightTemperatureProtectionSetResult> =
        commandGateway.execute(
            deviceUid,
            DeviceLightTemperatureProtectionSetCommand(payload)
        )
}

private data object DeviceLightTemperatureProtectionStatusGetCommand :
    DeviceRuntimeCommand<DeviceLightTemperatureProtectionStatus> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String =
        AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceLightTemperatureProtectionStatus {
        require(response.statusCode == 200)
        return DeviceLightTemperatureProtectionParser.parseStatus(response.data).getOrThrow()
    }
}

private class DeviceLightTemperatureProtectionSetCommand(
    private val payload: DeviceLightTemperatureProtectionSetPayload
) : DeviceRuntimeCommand<DeviceLightTemperatureProtectionSetResult> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_TEMPERATURE_PROTECTION_SET
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceLightTemperatureProtectionSetResult {
        require(response.statusCode == 200)
        return DeviceLightTemperatureProtectionParser.parseSetResult(response.data).getOrThrow()
    }
}
