package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import com.aqua.aqualight.data.devices.monitor.DevicePresenceSupervisor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AqlDiscoveryParserContractTest {

    @Test
    fun `firmware sentAt does not replace Android receive clocks`() {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(sentAtMillis = 4_294_967_295L),
            sourceIp = "192.168.1.44",
            receivedAtMillis = 1_234L
        )
        assertTrue(parsed is AqlDiscoveryParser.ParseResult.Valid)

        val device = (parsed as AqlDiscoveryParser.ParseResult.Valid).device
        val presence = DevicePresenceSupervisor(
            elapsedRealtimeMillis = { 5_678L }
        )
        presence.onDiscoveredDevice(device)

        val snapshot = presence.snapshots.value.getValue(device.snapshot.deviceUid)
        assertEquals(1_234L, snapshot.connectionState.lastUdpSeenAtMillis)
        assertEquals(5_678L, snapshot.connectionState.lastUdpSeenElapsedMillis)
    }

    @Test
    fun `UDP effective name never replaces immutable product display name`() {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(sentAtMillis = 10L),
            sourceIp = "192.168.1.44"
        ) as AqlDiscoveryParser.ParseResult.Valid

        assertEquals("AquaLight", parsed.device.snapshot.identity.displayName)
        assertEquals("AquaLight One", parsed.device.snapshot.identity.customName)
        assertEquals("AquaLight One", parsed.device.snapshot.identity.effectiveDisplayName)
        assertEquals("AquaLight", parsed.device.snapshot.product.displayName)
    }

    @Test
    fun `stale v2 documentation shape is not accepted as a runtime contract`() {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(sentAtMillis = 10L)
                .replace("aql.discovery.v1", "aql.discovery.v2")
        )

        assertInvalid(parsed, AqlDiscoveryParser.ParseError.UNSUPPORTED_SCHEMA)
    }

    @Test
    fun `duplicate and unknown JSON fields fail closed`() {
        val duplicate = validV1Payload(10L)
            .replace("\"version\":1,", "\"version\":1,\"version\":1,")
        val unknown = validV1Payload(10L)
            .replace("\"sentAtMs\":10,", "\"sentAtMs\":10,\"legacy\":true,")

        assertInvalid(
            AqlDiscoveryParser.parseDeviceAnnounce(duplicate),
            AqlDiscoveryParser.ParseError.DUPLICATE_FIELD
        )
        assertInvalid(
            AqlDiscoveryParser.parseDeviceAnnounce(unknown),
            AqlDiscoveryParser.ParseError.UNEXPECTED_FIELD
        )
    }

    @Test
    fun `packet limit is measured in UTF-8 bytes rather than Kotlin characters`() {
        val oversizedName = "ş".repeat(250)
        val payload = validV1Payload(10L)
            .replace("AquaLight One", oversizedName)

        assertTrue(payload.length <= AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES)
        assertTrue(
            payload.toByteArray(Charsets.UTF_8).size >
                AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
        )
        assertInvalid(
            AqlDiscoveryParser.parseDeviceAnnounce(payload),
            AqlDiscoveryParser.ParseError.PACKET_TOO_LARGE
        )
    }

    @Test
    fun `datagram source must match advertised host`() {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(10L),
            sourceIp = "192.168.1.45"
        )

        assertInvalid(parsed, AqlDiscoveryParser.ParseError.SOURCE_HOST_MISMATCH)
    }

    @Test
    fun `public or type coerced runtime endpoints are rejected`() {
        val publicEndpoint = validV1Payload(10L)
            .replace("192.168.1.44", "8.8.8.8")
        val stringPort = validV1Payload(10L)
            .replace("\"port\":80", "\"port\":\"80\"")

        assertInvalid(
            AqlDiscoveryParser.parseDeviceAnnounce(publicEndpoint, sourceIp = "8.8.8.8"),
            AqlDiscoveryParser.ParseError.UNSAFE_RUNTIME_ENDPOINT
        )
        assertInvalid(
            AqlDiscoveryParser.parseDeviceAnnounce(stringPort, sourceIp = "192.168.1.44"),
            AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE
        )
    }

    private fun assertInvalid(
        result: AqlDiscoveryParser.ParseResult,
        expected: AqlDiscoveryParser.ParseError
    ) {
        val invalid = result as AqlDiscoveryParser.ParseResult.Invalid
        assertEquals(expected, invalid.error)
    }

    private fun validV1Payload(sentAtMillis: Long): String {
        return """
            {"schema":"aql.discovery.v1","type":"device.announce","version":1,"sentAtMs":$sentAtMillis,"device":{"uid":"AQL-WPE-336172","shortId":"336172","name":"AquaLight One"},"product":{"family":"light","model":"wrgb_pro_elite_120","name":"AquaLight"},"network":{"mode":"sta","connected":true},"runtime":{"transport":"websocket","host":"192.168.1.44","port":80,"path":"/aql/v1/ws","protocol":"aql.ws.v1","protocolVersion":1}}
        """.trimIndent()
    }
}
