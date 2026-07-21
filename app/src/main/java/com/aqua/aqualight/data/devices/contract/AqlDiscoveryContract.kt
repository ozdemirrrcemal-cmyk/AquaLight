package com.aqua.aqualight.data.devices.contract

/**
 * Android mirror of the firmware UDP discovery contract.
 *
 * UDP is only the public LAN beacon used to find the authenticated WebSocket runtime endpoint.
 * There is one accepted contract shape; noncanonical discovery packets are rejected.
 */
object AqlDiscoveryContract {
    const val SCHEMA = "aql.discovery.v1"
    const val TYPE_DEVICE_ANNOUNCE = "device.announce"
    const val TYPE_REFRESH = "refresh"
    const val VERSION = 1
    const val PORT = 10888
    const val MAX_PACKET_SIZE_BYTES = 768
    const val RUNTIME_TRANSPORT_WEBSOCKET = "websocket"

    fun buildRefreshPayload(): String =
        "{\"schema\":\"$SCHEMA\",\"type\":\"$TYPE_REFRESH\",\"version\":$VERSION}"
}
