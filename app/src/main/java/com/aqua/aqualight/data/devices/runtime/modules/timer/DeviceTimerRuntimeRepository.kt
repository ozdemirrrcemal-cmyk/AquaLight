package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceTimerRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimerStatus> =
        commandGateway.execute(deviceUid, DeviceTimerStatusGetCommand)

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimerConfigApplyPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerConfigApplyResult> =
        commandGateway.execute(deviceUid, DeviceTimerConfigApplyCommand(payload))

    suspend fun setChannel(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> =
        commandGateway.execute(deviceUid, DeviceTimerChannelSetCommand(payload))

    suspend fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceTimerRegime,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimerChannelSetResult> = setChannel(
        deviceUid = deviceUid,
        payload = DeviceTimerChannelSetPayload(
            channelKey = channelKey,
            regime = regime,
            save = save
        )
    )
}

private data object DeviceTimerStatusGetCommand : DeviceRuntimeCommand<DeviceTimerStatus> {
    override val module: String = AqlWsContract.MODULE_TIMER
    override val action: String = AqlWsContract.ACTION_TIMER_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceTimerStatus {
        require(response.statusCode == HTTP_OK)
        return DeviceTimerStatusParser.parse(response.data)
    }
}

private class DeviceTimerConfigApplyCommand(
    private val payload: DeviceTimerConfigApplyPayload
) : DeviceRuntimeCommand<DeviceTimerConfigApplyResult> {
    override val module: String = AqlWsContract.MODULE_TIMER
    override val action: String = AqlWsContract.ACTION_TIMER_CONFIG_APPLY
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceTimerConfigApplyResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimerStatusParser.parseConfigApply(response.data)
    }
}

private class DeviceTimerChannelSetCommand(
    private val payload: DeviceTimerChannelSetPayload
) : DeviceRuntimeCommand<DeviceTimerChannelSetResult> {
    override val module: String = AqlWsContract.MODULE_TIMER
    override val action: String = AqlWsContract.ACTION_TIMER_CHANNEL_SET
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceTimerChannelSetResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimerStatusParser.parseChannelSet(response.data)
    }
}

private const val HTTP_OK = 200
