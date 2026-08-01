package com.aqua.aqualight.data.devices.runtime.modules.network

data class DeviceNetworkStatus(
    val ip: String,
    val macAddress: String,
    val wifiModeCode: Int,
    val wifiMode: String,
    val stationEnabled: Boolean,
    val setupApEnabled: Boolean,
    val clientConnected: Boolean,
    val setupApActive: Boolean,
    val uptimeMs: Long,
    val client: DeviceNetworkClientStatus,
    val setupAp: DeviceNetworkSetupApStatus,
    val discovery: DeviceNetworkDiscoveryStatus,
    val runtime: DeviceNetworkRuntimeStatus
)

data class DeviceNetworkClientStatus(
    val enabled: Boolean,
    val configured: Boolean,
    val ssid: String,
    val bssidConfigured: Boolean,
    val channel: Int,
    val connected: Boolean,
    val state: String,
    val wifiStatus: Int,
    val ip: String,
    val gateway: String,
    val subnet: String,
    val dns: String,
    val rssi: Int,
    val lastWifiEvent: Int,
    val lastDisconnectReason: Int,
    val lastDisconnectReasonName: String,
    val lastDisconnectAgeMs: Long,
    val lastGotIpAgeMs: Long,
    val nextRetryRemainingMs: Long,
    val connectionInProgress: Boolean
)

data class DeviceNetworkSetupApStatus(
    val enabled: Boolean,
    val active: Boolean,
    val ssid: String,
    val ip: String,
    val stationCount: Int
)

data class DeviceNetworkDiscoveryStatus(
    val ready: Boolean,
    val port: Int,
    val broadcastIp: String,
    val currentIp: String,
    val payloadSize: Int,
    val lastRefreshMs: Long,
    val lastPacketRejectedMs: Long,
    val rejectedPacketCount: Long
)

data class DeviceNetworkRuntimeStatus(
    val transport: String,
    val wsPort: Int,
    val wsPath: String,
    val wsProtocol: String,
    val wsProtocolVersion: Int
)
