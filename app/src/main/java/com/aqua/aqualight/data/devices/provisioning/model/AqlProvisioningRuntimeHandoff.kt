package com.aqua.aqualight.data.devices.provisioning.model

import com.aqua.aqualight.data.devices.contract.AqlBleProvisioningContract
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceUid

data class AqlProvisioningRuntimeHandoff(
    val deviceUid: DeviceUid,
    val endpoint: DeviceRuntimeEndpoint,
    val webSocketToken: String
) {
    val isUsable: Boolean
        get() = endpoint.hasWebSocketEndpoint &&
            webSocketToken.isRuntimeTokenHex() &&
            deviceUid.value.isNotBlank()
}

private fun String.isRuntimeTokenHex(): Boolean {
    return length == AqlBleProvisioningContract.RUNTIME_TOKEN_HEX_LENGTH &&
        matches(Regex("(?i)^[0-9a-f]+$"))
}
