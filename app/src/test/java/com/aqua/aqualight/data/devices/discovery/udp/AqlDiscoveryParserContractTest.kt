package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import com.aqua.aqualight.data.devices.monitor.DevicePresenceSupervisor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val presence = DevicePresenceSupervisor(elapsedRealtimeMillis = { 5_678L })
        presence.onDiscoveredDevice(device)

        val snapshot = presence.snapshots.value.getValue(device.snapshot.deviceUid)
        assertEquals(1_234L, snapshot.connectionState.lastUdpSeenAtMillis)
        assertEquals(5_678L, snapshot.connectionState.lastUdpSeenElapsedMillis)
        assertEquals("192.168.1.44", device.sourceIp)
    }

    @Test
    fun `stale v2 documentation shape is not accepted as a runtime contract`() {
        val invalid = parseInvalid(validV1Json().put("schema", "aql.discovery.v2"))

        assertEquals(AqlDiscoveryParser.ParseError.UNSUPPORTED_SCHEMA, invalid.error)
    }

    @Test
    fun `announce rejects unknown fields duplicate keys and scalar coercion`() {
        val unknown = parseInvalid(validV1Json().put("legacy", true))
        val versionCoercion = parseInvalid(validV1Json().put("version", "1"))
        val booleanCoercion = parseInvalid(
            validV1Json().apply {
                getJSONObject("network").put("connected", 1)
            }
        )
        val duplicateRaw = validV1Payload().replace(
            "\"version\":1",
            "\"version\":1,\"version\":1"
        )
        val duplicate = AqlDiscoveryParser.parseDeviceAnnounce(duplicateRaw)
            as AqlDiscoveryParser.ParseResult.Invalid

        assertEquals(AqlDiscoveryParser.ParseError.UNEXPECTED_FIELD, unknown.error)
        assertEquals(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE, versionCoercion.error)
        assertEquals(AqlDiscoveryParser.ParseError.INVALID_FIELD_TYPE, booleanCoercion.error)
        assertEquals(AqlDiscoveryParser.ParseError.DUPLICATE_FIELD, duplicate.error)
    }

    @Test
    fun `announce rejects aliases whitespace and inconsistent network state`() {
        val familyAlias = parseInvalid(
            validV1Json().apply {
                getJSONObject("product").put("family", "LIGHT")
            }
        )
        val paddedUid = parseInvalid(
            validV1Json().apply {
                getJSONObject("device").put("uid", " AQL-WPE-336172")
            }
        )
        val invalidNetwork = parseInvalid(
            validV1Json().apply {
                getJSONObject("network")
                    .put("mode", "ap")
                    .put("connected", true)
            }
        )

        assertEquals(
            AqlDiscoveryParser.ParseError.UNSUPPORTED_PRODUCT_FAMILY,
            familyAlias.error
        )
        assertEquals(AqlDiscoveryParser.ParseError.INVALID_FIELD_VALUE, paddedUid.error)
        assertEquals(AqlDiscoveryParser.ParseError.INVALID_NETWORK_STATE, invalidNetwork.error)
    }

    @Test
    fun `announce requires canonical private runtime host matching datagram source`() {
        val mismatch = AqlDiscoveryParser.parseDeviceAnnounce(
            rawPayload = validV1Payload(),
            sourceIp = "192.168.1.99"
        ) as AqlDiscoveryParser.ParseResult.Invalid
        val publicHost = parseInvalid(
            validV1Json().apply {
                getJSONObject("runtime").put("host", "8.8.8.8")
            }
        )
        val nonCanonicalHost = parseInvalid(
            validV1Json().apply {
                getJSONObject("runtime").put("host", "192.168.001.44")
            }
        )

        assertEquals(
            AqlDiscoveryParser.ParseError.RUNTIME_HOST_SOURCE_MISMATCH,
            mismatch.error
        )
        assertEquals(AqlDiscoveryParser.ParseError.INVALID_RUNTIME_HOST, publicHost.error)
        assertEquals(
            AqlDiscoveryParser.ParseError.INVALID_RUNTIME_HOST,
            nonCanonicalHost.error
        )
    }

    @Test
    fun `packet limit is measured in utf8 bytes rather than characters`() {
        val payload = validV1Json().apply {
            getJSONObject("device").put("name", "ş".repeat(350))
        }.toString()
        assertTrue(payload.length < AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES)
        assertTrue(
            payload.toByteArray(Charsets.UTF_8).size >
                AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
        )

        val invalid = AqlDiscoveryParser.parseDeviceAnnounce(payload)
            as AqlDiscoveryParser.ParseResult.Invalid
        assertEquals(AqlDiscoveryParser.ParseError.PACKET_TOO_LARGE, invalid.error)
    }

    @Test
    fun `datagram decoder rejects oversize malformed utf8 and invalid slices`() {
        val validBytes = validV1Payload().toByteArray(Charsets.UTF_8)
        val decoded = AqlDiscoveryDatagramDecoder.decode(
            data = validBytes,
            offset = 0,
            length = validBytes.size
        )
        val malformed = byteArrayOf(0x7b, 0xc3.toByte(), 0x28, 0x7d)

        assertEquals(validV1Payload(), decoded)
        assertNull(
            AqlDiscoveryDatagramDecoder.decode(
                data = ByteArray(AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES + 1),
                offset = 0,
                length = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES + 1
            )
        )
        assertNull(
            AqlDiscoveryDatagramDecoder.decode(
                data = malformed,
                offset = 0,
                length = malformed.size
            )
        )
        assertNull(
            AqlDiscoveryDatagramDecoder.decode(
                data = validBytes,
                offset = validBytes.size,
                length = 1
            )
        )
    }

    private fun parseInvalid(json: JSONObject): AqlDiscoveryParser.ParseResult.Invalid =
        AqlDiscoveryParser.parseDeviceAnnounce(json.toString())
            as AqlDiscoveryParser.ParseResult.Invalid

    private fun validV1Payload(sentAtMillis: Long = 10L): String =
        validV1Json(sentAtMillis).toString()

    private fun validV1Json(sentAtMillis: Long = 10L): JSONObject = JSONObject()
        .put("schema", "aql.discovery.v1")
        .put("type", "device.announce")
        .put("version", 1)
        .put("sentAtMs", sentAtMillis)
        .put(
            "device",
            JSONObject()
                .put("uid", "AQL-WPE-336172")
                .put("shortId", "336172")
                .put("name", "AquaLight One")
        )
        .put(
            "product",
            JSONObject()
                .put("family", "light")
                .put("model", "AQL-LIGHT")
                .put("name", "AquaLight")
        )
        .put(
            "network",
            JSONObject()
                .put("mode", "sta")
                .put("connected", true)
        )
        .put(
            "runtime",
            JSONObject()
                .put("transport", "websocket")
                .put("host", "192.168.1.44")
                .put("port", 80)
                .put("path", "/aql/v1/ws")
                .put("protocol", "aql.ws.v1")
                .put("protocolVersion", 1)
        )
}
