package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext

object UdpDiscoveryDiagnostics {

    private const val VER_UDP = 20240813
    private const val UDP_PORT = 10888
    private const val SOCKET_TIMEOUT_MS = 300
    private const val BUFFER_SIZE = 4096

    data class Result(
        val broadcastIp: String,
        val port: Int,
        val sent: Boolean,
        val rawResponses: List<RawResponse>,
        val acceptedDevices: List<AcceptedDevice>,
        val rejectedResponses: List<RejectedResponse>,
        val error: String?
    ) {
        fun toDisplayText(): String {
            return buildString {
                appendLine("UDP Discovery Diagnostics")
                appendLine()
                appendLine("Broadcast: $broadcastIp:$port")
                appendLine("Packet sent: $sent")
                appendLine("Raw responses: ${rawResponses.size}")
                appendLine("Accepted devices: ${acceptedDevices.size}")
                appendLine("Rejected responses: ${rejectedResponses.size}")

                if (!error.isNullOrBlank()) {
                    appendLine()
                    appendLine("Error:")
                    appendLine(error)
                }

                if (acceptedDevices.isNotEmpty()) {
                    appendLine()
                    appendLine("Accepted:")
                    acceptedDevices.forEach { device ->
                        appendLine("- ${device.aquaName} / ${device.name}")
                        appendLine("  id=${device.id}")
                        appendLine("  ip=${device.ip}")
                        appendLine("  type=${device.deviceType}")
                    }
                }

                if (rejectedResponses.isNotEmpty()) {
                    appendLine()
                    appendLine("Rejected:")
                    rejectedResponses.forEachIndexed { index, rejected ->
                        appendLine("${index + 1}. ${rejected.reason}")
                        appendLine(rejected.raw.take(700))
                    }
                }

                if (rawResponses.isNotEmpty()) {
                    appendLine()
                    appendLine("Raw JSON:")
                    rawResponses.forEachIndexed { index, raw ->
                        appendLine("${index + 1}. from ${raw.sourceIp}")
                        appendLine(raw.body.take(900))
                    }
                }
            }
        }
    }

    data class RawResponse(
        val sourceIp: String,
        val body: String
    )

    data class AcceptedDevice(
        val id: Long,
        val ip: String,
        val aquaName: String,
        val name: String,
        val deviceType: AquaDeviceType
    )

    data class RejectedResponse(
        val reason: String,
        val raw: String
    )

