package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceLightRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(deviceUid: DeviceUid): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.STATUS_GET
        )
    }

    fun requestTemperatureProtectionStatus(deviceUid: DeviceUid): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET
        )
    }

    fun setTemperatureProtection(
        deviceUid: DeviceUid,
        payload: DeviceLightTemperatureProtectionSetPayload
    ): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET,
            data = payload.toJson()
        )
    }

    fun setManual(
        deviceUid: DeviceUid,
        payload: DeviceLightManualSetPayload
    ): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.MANUAL_SET,
            data = payload.toJson()
        )
    }

    fun clearManual(
        deviceUid: DeviceUid,
        channelKeys: List<String> = emptyList()
    ): DeviceLightCommandResult {
        return setManual(
            deviceUid = deviceUid,
            payload = DeviceLightManualSetPayload(
                clear = true,
                durationMs = null,
                channels = channelKeys.map { key ->
                    DeviceLightManualChannelPayload(
                        channelKey = key,
                        percent = 0.0
                    )
                }
            )
        )
    }

    fun setChannelRegime(
        deviceUid: DeviceUid,
        payload: DeviceLightChannelRegimeSetPayload
    ): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET,
            data = payload.toJson()
        )
    }

    fun setChannelRegime(
        deviceUid: DeviceUid,
        channelKey: String,
        regime: DeviceLightRegime,
        save: Boolean = true
    ): DeviceLightCommandResult {
        return setChannelRegime(
            deviceUid = deviceUid,
            payload = DeviceLightChannelRegimeSetPayload(
                channelKey = channelKey,
                regime = regime,
                save = save
            )
        )
    }

    fun applyProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramApplyPayload
    ): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.PROGRAM_APPLY,
            data = payload.toJson()
        )
    }

    fun deleteProgram(
        deviceUid: DeviceUid,
        payload: DeviceLightProgramDeletePayload
    ): DeviceLightCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceLightRuntimeContract.Action.PROGRAM_DELETE,
            data = payload.toJson()
        )
    }

    fun deleteProgram(
        deviceUid: DeviceUid,
        programIndex: Int,
        save: Boolean = true
    ): DeviceLightCommandResult {
        return deleteProgram(
            deviceUid = deviceUid,
            payload = DeviceLightProgramDeletePayload(
                programIndex = programIndex,
                save = save
            )
        )
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceLightCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceLightCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val messageId = commandClient.command(
            module = DeviceLightRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceLightCommandResult(
            sent = messageId != null,
            action = action,
            messageId = messageId.orEmpty(),
            errorMessage = if (messageId != null) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(commandClient: AqlWsCommandClient): DeviceLightRuntimeRepository {
            return DeviceLightRuntimeRepository { commandClient }
        }
    }
}
