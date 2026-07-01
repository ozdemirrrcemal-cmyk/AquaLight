package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceFirmwareRuntimeRepository(
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {

    fun requestStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceFirmwareRuntimeContract.Action.STATUS_GET
        )
    }

    fun requestOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceFirmwareRuntimeContract.Action.OTA_STATUS
        )
    }

    fun startOta(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload
    ): DeviceFirmwareCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceFirmwareRuntimeContract.Action.OTA_START,
            data = payload.toJson()
        )
    }

    fun startUpdate(plan: DeviceFirmwareUpdatePlan): DeviceFirmwareCommandResult {
        return startOta(
            deviceUid = plan.deviceUid,
            payload = plan.payload
        )
    }

    fun clearOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult {
        return send(
            deviceUid = deviceUid,
            action = DeviceFirmwareRuntimeContract.Action.OTA_CLEAR
        )
    }

    private fun send(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceFirmwareCommandResult {
        val commandClient = commandClientProvider(deviceUid)

        if (commandClient == null) {
            return DeviceFirmwareCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
        }

        val messageId = commandClient.command(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = action,
            data = data
        )

        return DeviceFirmwareCommandResult(
            sent = messageId != null,
            action = action,
            messageId = messageId.orEmpty(),
            errorMessage = if (messageId != null) "" else "WebSocket send failed"
        )
    }

    companion object {
        fun singleSession(commandClient: AqlWsCommandClient): DeviceFirmwareRuntimeRepository {
            return DeviceFirmwareRuntimeRepository { commandClient }
        }
    }
}