    suspend fun run(
        context: Context,
        timeoutMs: Long = 3_000L
    ): Result = withContext(Dispatchers.IO) {
        val rawResponses = mutableListOf<RawResponse>()
        val acceptedDevices = mutableListOf<AcceptedDevice>()
        val rejectedResponses = mutableListOf<RejectedResponse>()

        val broadcastAddress = getBroadcastAddress(context)
        val buffer = ByteArray(BUFFER_SIZE)

        var sent = false
        var error: String? = null

        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = SOCKET_TIMEOUT_MS
                bind(InetSocketAddress(UDP_PORT))
            }
        } catch (exception: Exception) {
            return@withContext Result(
                broadcastIp = broadcastAddress.hostAddress.orEmpty(),
                port = UDP_PORT,
                sent = false,
                rawResponses = emptyList(),
                acceptedDevices = emptyList(),
                rejectedResponses = emptyList(),
                error = "Socket open failed: ${exception.message}"
            )
        }

        try {
            coroutineContext.ensureActive()

            val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
            val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                broadcastAddress,
                UDP_PORT
            )

            socket.send(sendPacket)
            sent = true

            val startTime = android.os.SystemClock.elapsedRealtime()

            while (
                coroutineContext.isActive &&
                android.os.SystemClock.elapsedRealtime() - startTime < timeoutMs
            ) {
                coroutineContext.ensureActive()

                try {
                    val packet = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    socket.receive(packet)

                    val sourceIp = packet.address.hostAddress.orEmpty()

                    val body = String(
                        packet.data,
                        0,
                        packet.length,
                        StandardCharsets.UTF_8
                    )

                    rawResponses.add(
                        RawResponse(
                            sourceIp = sourceIp,
                            body = body
                        )
                    )

                    val parsed = parseRawResponse(
                        raw = body,
                        sourceIp = sourceIp
                    )

                    if (parsed.accepted != null) {
                        acceptedDevices.add(parsed.accepted)
                    } else {
                        rejectedResponses.add(
                            RejectedResponse(
                                reason = parsed.rejectReason ?: "Unknown reject reason",
                                raw = body
                            )
                        )
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal.
                } catch (exception: Exception) {
                    rejectedResponses.add(
                        RejectedResponse(
                            reason = "Receive/parse error: ${exception.message}",
                            raw = ""
                        )
                    )
                }
            }
        } catch (exception: Exception) {
            error = exception.message ?: exception.toString()
        } finally {
            socket.close()
        }

        return@withContext Result(
            broadcastIp = broadcastAddress.hostAddress.orEmpty(),
            port = UDP_PORT,
            sent = sent,
            rawResponses = rawResponses,
            acceptedDevices = acceptedDevices,
            rejectedResponses = rejectedResponses,
            error = error
        )
    }

    private data class ParseResult(
        val accepted: AcceptedDevice?,
        val rejectReason: String?
    )

    private fun parseRawResponse(
        raw: String,
        sourceIp: String
    ): ParseResult {
        val root = try {
            JSONObject(raw)
        } catch (exception: Exception) {
            return ParseResult(
                accepted = null,
                rejectReason = "Invalid JSON"
            )
        }

        val deviceJson = extractDeviceJson(root)
            ?: return ParseResult(
                accepted = null,
                rejectReason = "Device JSON not found. Expected Data.0 or NetUdp.Data.0"
            )

        val idRaw = deviceJson.optLong(
            "ID",
            0L
        )

        val ip = deviceJson
            .optString("IP", sourceIp)
            .ifBlank { sourceIp }

        val finalId = if (idRaw > 0L) {
            idRaw
        } else {
            createStableIdFromIp(ip)
        }

        if (finalId <= 0L) {
            return ParseResult(
                accepted = null,
                rejectReason = "Invalid device id"
            )
        }

        if (ip.isBlank()) {
            return ParseResult(
                accepted = null,
                rejectReason = "Blank device ip"
            )
        }

        val aquaName = deviceJson
            .optString("AquaName", "")
            .trim()

        val name = deviceJson
            .optString("Name", "")
            .trim()
            .ifBlank { "Aqua_$finalId" }

        val productId = deviceJson
            .optString("ProductId", "")
            .trim()
            .ifBlank { null }

        if (aquaName.isBlank() && name.isBlank() && productId.isNullOrBlank()) {
            return ParseResult(
                accepted = null,
                rejectReason = "Missing AquaName, Name and ProductId"
            )
        }

        val typeByProductId = AquaDeviceCatalog.resolveTypeByProductId(
            productId = productId
        )

        val resolvedType = if (typeByProductId != AquaDeviceType.UNKNOWN) {
            typeByProductId
        } else {
            AquaDeviceCatalog.resolveTypeByLegacyIdentity(
                aquaName = aquaName,
                name = name
            )
        }

        if (resolvedType == AquaDeviceType.UNKNOWN) {
            return ParseResult(
                accepted = null,
                rejectReason = "Unsupported identity. AquaName=$aquaName, Name=$name, ProductId=$productId"
            )
        }

        return ParseResult(
            accepted = AcceptedDevice(
                id = finalId,
                ip = ip,
                aquaName = aquaName,
                name = name,
                deviceType = resolvedType
            ),
            rejectReason = null
        )
    }

    private fun extractDeviceJson(
        root: JSONObject
    ): JSONObject? {
        root.optJSONObject("Data")
            ?.optJSONObject("0")
            ?.let { return it }

        root.optJSONObject("NetUdp")
            ?.optJSONObject("Data")
            ?.optJSONObject("0")
            ?.let { return it }

        return null
    }

    private fun getBroadcastAddress(
        context: Context
    ): InetAddress {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        val dhcp = wifiManager.dhcpInfo

        if (
            dhcp == null ||
            dhcp.ipAddress == 0 ||
            dhcp.netmask == 0
        ) {
            return InetAddress.getByName("255.255.255.255")
        }

        val ipBytes = dhcp.ipAddress.toLittleEndianBytes()
        val maskBytes = dhcp.netmask.toLittleEndianBytes()

        val broadcastBytes = ByteArray(4) { index ->
            val ip = ipBytes[index].toInt() and 0xFF
            val mask = maskBytes[index].toInt() and 0xFF
            val inverseMask = mask.inv() and 0xFF

            ((ip and mask) or inverseMask).toByte()
        }

        return InetAddress.getByAddress(broadcastBytes)
    }

    private fun Int.toLittleEndianBytes(): ByteArray {
        return byteArrayOf(
            (this and 0xFF).toByte(),
            ((this shr 8) and 0xFF).toByte(),
            ((this shr 16) and 0xFF).toByte(),
            ((this shr 24) and 0xFF).toByte()
        )
    }

    private fun createStableIdFromIp(
        ip: String
    ): Long {
        return ip.hashCode().toLong() and 0x00000000FFFFFFFFL
    }
}