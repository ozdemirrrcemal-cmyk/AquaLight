package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import java.net.DatagramPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AqlDiscoveryDatagramDecoderTest {

    @Test
    fun `sentinel byte exposes and rejects oversized datagrams`() {
        val bytes = ByteArray(AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES + 1) { 'a'.code.toByte() }
        val packet = DatagramPacket(bytes, bytes.size)

        assertNull(
            AqlDiscoveryDatagramDecoder.decode(
                packet = packet,
                maxPacketSizeBytes = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
            )
        )
    }

    @Test
    fun `malformed UTF-8 is rejected instead of replacement decoding`() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)
        val packet = DatagramPacket(bytes, bytes.size)

        assertNull(
            AqlDiscoveryDatagramDecoder.decode(
                packet = packet,
                maxPacketSizeBytes = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
            )
        )
    }

    @Test
    fun `valid UTF-8 honors datagram offset and length`() {
        val payload = "ş-device"
        val encoded = payload.toByteArray(Charsets.UTF_8)
        val framed = byteArrayOf(0x00) + encoded + byteArrayOf(0x00)
        val packet = DatagramPacket(framed, 1, encoded.size)

        assertEquals(
            payload,
            AqlDiscoveryDatagramDecoder.decode(
                packet = packet,
                maxPacketSizeBytes = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
            )
        )
    }
}
