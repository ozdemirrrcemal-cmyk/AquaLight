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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UDP v1 discovery scanner.
 *
 * The socket is bound to the canonical Android Wi-Fi/Ethernet Network when available, preventing
 * VPN or cellular default-route changes from silently moving local discovery traffic elsewhere.
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
        require(listenPort in 1..65_535) { "UDP discovery listen port is invalid." }
        require(packetSizeBytes > 0) { "UDP discovery packet limit must be positive." }
        require(receiveTimeoutMillis > 0) { "UDP discovery timeout must be positive." }
    }

    fun scan(): Flow<AqlDiscoveredDevice> = callbackFlow {
        val socketRef = AtomicReference<DatagramSocket?>()

        val job = launch(Dispatchers.IO) {
            val network = networkProvider()
            if (requireLocalNetwork && network == null) {
                return@launch
            }

            val socket = DatagramSocket(null).also { datagramSocket ->
                network?.bindSocket(datagramSocket)
                datagramSocket.reuseAddress = true
                datagramSocket.soTimeout = receiveTimeoutMillis
                datagramSocket.bind(InetSocketAddress(listenPort))
            }
            socketRef.set(socket)

            // One sentinel byte above the contract limit is required. A buffer sized exactly to
            // the limit would allow the Java UDP stack to truncate an oversized datagram and hide
            // the fact that firmware's 768-byte contract was violated.
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
                        packet = packet,
                        maxPacketSizeBytes = packetSizeBytes
                    ) ?: continue
                    val sourceIp = packet.address?.hostAddress.orEmpty()
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

/** Strict datagram boundary shared by production scanner code and contract tests. */
internal object AqlDiscoveryDatagramDecoder {
    fun decode(packet: DatagramPacket, maxPacketSizeBytes: Int): String? {
        require(maxPacketSizeBytes > 0) { "UDP discovery packet limit must be positive." }
        if (packet.length <= 0 || packet.length > maxPacketSizeBytes) return null

        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(packet.data, packet.offset, packet.length))
                .toString()
        }.getOrNull()
    }
}
