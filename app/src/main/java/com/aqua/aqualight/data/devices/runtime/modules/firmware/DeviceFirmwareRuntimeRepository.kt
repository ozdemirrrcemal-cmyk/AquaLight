package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceFirmwareRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareStatus> =
        commandGateway.execute(deviceUid, DeviceFirmwareStatusGetCommand)

    suspend fun requestOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaSnapshot> =
        commandGateway.execute(deviceUid, DeviceFirmwareOtaStatusCommand)

    suspend fun startOta(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> =
        commandGateway.execute(deviceUid, DeviceFirmwareOtaStartCommand(payload))

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> =
        startOta(plan.deviceUid, plan.payload)

    suspend fun clearOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearTypedResult> =
        commandGateway.execute(deviceUid, DeviceFirmwareOtaClearCommand)
}

private data object DeviceFirmwareStatusGetCommand : DeviceRuntimeCommand<DeviceFirmwareStatus> {
    override val module: String = DeviceFirmwareRuntimeContract.MODULE
    override val action: String = DeviceFirmwareRuntimeContract.Action.STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceFirmwareStatus {
        require(response.statusCode == HTTP_OK)
        return DeviceFirmwareCommandParsers.parseFirmwareStatus(response.data)
    }
}

private data object DeviceFirmwareOtaStatusCommand :
    DeviceRuntimeCommand<DeviceFirmwareOtaSnapshot> {
    override val module: String = DeviceFirmwareRuntimeContract.MODULE
    override val action: String = DeviceFirmwareRuntimeContract.Action.OTA_STATUS
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceFirmwareOtaSnapshot {
        require(response.statusCode == HTTP_OK)
        return DeviceFirmwareCommandParsers.parseOtaStatus(response.data)
    }
}

private class DeviceFirmwareOtaStartCommand(
    private val payload: DeviceFirmwareOtaStartPayload
) : DeviceRuntimeCommand<DeviceFirmwareOtaStartAccepted> {
    override val module: String = DeviceFirmwareRuntimeContract.MODULE
    override val action: String = DeviceFirmwareRuntimeContract.Action.OTA_START
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceFirmwareOtaStartAccepted {
        require(response.statusCode == HTTP_ACCEPTED)
        return DeviceFirmwareCommandParsers.parseOtaStart(response.data)
    }
}

private data object DeviceFirmwareOtaClearCommand :
    DeviceRuntimeCommand<DeviceFirmwareOtaClearTypedResult> {
    override val module: String = DeviceFirmwareRuntimeContract.MODULE
    override val action: String = DeviceFirmwareRuntimeContract.Action.OTA_CLEAR
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceFirmwareOtaClearTypedResult {
        require(response.statusCode == HTTP_OK)
        return DeviceFirmwareCommandParsers.parseOtaClear(response.data)
    }
}

private const val HTTP_OK = 200
private const val HTTP_ACCEPTED = 202
