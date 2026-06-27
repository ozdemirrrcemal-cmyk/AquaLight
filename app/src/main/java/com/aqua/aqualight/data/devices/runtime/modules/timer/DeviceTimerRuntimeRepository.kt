package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceTimerRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceTimerCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimerRuntimeContract.Action.STATUS_GET
        )
    }

    fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimerConfigApplyPayload
    ): DeviceTimerCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimerRuntimeContract.Action.CONFIG_APPLY,
            data = payload.toJson()
        )
    }

    fun setChannel(
        deviceUid: DeviceUid,
        payload: DeviceTimerChannelSetPayload
    ): DeviceTimerCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceTimerRuntimeContract.Action.CHANNEL_SET,
            data = payload.toJson()
        )
    }

    fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceTimerRegime,
        save: Boolean = true
    ): DeviceTimerCommandResult {
        return setChannel(
            deviceUid = deviceUid,
            payload = DeviceTimerChannelSetPayload(
                channelKey = channelKey,
                regime = regime,
                save = save
            )
        )
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceTimerCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceTimerCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val sent = commandClient.command(
            module = DeviceTimerRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceTimerCommandResult(
            sent = sent,
            action = action,
            errorMessage = if (sent) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(
            commandClient: AqlWsCommandClient
        ): DeviceTimerRuntimeRepository {
            return DeviceTimerRuntimeRepository { commandClient }
        }
    }
}
