package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.monitor.DevicePresenceSupervisor
import org.json.JSONObject
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
    fun `coercible values are rejected instead of normalized`() {
        val stringVersion = JSONObject(validV1Payload(sentAtMillis = 10L))
            .put("version", "1")
        val stringConnected = JSONObject(validV1Payload(sentAtMillis = 10L)).apply {
            getJSONObject("network").put("connected", "true")
        }
        val normalizedFamily = JSONObject(validV1Payload(sentAtMillis = 10L)).apply {
            getJSONObject("product").put("family", " LIGHT ")
        }

        assertInvalid(
            stringVersion,
            AqlDiscoveryParser.ParseError.UNSUPPORTED_UDP_VERSION
        )
        assertInvalid(
            stringConnected,
            AqlDiscoveryParser.ParseError.INVALID_CONTRACT_SHAPE
        )
        assertInvalid(
            normalizedFamily,
            AqlDiscoveryParser.ParseError.UNSUPPORTED_PRODUCT_FAMILY
        )
    }

    @Test
    fun `extra fields and payload wrapper whitespace are rejected`() {
        val extraField = JSONObject(validV1Payload(sentAtMillis = 10L))
            .put("legacyPort", 10888)

        assertInvalid(
            extraField,
            AqlDiscoveryParser.ParseError.INVALID_CONTRACT_SHAPE
        )
        val wrapped = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = " " + validV1Payload(sentAtMillis = 10L)
        ) as AqlDiscoveryParser.ParseResult.Invalid
        assertEquals(
            AqlDiscoveryParser.ParseError.INVALID_CONTRACT_SHAPE,
            wrapped.error
        )
    }

    @Test
    fun `stale v2 documentation shape is not accepted as a runtime contract`() {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(sentAtMillis = 10L)
                .replace("aql.discovery.v1", "aql.discovery.v2")
        )

        val invalid = parsed as AqlDiscoveryParser.ParseResult.Invalid
        assertEquals(
            AqlDiscoveryParser.ParseError.UNSUPPORTED_SCHEMA,
            invalid.error
        )
    }

    private fun assertInvalid(
        payload: JSONObject,
        expected: AqlDiscoveryParser.ParseError
    ) {
        val parsed = AqlDiscoveryParser.parseDeviceAnnounce(payload.toString())
            as AqlDiscoveryParser.ParseResult.Invalid
        assertEquals(expected, parsed.error)
    }

    private fun validV1Payload(sentAtMillis: Long): String {
        return """
            {
              "schema":"aql.discovery.v1",
              "type":"device.announce",
              "version":1,
              "sentAtMs":$sentAtMillis,
              "device":{"uid":"AQL-WPE-336172","shortId":"336172","name":"AquaLight One"},
              "product":{"family":"light","model":"AQL-LIGHT","name":"AquaLight"},
              "network":{"mode":"sta","connected":true},
              "runtime":{
                "transport":"websocket",
                "host":"192.168.1.44",
                "port":80,
                "path":"/aql/v1/ws",
                "protocol":"aql.ws.v1",
                "protocolVersion":1
              }
            }
        """.trimIndent()
    }
}
