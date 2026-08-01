package com.aqua.aqualight.data.devices.runtime.modules.network

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.common.DeviceRuntimeJsonCommand

class DeviceNetworkRuntimeRepository(
    private val gateway: DeviceRuntimeCommandGateway
) {
    suspend fun requestStatus(
        deviceUid: DeviceUid
    ): DeviceRuntimeCommandOutcome<DeviceNetworkStatus> = gateway.execute(
        deviceUid,
        DeviceRuntimeJsonCommand(
            module = AqlWsContract.MODULE_NETWORK,
            action = AqlWsContract.ACTION_NETWORK_STATUS_GET,
            successParser = DeviceNetworkStatusParser::parse
        )
    )
}
