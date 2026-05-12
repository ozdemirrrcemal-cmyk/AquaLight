package com.aqua.aqualight.ui.tabs.devices

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
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

private const val TAG_DISCOVERY = "UDP_DISCOVERY"
private const val TAG_RECEIVED = "UDP_RECEIVED"

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888
private const val SOCKET_TIMEOUT_MS = 300
private const val BUFFER_SIZE = 2048

suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    val resultMap = linkedMapOf<Long, DiscoveredDevice>()
    val buffer = ByteArray(BUFFER_SIZE)

    val broadcastAddress = getBroadcastAddress(context)

    Log.d(
        TAG_DISCOVERY,
        "Broadcast to: ${broadcastAddress.hostAddress}:$UDP_PORT"
    )

    val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = SOCKET_TIMEOUT_MS
        bind(InetSocketAddress(UDP_PORT))
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

        val startTime = SystemClock.elapsedRealtime()

        while (
            coroutineContext.isActive &&
            SystemClock.elapsedRealtime() - startTime < timeoutMs
        ) {
            coroutineContext.ensureActive()

            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val jsonStr = String(
                    packet.data,
                    0,
                    packet.length,
                    StandardCharsets.UTF_8
                )

                val sourceIp = packet.address.hostAddress ?: continue

                Log.d(TAG_RECEIVED, "from $sourceIp: $jsonStr")

                val parsed = parseDiscoveredDevice(
                    jsonStr = jsonStr,
                    srcIp = sourceIp
                ) ?: continue

                resultMap[parsed.first] = parsed.second

            } catch (e: SocketTimeoutException) {
                // Normal durum. Küçük timeout ile cancellation kontrolü yapılır.
            } catch (e: Exception) {
                Log.e(TAG_RECEIVED, "Packet parse error", e)
            }
        }

    } finally {
        socket.close()
    }

    return@withContext resultMap.values.toList()
}

private fun getBroadcastAddress(context: Context): InetAddress {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val dhcp = wifiManager.dhcpInfo

    if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) {
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

private fun parseDiscoveredDevice(
    jsonStr: String,
    srcIp: String
): Pair<Long, DiscoveredDevice>? {

    val root = try {
        JSONObject(jsonStr)
    } catch (e: Exception) {
        return null
    }

    val deviceJson = extractDeviceJson(root) ?: return null

    val idRaw = deviceJson.optLong("ID", 0L)

    val ip = deviceJson
        .optString("IP", srcIp)
        .ifBlank { srcIp }

    val name = deviceJson
        .optString("Name", "")
        .ifBlank { "Aqua_$idRaw" }

    val aquaName = deviceJson
        .optString("AquaName", "")
        .ifBlank { null }

    val firmware = deviceJson
        .optString("FirmwareBuild", "")
        .ifBlank { null }

    val finalId = if (idRaw > 0L) {
        idRaw
    } else {
        createStableIdFromIp(ip)
    }

    if (finalId <= 0L) return null
    if (ip.isBlank()) return null
    if (name.isBlank() && aquaName.isNullOrBlank()) return null

    val discovered = DiscoveredDevice(
        id = finalId,
        name = name,
        ip = ip,
        aquaName = aquaName,
        firmwareBuild = firmware
    )

    return finalId to discovered
}

private fun extractDeviceJson(root: JSONObject): JSONObject? {
    root.optJSONObject("Data")
        ?.optJSONObject("0")
        ?.let { return it }

    root.optJSONObject("NetUdp")
        ?.optJSONObject("Data")
        ?.optJSONObject("0")
        ?.let { return it }

    return null
}

private fun createStableIdFromIp(ip: String): Long {
    return ip.hashCode().toLong() and 0x00000000FFFFFFFFL
}