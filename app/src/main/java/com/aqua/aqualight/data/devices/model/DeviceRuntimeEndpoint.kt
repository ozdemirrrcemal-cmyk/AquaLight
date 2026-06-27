package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.contract.AqlWsContract

data class DeviceRuntimeEndpoint(
    val ip: String = "",
    val wifiMode: String = "",
    val wifiConnected: Boolean = false,
    val setupApActive: Boolean = false,
    val runtimeTransport: String = "",
    val wsPort: Int = 0,
    val wsPath: String = AqlWsContract.DEFAULT_PATH,
    val wsProtocol: String = AqlWsContract.DEFAULT_PROTOCOL,
    val wsProtocolVersion: Int = 0,
    val discoveryPort: Int = 0
) {
    val hasWebSocketEndpoint: Boolean
        get() = ip.isNotBlank() && wsPort > 0 && wsPath.isNotBlank()

    fun toWebSocketUrl(): String? = if (hasWebSocketEndpoint) {
        "ws://$ip:$wsPort$wsPath"
    } else {
        null
    }
}
