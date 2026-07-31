package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceCoolingRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceCoolingStatus> =
        commandGateway.execute(deviceUid, DeviceCoolingStatusGetCommand)

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceCoolingConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        commandGateway.execute(deviceUid, DeviceCoolingConfigApplyCommand(payload))

    suspend fun setMode(
        deviceUid: DeviceUid,
        mode: DeviceCoolingMode,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid = deviceUid,
        payload = DeviceCoolingConfigApplyPayload(mode = mode, save = save)
    )

    suspend fun setTemperatureRange(
        deviceUid: DeviceUid,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid = deviceUid,
        payload = DeviceCoolingConfigApplyPayload(
            minTemperatureC = minTemperatureC,
            maxTemperatureC = maxTemperatureC,
            save = save
        )
    )

    suspend fun setFanDisplayNames(
        deviceUid: DeviceUid,
        fans: List<DeviceCoolingFanConfig>,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> = applyConfig(
        deviceUid = deviceUid,
        payload = DeviceCoolingConfigApplyPayload(
            fans = fans,
            save = save
        )
    )

    suspend fun setAuto(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.AUTO, save)

    suspend fun setOn(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.ON, save)

    suspend fun setOff(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult> =
        setMode(deviceUid, DeviceCoolingMode.OFF, save)
}

private data object DeviceCoolingStatusGetCommand : DeviceRuntimeCommand<DeviceCoolingStatus> {
    override val module: String = AqlWsContract.MODULE_COOLING
    override val action: String = AqlWsContract.ACTION_COOLING_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceCoolingStatus {
        require(response.statusCode == HTTP_OK)
        return DeviceCoolingStatusParser.parse(response.data)
    }
}

private class DeviceCoolingConfigApplyCommand(
    private val payload: DeviceCoolingConfigApplyPayload
) : DeviceRuntimeCommand<DeviceCoolingConfigApplyResult> {
    override val module: String = AqlWsContract.MODULE_COOLING
    override val action: String = AqlWsContract.ACTION_COOLING_CONFIG_APPLY
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceCoolingConfigApplyResult {
        require(response.statusCode == HTTP_OK)
        return DeviceCoolingStatusParser.parseConfigApply(response.data)
    }
}

private const val HTTP_OK = 200
