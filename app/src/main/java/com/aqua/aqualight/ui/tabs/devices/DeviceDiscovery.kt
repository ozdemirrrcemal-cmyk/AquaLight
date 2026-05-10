package com.aqua.aqualight.ui.tabs.devices

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

// 🔥 TEK SOCKET LOCK (çift scan fix)
@Volatile
private var activeSocket: DatagramSocket? = null

suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    // 🔥 eski scan varsa KES
    try {
        activeSocket?.close()
    } catch (_: Exception) {}
    activeSocket = null

    val resultMap = linkedMapOf<Long, DiscoveredDevice>()
    val buffer = ByteArray(2048)

    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val dhcp = wifiManager.dhcpInfo

    fun Int.toInetBytes(): ByteArray {
        return byteArrayOf(
            (this and 0xFF).toByte(),
            (this shr 8 and 0xFF).toByte(),
            (this shr 16 and 0xFF).toByte(),
            (this shr 24 and 0xFF).toByte()
        )
    }

    val broadcastAddress: InetAddress = if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {
        val ipBytes = dhcp.ipAddress.toInetBytes()
        val maskBytes = dhcp.netmask.toInetBytes()

        val broadcastBytes = ByteArray(4) { i ->
            val ip = ipBytes[i].toInt() and 0xFF
            val mask = maskBytes[i].toInt() and 0xFF
            val invMask = mask.inv() and 0xFF
            (ip and mask or invMask).toByte()
        }

        InetAddress.getByAddress(broadcastBytes)
    } else {
        InetAddress.getByName("255.255.255.255")
    }

    Log.d("UDP_DISCOVERY", "Broadcast to: ${broadcastAddress.hostAddress}:$UDP_PORT")

    // 🔥 STABLE SOCKET (çakışma fix)
    val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = 400
        bind(InetSocketAddress(UDP_PORT))
    }

    activeSocket = socket

    try {
        // 📤 REQUEST
        val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
        val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

        val sendPacket = DatagramPacket(
            sendData,
            sendData.size,
            broadcastAddress,
            UDP_PORT
        )

        socket.send(sendPacket)

        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                Log.d("UDP_RECEIVED", "${packet.address.hostAddress}: $jsonStr")

                val parsed = parseDiscoveredDevice(jsonStr, packet.address.hostAddress)
                    ?: continue

                resultMap[parsed.first] = parsed.second

            } catch (e: SocketTimeoutException) {
                // normal
            } catch (e: Exception) {
                Log.e("UDP_RECEIVED", "parse error", e)
            }
        }

    } finally {
        try {
            socket.close()
        } catch (_: Exception) {}

        if (activeSocket == socket) {
            activeSocket = null
        }
    }

    return@withContext resultMap.values.toList()
}