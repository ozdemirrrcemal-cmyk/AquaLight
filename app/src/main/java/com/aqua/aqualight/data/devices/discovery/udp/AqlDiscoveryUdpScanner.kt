package com.aqua.aqualight.data.devices.discovery.udp

import android.net.Network
import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UDP v2 discovery scanner.
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

            val buffer = ByteArray(packetSizeBytes)

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

                    val rawPayload = packet.toPayloadString()
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

    private fun DatagramPacket.toPayloadString(): String =
        String(data, offset, length, Charsets.UTF_8)

    companion object {
        private const val DEFAULT_RECEIVE_TIMEOUT_MS = 1_000
    }
}
