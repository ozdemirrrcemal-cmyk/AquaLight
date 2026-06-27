package com.aqua.aqualight.data.devices.discovery.udp

import com.aqua.aqualight.data.devices.contract.AqlDiscoveryContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends UDP v2 discovery refresh requests.
 *
 * This sender never carries runtime commands. It only asks firmware devices to announce their
 * current UDP v2 discovery payload. Runtime control belongs to WebSocket.
 */
class AqlDiscoveryRefreshSender(
    private val port: Int = AqlDiscoveryContract.PORT,
    private val addressResolver: () -> List<InetAddress> = AqlBroadcastAddressResolver::resolve
) {

    suspend fun sendRefresh(): SendResult = withContext(Dispatchers.IO) {
        val payload = AqlDiscoveryContract.buildRefreshPayload().toByteArray(Charsets.UTF_8)
        val addresses = addressResolver().distinctBy { it.hostAddress }
        var successCount = 0
        var lastError: String? = null

        DatagramSocket().use { socket ->
            socket.broadcast = true
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
