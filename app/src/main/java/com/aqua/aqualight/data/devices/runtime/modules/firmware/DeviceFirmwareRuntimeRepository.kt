package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsCommandClient
import org.json.JSONObject

class DeviceFirmwareRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway,
    private val commandClientProvider: (DeviceUid) -> AqlWsCommandClient?
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.STATUS_GET,
            successParser = DeviceFirmwareReadParser::parseStatus
        )
    )

    suspend fun readOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_STATUS,
            successParser = DeviceFirmwareReadParser::parseOtaStatus
        )
    )

    fun requestOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult = sendLegacy(
        deviceUid = deviceUid,
        action = DeviceFirmwareRuntimeContract.Action.OTA_STATUS
    )

    fun startOta(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload
    ): DeviceFirmwareCommandResult = sendLegacy(
        deviceUid = deviceUid,
        action = DeviceFirmwareRuntimeContract.Action.OTA_START,
        data = payload.toJson()
    )

    fun startUpdate(plan: DeviceFirmwareUpdatePlan): DeviceFirmwareCommandResult =
        startOta(
            deviceUid = plan.deviceUid,
            payload = plan.payload
        )

    fun clearOtaStatus(deviceUid: DeviceUid): DeviceFirmwareCommandResult = sendLegacy(
        deviceUid = deviceUid,
        action = DeviceFirmwareRuntimeContract.Action.OTA_CLEAR
    )

    private fun sendLegacy(
        deviceUid: DeviceUid,
        action: String,
        data: JSONObject = JSONObject()
    ): DeviceFirmwareCommandResult {
        val commandClient = commandClientProvider(deviceUid)
            ?: return DeviceFirmwareCommandResult(
                sent = false,
                action = action,
                errorMessage = "No WebSocket command client for ${deviceUid.value}"
            )
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
}
