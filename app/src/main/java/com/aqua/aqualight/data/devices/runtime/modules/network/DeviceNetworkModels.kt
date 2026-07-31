package com.aqua.aqualight.data.devices.runtime.modules.network

enum class DeviceNetworkWifiMode(val wireValue: String, val code: Int?) {
    OFF("off", 0),
    CLIENT("client", 1),
    SETUP_AP("setup_ap", 2),
    CLIENT_AND_SETUP_AP("client_and_setup_ap", 3),
    UNKNOWN("unknown", null);

    companion object {
        private val byWire = entries.associateBy(DeviceNetworkWifiMode::wireValue)
        fun fromWireExact(value: String): DeviceNetworkWifiMode? = byWire[value]
    }
}

enum class DeviceNetworkClientState(val wireValue: String) {
    IDLE("idle"),
    SETUP_AP_ONLY("setupApOnly"),
    CONNECTING("connecting"),
    CONNECTED_TO_AP("connectedToAp"),
    GOT_IP("gotIp"),
    DISCONNECTED("disconnected"),
    LOST_IP("lostIp"),
    FAILED_TIMEOUT("failedTimeout"),
    FAILED_NO_AP("failedNoAp"),
    FAILED_AUTH("failedAuth"),
    FAILED_HANDSHAKE("failedHandshake"),
    FAILED_ASSOCIATION("failedAssociation"),
    FAILED_UNKNOWN("failedUnknown"),
    UNKNOWN("unknown");

    companion object {
        private val byWire = entries.associateBy(DeviceNetworkClientState::wireValue)
        fun fromWireExact(value: String): DeviceNetworkClientState? = byWire[value]
    }
}

enum class DeviceNetworkDisconnectReason(val wireValue: String) {
    NONE("none"),
    UNSPECIFIED("unspecified"),
    AUTH_EXPIRE("authExpire"),
    AUTH_LEAVE("authLeave"),
    ASSOC_EXPIRE("assocExpire"),
    ASSOC_TOO_MANY("assocTooMany"),
    NOT_AUTHED("notAuthed"),
    NOT_ASSOCED("notAssoced"),
    ASSOC_LEAVE("assocLeave"),
    ASSOC_NOT_AUTHED("assocNotAuthed"),
    FOUR_WAY_HANDSHAKE_TIMEOUT("fourWayHandshakeTimeout"),
    GROUP_KEY_UPDATE_TIMEOUT("groupKeyUpdateTimeout"),
    IE_IN_FOUR_WAY_DIFFERS("ieInFourWayDiffers"),
    GROUP_CIPHER_INVALID("groupCipherInvalid"),
    PAIRWISE_CIPHER_INVALID("pairwiseCipherInvalid"),
    AKMP_INVALID("akmpInvalid"),
    UNSUPPORTED_RSN_IE_VERSION("unsupportedRsnIeVersion"),
    INVALID_RSN_IE_CAPABILITIES("invalidRsnIeCapabilities"),
    EIGHT_ZERO_TWO_ONE_X_AUTH_FAILED("8021xAuthFailed"),
    CIPHER_SUITE_REJECTED("cipherSuiteRejected"),
    BEACON_TIMEOUT("beaconTimeout"),
    NO_AP_FOUND("noApFound"),
    AUTH_FAIL("authFail"),
    ASSOC_FAIL("assocFail"),
    HANDSHAKE_TIMEOUT("handshakeTimeout"),
    CONNECTION_FAIL("connectionFail"),
    UNKNOWN("unknown");

    companion object {
        private val byWire = entries.associateBy(DeviceNetworkDisconnectReason::wireValue)
        fun fromWireExact(value: String): DeviceNetworkDisconnectReason? = byWire[value]
    }
}

data class DeviceNetworkClientStatus(
    val enabled: Boolean,
    val configured: Boolean,
    val ssid: String,
    val bssidConfigured: Boolean,
    val channel: Int,
    val connected: Boolean,
    val state: DeviceNetworkClientState,
    val wifiStatus: Int,
    val ip: String,
    val gateway: String,
    val subnet: String,
    val dns: String,
    val rssi: Int,
    val lastWifiEvent: Int,
    val lastDisconnectReason: Int,
    val lastDisconnectReasonName: DeviceNetworkDisconnectReason,
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

data class DeviceNetworkRuntimeTransport(
    val transport: String,
    val wsPort: Int,
    val wsPath: String,
    val wsProtocol: String,
    val wsProtocolVersion: Int
)

data class DeviceNetworkStatus(
    val ip: String,
    val macAddress: String,
    val wifiModeCode: Int,
    val wifiMode: DeviceNetworkWifiMode,
    val stationEnabled: Boolean,
    val setupApEnabled: Boolean,
    val clientConnected: Boolean,
    val setupApActive: Boolean,
    val uptimeMs: Long,
    val client: DeviceNetworkClientStatus,
    val setupAp: DeviceNetworkSetupApStatus,
    val discovery: DeviceNetworkDiscoveryStatus,
    val runtime: DeviceNetworkRuntimeTransport
)
