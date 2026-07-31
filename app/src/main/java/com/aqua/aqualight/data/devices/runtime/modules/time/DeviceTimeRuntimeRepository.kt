package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

class DeviceTimeRuntimeRepository(
    private val commandGateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimeStatus> =
        commandGateway.execute(deviceUid, DeviceTimeStatusGetCommand)

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimeConfigApplyPayload = DeviceSystemTimePayloadFactory.configFromSystem()
    ): DeviceRuntimeCommandOutcome<DeviceTimeConfigApplyResult> =
        commandGateway.execute(deviceUid, DeviceTimeConfigApplyCommand(payload))

    suspend fun syncPhoneNow(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimeSyncResult> = syncPhone(
        deviceUid = deviceUid,
        payload = DeviceSystemTimePayloadFactory.phoneSyncNow(save = save)
    )

    suspend fun syncPhone(
        deviceUid: DeviceUid,
        payload: DevicePhoneSyncPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimeSyncResult> =
        commandGateway.execute(deviceUid, DeviceTimePhoneSyncCommand(payload))

    suspend fun syncNtp(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimeSyncResult> =
        commandGateway.execute(deviceUid, DeviceTimeNtpSyncCommand)

    suspend fun setRtc(
        deviceUid: DeviceUid,
        payload: DeviceRtcSetPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimeSyncResult> =
        commandGateway.execute(deviceUid, DeviceTimeRtcSetCommand(payload))
}

private data object DeviceTimeStatusGetCommand : DeviceRuntimeCommand<DeviceTimeStatus> {
    override val module: String = AqlWsContract.MODULE_TIME
    override val action: String = AqlWsContract.ACTION_TIME_STATUS_GET
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceTimeStatus {
        require(response.statusCode == HTTP_OK)
        return DeviceTimeStatusParser.parse(response.data)
    }
}

private class DeviceTimeConfigApplyCommand(
    private val payload: DeviceTimeConfigApplyPayload
) : DeviceRuntimeCommand<DeviceTimeConfigApplyResult> {
    override val module: String = AqlWsContract.MODULE_TIME
    override val action: String = AqlWsContract.ACTION_TIME_CONFIG_APPLY
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(
        response: AqlWsIncomingMessage.Response
    ): DeviceTimeConfigApplyResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimeStatusParser.parseConfigApply(response.data)
    }
}

private class DeviceTimePhoneSyncCommand(
    private val payload: DevicePhoneSyncPayload
) : DeviceRuntimeCommand<DeviceTimeSyncResult> {
    override val module: String = AqlWsContract.MODULE_TIME
    override val action: String = AqlWsContract.ACTION_TIME_PHONE_SYNC
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceTimeSyncResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimeStatusParser.parsePhoneSync(response.data)
    }
}

private data object DeviceTimeNtpSyncCommand : DeviceRuntimeCommand<DeviceTimeSyncResult> {
    override val module: String = AqlWsContract.MODULE_TIME
    override val action: String = AqlWsContract.ACTION_TIME_NTP_SYNC
    override fun encodeData(): JSONObject = JSONObject()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceTimeSyncResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimeStatusParser.parseNtpSync(response.data)
    }
}

private class DeviceTimeRtcSetCommand(
    private val payload: DeviceRtcSetPayload
) : DeviceRuntimeCommand<DeviceTimeSyncResult> {
    override val module: String = AqlWsContract.MODULE_TIME
    override val action: String = AqlWsContract.ACTION_TIME_RTC_SET
    override fun encodeData(): JSONObject = payload.toJson()

    override fun parseSuccess(response: AqlWsIncomingMessage.Response): DeviceTimeSyncResult {
        require(response.statusCode == HTTP_OK)
        return DeviceTimeStatusParser.parseRtcSet(response.data)
    }
}

private const val HTTP_OK = 200
