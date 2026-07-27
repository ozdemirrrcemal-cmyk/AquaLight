package com.aqua.aqualight.data.devices.discovery.udp

import android.net.Network
import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Sends UDP v2 discovery refresh requests.
 *
 * This sender never carries runtime commands. It only asks firmware devices to announce their
 * current discovery payload and binds each socket to the selected Android local network.
 */
class AqlDiscoveryRefreshSender(
    private val port: Int = AqlDiscoveryContract.PORT,
    private val addressResolver: () -> List<InetAddress> = AqlBroadcastAddressResolver::resolve,
    private val networkProvider: () -> Network? = { null }
) {

    suspend fun sendRefresh(): SendResult = withContext(Dispatchers.IO) {
        val payload = AqlDiscoveryContract.buildRefreshPayload().toByteArray(Charsets.UTF_8)
        val addresses = addressResolver().distinctBy { it.hostAddress }
        var successCount = 0
        var lastError: String? = null

        DatagramSocket(null).use { socket ->
            networkProvider()?.bindSocket(socket)
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(0))
            for (address in addresses) {
                runCatching {
                    val packet = DatagramPacket(payload, payload.size, address, port)
                    socket.send(packet)
                    successCount++
                }.onFailure { error ->
                    lastError = error.message ?: error.javaClass.simpleName
                }
            }
        }

        SendResult(
            attemptedAddressCount = addresses.size,
            sentAddressCount = successCount,
            lastErrorMessage = lastError
        )
    }

    suspend fun sendForegroundBurst(
        delaysMillis: List<Long> = DEFAULT_FOREGROUND_BURST_DELAYS_MS
    ): List<SendResult> {
        val results = mutableListOf<SendResult>()
        for ((index, delayMillis) in delaysMillis.withIndex()) {
            if (index > 0 && delayMillis > 0L) delay(delayMillis)
            results += sendRefresh()
        }
        return results
    }

    data class SendResult(
        val attemptedAddressCount: Int,
        val sentAddressCount: Int,
        val lastErrorMessage: String? = null
    ) {
        val hasSuccess: Boolean
            get() = sentAddressCount > 0
    }

    companion object {
        val DEFAULT_FOREGROUND_BURST_DELAYS_MS: List<Long> = listOf(0L, 1_500L, 5_000L)
    }
}
