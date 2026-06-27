package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid

data class AqlProvisioningRuntimeHandoff(
    val deviceUid: DeviceUid,
    val endpoint: DeviceRuntimeEndpoint,
    val webSocketToken: String
) {
    val isUsable: Boolean
        get() = endpoint.hasWebSocketEndpoint && webSocketToken.isNotBlank()
}
