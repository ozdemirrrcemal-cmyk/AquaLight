package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceCoolingRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(deviceUid: DeviceUid): DeviceCoolingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceCoolingRuntimeContract.Action.STATUS_GET
        )
    }

    fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceCoolingConfigApplyPayload
    ): DeviceCoolingCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceCoolingRuntimeContract.Action.CONFIG_APPLY,
            data = payload.toJson()
        )
    }

    fun setMode(
        deviceUid: DeviceUid,
        mode: DeviceCoolingMode,
        save: Boolean = true
    ): DeviceCoolingCommandResult {
        return applyConfig(
            deviceUid = deviceUid,
            payload = DeviceCoolingConfigApplyPayload(mode = mode, save = save)
        )
    }

    fun setTemperatureRange(
        deviceUid: DeviceUid,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean = true
    ): DeviceCoolingCommandResult {
        return applyConfig(
            deviceUid = deviceUid,
            payload = DeviceCoolingConfigApplyPayload(
                minTemperatureC = minTemperatureC,
                maxTemperatureC = maxTemperatureC,
                save = save
            )
        )
    }

    fun setAuto(deviceUid: DeviceUid, save: Boolean = true): DeviceCoolingCommandResult {
        return setMode(deviceUid, DeviceCoolingMode.AUTO, save)
    }

    fun setOn(deviceUid: DeviceUid, save: Boolean = true): DeviceCoolingCommandResult {
        return setMode(deviceUid, DeviceCoolingMode.ON, save)
    }

    fun setOff(deviceUid: DeviceUid, save: Boolean = true): DeviceCoolingCommandResult {
        return setMode(deviceUid, DeviceCoolingMode.OFF, save)
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceCoolingCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceCoolingCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val sent = commandClient.command(
            module = DeviceCoolingRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceCoolingCommandResult(
            sent = sent,
            action = action,
            errorMessage = if (sent) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(commandClient: AqlWsCommandClient): DeviceCoolingRuntimeRepository {
            return DeviceCoolingRuntimeRepository { commandClient }
        }
    }
}
