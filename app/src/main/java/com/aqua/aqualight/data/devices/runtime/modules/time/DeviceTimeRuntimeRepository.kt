package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

class DeviceTimeRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimeStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceTimeRuntimeContract.MODULE,
            action = DeviceTimeRuntimeContract.Action.STATUS_GET,
            successParser = DeviceTimeStatusParser::parseExact
        )
    )

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceTimeConfigApplyPayload = DeviceSystemTimePayloadFactory.configFromSystem()
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = executeMutation(
        deviceUid = deviceUid,
        action = DeviceTimeRuntimeContract.Action.CONFIG_APPLY,
        dataFactory = payload::toJson
    )

    suspend fun syncPhoneNow(
        deviceUid: DeviceUid,
        save: Boolean = true
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = syncPhone(
        deviceUid = deviceUid,
        payload = DeviceSystemTimePayloadFactory.phoneSyncNow(save = save)
    )

    suspend fun syncPhone(
        deviceUid: DeviceUid,
        payload: DevicePhoneSyncPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = executeMutation(
        deviceUid = deviceUid,
        action = DeviceTimeRuntimeContract.Action.PHONE_SYNC,
        dataFactory = payload::toJson
    )

    suspend fun syncNtp(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = executeMutation(
        deviceUid = deviceUid,
        action = DeviceTimeRuntimeContract.Action.NTP_SYNC
    )

    suspend fun setRtc(
        deviceUid: DeviceUid,
        payload: DeviceManualRtcPayload
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = executeMutation(
        deviceUid = deviceUid,
        action = DeviceTimeRuntimeContract.Action.RTC_SET,
        dataFactory = payload::toJson
    )

    private suspend fun executeMutation(
        deviceUid: DeviceUid,
        action: String,
        dataFactory: () -> JSONObject = ::JSONObject
    ): DeviceRuntimeCommandOutcome<DeviceTimeMutationResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceTimeRuntimeContract.MODULE,
            action = action,
            dataFactory = dataFactory,
            successParser = { data -> DeviceTimeStatusParser.parseMutation(data, action) }
        )
    )
}
