package com.aqua.aqualight.data.devices.contract

/**
 * Android mirror of the firmware UDP discovery contract.
 *
 * UDP is used only for LAN discovery and WebSocket endpoint announcement.
 * Runtime commands, pairing, schedules, OTA control and device settings must not use UDP.
 */
object AqlDiscoveryContract {
    const val SCHEMA = "aql.discovery.v2"
    const val MESSAGE_DEVICE_ANNOUNCE = "device_announce"
    const val MESSAGE_REFRESH = "discovery_refresh"
    const val UDP_VERSION = 20260624
    const val PORT = 10888
    const val MAX_PACKET_SIZE_BYTES = 1536
    const val RUNTIME_TRANSPORT_WEBSOCKET = "websocket"

    fun buildRefreshPayload(): String =
        "{\"schema\":\"$SCHEMA\",\"messageType\":\"$MESSAGE_REFRESH\"}"
}
