package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceLightTemperatureProtectionRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    fun requestStatus(deviceUid: DeviceUid): DeviceLightCommandResult = send(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_STATUS_GET
    )

    fun setThreshold(
        deviceUid: DeviceUid,
        payload: DeviceLightTemperatureProtectionSetPayload
    ): DeviceLightCommandResult = send(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET,
        data = payload.toJson()
    )

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceLightCommandResult {
        val commandClient = commandClientProvider(deviceUid)
            ?: return DeviceLightCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )

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
        fun singleSession(
            commandClient: AqlWsCommandClient
        ): DeviceLightTemperatureProtectionRuntimeRepository =
            DeviceLightTemperatureProtectionRuntimeRepository { commandClient }
    }
}
