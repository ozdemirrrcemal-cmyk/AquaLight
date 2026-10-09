package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.core.boundToGeneration
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand
import org.json.JSONObject

/** Exact WRGB thermal command boundary, intentionally not presentation-wired yet. */
class DeviceLightThermalV1RuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid,
        expectedGeneration: DeviceRuntimeConnectionGeneration? = null
    ): DeviceRuntimeCommandOutcome<DeviceLightThermalStatus> = execute(
        deviceUid = deviceUid,
        action = DeviceLightThermalV1Contract.Action.STATUS_GET,
        expectedGeneration = expectedGeneration,
        parser = DeviceLightThermalV1ResponseParser::parseStatus
    )

    suspend fun applyConfig(
        deviceUid: DeviceUid,
        payload: DeviceLightThermalConfigApplyPayload,
        expectedGeneration: DeviceRuntimeConnectionGeneration? = null
    ): DeviceRuntimeCommandOutcome<DeviceLightThermalConfigApplyResult> = execute(
        deviceUid = deviceUid,
        action = DeviceLightThermalV1Contract.Action.CONFIG_APPLY,
        expectedGeneration = expectedGeneration,
        dataFactory = payload::toJson,
        parser = DeviceLightThermalV1ResponseParser::parseConfigApply
    )

    private suspend fun <T> execute(
        deviceUid: DeviceUid,
        action: String,
        expectedGeneration: DeviceRuntimeConnectionGeneration?,
        dataFactory: () -> JSONObject = ::JSONObject,
        parser: (JSONObject) -> T
    ): DeviceRuntimeCommandOutcome<T> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_LIGHT,
            action = action,
            dataFactory = dataFactory,
            successParser = parser
        ).boundToGeneration(expectedGeneration)
    )
}
