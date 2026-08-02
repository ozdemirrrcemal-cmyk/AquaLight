package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand

class DeviceFirmwareRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
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

    suspend fun startOta(
        deviceUid: DeviceUid,
        payload: DeviceFirmwareOtaStartPayload
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_START,
            dataFactory = payload::toJson,
            successParser = { data ->
                DeviceFirmwareStatusParser.parseOtaStartAcceptedExact(data).getOrThrow()
            }
        )
    )

    suspend fun startUpdate(
        plan: DeviceFirmwareUpdatePlan
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaStartAccepted> =
        startOta(
            deviceUid = plan.deviceUid,
            payload = plan.payload
        )

    suspend fun clearOtaStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceFirmwareOtaClearResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = DeviceFirmwareRuntimeContract.MODULE,
            action = DeviceFirmwareRuntimeContract.Action.OTA_CLEAR,
            successParser = { data ->
                DeviceFirmwareStatusParser.parseOtaClearResultExact(data).getOrThrow()
            }
        )
    )
}
