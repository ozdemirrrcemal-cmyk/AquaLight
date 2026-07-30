package com.aqua.aqualight.data.devices.runtime.state

data class DeviceRuntimeTransportStatus(
    val transport: String,
    val wsSchema: String,
    val wsPath: String,
    val wsPort: Int,
    val wsProtocol: String = wsSchema,
    val wsProtocolVersion: Int? = null
)

data class DeviceRuntimeNameStatus(
    val productDisplayName: String,
    val customName: String,
    val effectiveDisplayName: String,
    val editable: Boolean,
    val maxBytes: Int
)

data class DeviceRuntimeProductStatus(
    val productKey: String,
    val family: String,
    val model: String,
    val displayName: String
)

data class DeviceRuntimeCompiledModules(
    val light: Boolean,
    val cooling: Boolean,
    val temperature: Boolean,
    val timerApi: Boolean,
    val timerEngine: Boolean,
    val dosing: Boolean,
    val network: Boolean,
    val discovery: Boolean,
    val firmware: Boolean,
    val system: Boolean
)

data class DeviceRuntimeDeviceStatus(
    val state: String,
    val authenticated: Boolean,
    val uptimeMs: Long,
    val device: DeviceRuntimeNameStatus,
    val product: DeviceRuntimeProductStatus,
    val runtime: DeviceRuntimeTransportStatus,
    val modules: DeviceRuntimeCompiledModules
)

data class DeviceRuntimeSecurityStatus(
    val tokenGateEnabled: Boolean,
    val dynamicPairingEnabled: Boolean,
    val paired: Boolean,
    val runtimeTransport: String,
    val runtimeAuthMessageType: String,
    val runtimeAuthScheme: String,
    val runtimeCredentialSerialized: Boolean,
    val runtimeReplayProtection: String,
    val initialOwnershipTransport: String,
    val firstTokenTransport: String,
    val webSocketPairingCommand: String,
    val webSocketPairingCommandAuth: String,
    val webSocketPairingPurpose: String,
    val publicFirstPairingSupported: Boolean,
    val mutatingCommandsRequireAuth: Boolean,
    val tokenReturnedByStatus: Boolean,
    val tokenStorageBackend: String,
    val tokenStorageFormat: String,
    val tokenStoredPlaintext: Boolean,
    val tokenFormat: String,
    val tokenHexLength: Int,
    val deviceUid: String,
    val shortId: String,
    val serialNumber: String,
    val tokenVersion: Long?,
    val pairedAtMs: Long?,
    val lastRotatedAtMs: Long?,
    val provisioningTokenPending: Boolean?
)

data class DeviceRuntimeNetworkClientStatus(
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

data class DeviceRuntimeSetupApStatus(
    val enabled: Boolean,
    val active: Boolean,
    val ssid: String,
    val ip: String,
    val stationCount: Int
)

data class DeviceRuntimeDiscoveryStatus(
    val ready: Boolean,
    val port: Int,
    val broadcastIp: String,
    val currentIp: String,
    val payloadSize: Int,
    val lastRefreshMs: Long,
    val lastPacketRejectedMs: Long,
    val rejectedPacketCount: Long
)

data class DeviceRuntimeNetworkStatus(
    val ip: String,
    val macAddress: String,
    val wifiModeCode: Int,
    val wifiMode: String,
    val stationEnabled: Boolean,
    val setupApEnabled: Boolean,
    val clientConnected: Boolean,
    val setupApActive: Boolean,
    val uptimeMs: Long,
    val client: DeviceRuntimeNetworkClientStatus,
    val setupAp: DeviceRuntimeSetupApStatus,
    val discovery: DeviceRuntimeDiscoveryStatus,
    val runtime: DeviceRuntimeTransportStatus
)
