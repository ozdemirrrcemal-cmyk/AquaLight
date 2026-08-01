package com.aqua.aqualight.data.devices.runtime.modules.device

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeIdentityEnvelope
import com.aqua.aqualight.data.devices.model.DeviceRuntimeModuleStatus
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeCapabilitiesParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeIdentityParser
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeModulesParser
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand

class DeviceCommonRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestIdentity(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceRuntimeIdentityEnvelope> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_IDENTITY_GET,
            successParser = { data ->
                DeviceRuntimeIdentityParser.parse(deviceUid, data).getOrThrow()
            }
        )
    )

    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceRuntimeModuleStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_STATUS_GET,
            successParser = { data ->
                DeviceRuntimeModulesParser.parseDeviceStatus(data).getOrThrow()
            }
        )
    )

    suspend fun requestCapabilities(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceRuntimeCapabilities> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_CAPABILITIES_GET,
            successParser = { data ->
                DeviceRuntimeCapabilitiesParser.parse(data).getOrThrow()
            }
        )
    )

    suspend fun setName(
        deviceUid: DeviceUid,
        request: DeviceNameSetRequest
    ): DeviceRuntimeCommandOutcome<DeviceNameSetResult> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_DEVICE,
            action = AqlWsContract.ACTION_DEVICE_NAME_SET,
            dataFactory = request::toJson,
            successParser = DeviceCommonRuntimeParser::parseNameSet
        )
    )
}
