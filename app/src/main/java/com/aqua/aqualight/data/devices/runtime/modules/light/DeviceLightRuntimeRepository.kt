package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceLightRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceLightStatus> =
        commandGateway.execute(deviceUid, DeviceLightStatusGetCommand)

    suspend fun setManual(
        deviceUid: DeviceUid,
        payload: DeviceLightManualSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightManualSetResult> =
        commandGateway.execute(deviceUid, DeviceLightManualSetCommand(payload))

    suspend fun clearManual(
        deviceUid: DeviceUid,
        channelKeys: List<String> = emptyList()
    ): DeviceRuntimeCommandOutcome<DeviceLightManualSetResult> = setManual(
        deviceUid = deviceUid,
        payload = DeviceLightManualSetPayload(
            clear = true,
            durationMs = null,
            channels = channelKeys.map { key ->
                DeviceLightManualChannelPayload(channelKey = key, percent = 0.0)
            }
        )
    )

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        payload: DeviceLightChannelRegimeSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightChannelRegimeSetResult> =
        commandGateway.execute(deviceUid, DeviceLightChannelRegimeSetCommand(payload))

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceLightRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceLightChannelRegimeSetResult> =
        setChannelRegime(
            deviceUid,
            DeviceLightChannelRegimeSetPayload(channelKey, regime, save)
        )

    suspend fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramApplyResult> =
        commandGateway.execute(deviceUid, DeviceLightProgramApplyCommand(payload))

    suspend fun deleteProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramDeletePayload
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramDeleteResult> =
        commandGateway.execute(deviceUid, DeviceLightProgramDeleteCommand(payload))

    suspend fun deleteProgram(
        deviceUid: DeviceUid,
        programIndex: Int,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceLightProgramDeleteResult> =
        deleteProgram(deviceUid, DeviceLightProgramDeletePayload(programIndex, save))
}

private data object DeviceLightStatusGetCommand : DeviceRuntimeCommand<DeviceLightStatus> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceLightStatus {
        require(response.statusCode == 200)
        return DeviceLightStatusParser.parse(response.data)
    }
}

private class DeviceLightManualSetCommand(
    private val payload: DeviceLightManualSetPayload
) : DeviceRuntimeCommand<DeviceLightManualSetResult> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_MANUAL_SET
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceLightManualSetResult {
        require(response.statusCode == 200)
        return DeviceLightStatusParser.parseManualSetResult(response.data)
    }
}

private class DeviceLightChannelRegimeSetCommand(
    private val payload: DeviceLightChannelRegimeSetPayload
) : DeviceRuntimeCommand<DeviceLightChannelRegimeSetResult> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_CHANNEL_REGIME_SET
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceLightChannelRegimeSetResult {
        require(response.statusCode == 200)
        return DeviceLightStatusParser.parseChannelRegimeSetResult(response.data)
    }
}

private class DeviceLightProgramApplyCommand(
    private val payload: DeviceLightProgramApplyPayload
) : DeviceRuntimeCommand<DeviceLightProgramApplyResult> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_PROGRAM_APPLY
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceLightProgramApplyResult = DeviceLightStatusParser.parseProgramApplyResult(
        data = response.data,
        statusCode = response.statusCode
    )
}

private class DeviceLightProgramDeleteCommand(
    private val payload: DeviceLightProgramDeletePayload
) : DeviceRuntimeCommand<DeviceLightProgramDeleteResult> {
    override val module: String = AqlWsContract.MODULE_LIGHT
    override val action: String = AqlWsContract.ACTION_LIGHT_PROGRAM_DELETE
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceLightProgramDeleteResult {
        require(response.statusCode == 200)
        return DeviceLightStatusParser.parseProgramDeleteResult(response.data)
    }
}
