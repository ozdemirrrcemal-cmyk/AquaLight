package com.aqua.aqualight.data.devices.discovery.udp

import android.net.Network
import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UDP v1 discovery scanner bound to Android's canonical Wi-Fi/Ethernet Network.
 *
 * One sentinel byte beyond the contract maximum makes oversized datagrams observable instead of
 * silently accepting a truncated 768-byte prefix. Payload decoding is strict UTF-8.
 */
class AqlDiscoveryUdpScanner(
    private val listenPort: Int = AqlDiscoveryContract.PORT,
    private val packetSizeBytes: Int = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES,
    private val receiveTimeoutMillis: Int = DEFAULT_RECEIVE_TIMEOUT_MS,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val networkProvider: () -> Network? = { null },
    private val requireLocalNetwork: Boolean = false
) {
    init {
        require(packetSizeBytes in 1..AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES)
        require(receiveTimeoutMillis > 0)
    }

    fun scan(): Flow<AqlDiscoveredDevice> = callbackFlow {
        val socketRef = AtomicReference<DatagramSocket?>()

        val job = launch(Dispatchers.IO) {
            val network = networkProvider()
            if (requireLocalNetwork && network == null) return@launch

            val socket = DatagramSocket(null).also { datagramSocket ->
                network?.bindSocket(datagramSocket)
                datagramSocket.reuseAddress = true
                datagramSocket.soTimeout = receiveTimeoutMillis
                datagramSocket.bind(InetSocketAddress(listenPort))
            }
            socketRef.set(socket)

            val buffer = ByteArray(packetSizeBytes + OVERSIZE_SENTINEL_BYTES)

            try {
                while (isActive && !socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (_: SocketException) {
                        break
                    }

                    val rawPayload = AqlDiscoveryDatagramDecoder.decode(
                        data = packet.data,
                        offset = packet.offset,
                        length = packet.length,
                        maximumBytes = packetSizeBytes
                    ) ?: continue
                    val sourceIp = packet.address?.hostAddress ?: continue
                    val receivedAt = clockMillis()

                    when (
                        val result = AqlDiscoveryParser.parseDeviceAnnounce(
                            rawPayload = rawPayload,
                            sourceIp = sourceIp,
                            receivedAtMillis = receivedAt
                        )
                    ) {
                        is AqlDiscoveryParser.ParseResult.Valid -> trySend(result.device)
                        is AqlDiscoveryParser.ParseResult.Invalid -> Unit
                    }
                }
            } finally {
                socketRef.getAndSet(null)?.close()
            }
        }

        awaitClose {
            job.cancel()
            socketRef.getAndSet(null)?.close()
        }
    }

    companion object {
        private const val DEFAULT_RECEIVE_TIMEOUT_MS = 1_000
        private const val OVERSIZE_SENTINEL_BYTES = 1
    }
}

internal object AqlDiscoveryDatagramDecoder {
    fun decode(
        data: ByteArray,
        offset: Int,
        length: Int,
        maximumBytes: Int = AqlDiscoveryContract.MAX_PACKET_SIZE_BYTES
    ): String? {
        if (maximumBytes <= 0 || length <= 0 || length > maximumBytes) return null
        if (offset < 0 || offset > data.size || length > data.size - offset) return null

        return try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, offset, length))
                .toString()
        } catch (_: Throwable) {
            null
        }
    }
}
