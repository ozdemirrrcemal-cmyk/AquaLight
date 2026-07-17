package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.contract.AqlWsContract

data class DeviceRuntimeEndpoint(
    val ip: String = "",
    val wifiMode: String = "",
    val wifiConnected: Boolean = false,
    val setupApActive: Boolean = false,
    val runtimeTransport: String = RUNTIME_TRANSPORT_WEBSOCKET,
    val wsPort: Int = 0,
    val wsPath: String = AqlWsContract.DEFAULT_PATH,
    val wsProtocol: String = AqlWsContract.DEFAULT_PROTOCOL,
    val wsProtocolVersion: Int = AqlWsContract.PROTOCOL_VERSION,
    val discoveryPort: Int = 0
) {
    val hasWebSocketEndpoint: Boolean
        get() = ip.isPrivateLanIpv4Literal() &&
            wsPort in MIN_PORT..MAX_PORT &&
            wsPath == AqlWsContract.DEFAULT_PATH &&
            wsPath.isValidWsPath() &&
            wsProtocol == AqlWsContract.DEFAULT_PROTOCOL &&
            wsProtocolVersion == AqlWsContract.PROTOCOL_VERSION &&
            runtimeTransport == RUNTIME_TRANSPORT_WEBSOCKET

    internal fun privateLanAddressBytes(): ByteArray? {
        if (!ip.isPrivateLanIpv4Literal()) return null
        return ip.trim().split('.').map { octet -> octet.toInt().toByte() }.toByteArray()
    }

    private fun String.isValidWsPath(): Boolean {
        val value = trim()
        return value.startsWith("/") &&
            value.isNotBlank() &&
            value.none { char -> char.isWhitespace() } &&
            !value.contains("#")
    }

    private fun String.isPrivateLanIpv4Literal(): Boolean {
        val octets = trim().split('.')
        if (octets.size != IPV4_OCTET_COUNT) return false

        val values = octets.map { octet ->
            if (octet.isBlank() || octet.length > IPV4_MAX_OCTET_DIGITS) return false
            if (octet.any { char -> char !in '0'..'9' }) return false
            octet.toIntOrNull()?.takeIf { value -> value in IPV4_OCTET_RANGE } ?: return false
        }

        val first = values[0]
        val second = values[1]

        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    private companion object {
        const val MIN_PORT = 1
        const val MAX_PORT = 65_535
        const val IPV4_OCTET_COUNT = 4
        const val IPV4_MAX_OCTET_DIGITS = 3
        const val RUNTIME_TRANSPORT_WEBSOCKET = "websocket"
        val IPV4_OCTET_RANGE = 0..255
    }
}
