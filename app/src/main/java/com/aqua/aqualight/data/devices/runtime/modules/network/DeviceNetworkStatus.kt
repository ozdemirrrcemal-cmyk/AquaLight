package com.aqua.aqualight.data.devices.runtime.modules.network

data class DeviceNetworkStatus(
    val connected: Boolean,
    val mode: Int,
    val rssi: Int,
    val channel: Int,
    val staHostname: String,
    val client: DeviceNetworkClientStatus,
    val setupAp: DeviceNetworkSetupApStatus,
    val discovery: DeviceNetworkDiscoveryStatus,
    val runtime: DeviceNetworkRuntimeStatus
)

data class DeviceNetworkClientStatus(
    val connected: Boolean,
    val configured: Boolean,
    val ssid: String,
    val ip: String,
    val gateway: String,
    val subnet: String,
    val dns: String,
    val mac: String,
    val hostname: String,
    val reconnectPolicy: String,
    val disconnectEraseAffects: String,
    val setupApFallback: Boolean
)

data class DeviceNetworkSetupApStatus(
    val active: Boolean,
    val ssid: String,
    val ip: String,
    val gateway: String,
    val subnet: String,
    val mac: String,
    val passwordProtected: Boolean,
    val passwordSource: String,
    val provisioningContract: String,
    val provisioningTransport: String
)

data class DeviceNetworkDiscoveryStatus(
    val udpBroadcast: Boolean,
    val udpPort: Int,
    val registeredOnly: Boolean,
    val authenticatedWebSocketRequired: Boolean
)

data class DeviceNetworkRuntimeStatus(
    val transport: String,
    val wsPath: String,
    val wsPort: Int,
    val wsProtocol: String,
    val wsProtocolVersion: Int
)
