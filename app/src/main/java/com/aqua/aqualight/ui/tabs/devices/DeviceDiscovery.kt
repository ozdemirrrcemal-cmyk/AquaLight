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
import java.util.concurrent.atomic.AtomicBoolean

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

private val discoveryRunning = AtomicBoolean(false)

suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    if (!discoveryRunning.compareAndSet(false, true)) {
        Log.d("UDP_DISCOVERY", "Discovery already running")
        return@withContext emptyList()
    }

    try {
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

        val broadcastAddress: InetAddress =
            if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {

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

        Log.d(
            "UDP_DISCOVERY",
            "Broadcast to ${broadcastAddress.hostAddress}:$UDP_PORT"
        )

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = 300

            // boş local port seç
            bind(InetSocketAddress(0))
        }

        try {
            val requestJson =
                """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""

            val sendData =
                requestJson.toByteArray(StandardCharsets.UTF_8)

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
                    val packet =
                        DatagramPacket(buffer, buffer.size)

                    socket.receive(packet)

                    val jsonStr = String(
                        packet.data,
                        0,
                        packet.length,
                        Charsets.UTF_8
                    )

                    Log.d(
                        "UDP_RECEIVED",
                        "from ${packet.address.hostAddress}: $jsonStr"
                    )

                    val parsed =
                        parseDiscoveredDevice(
                            jsonStr,
                            packet.address.hostAddress
                        ) ?: continue

                    resultMap[parsed.first] = parsed.second

                } catch (_: SocketTimeoutException) {
                } catch (e: Exception) {
                    Log.e("UDP_RECEIVED", "parse error", e)
                }
            }

        } finally {
            socket.close()
        }

        return@withContext resultMap.values.toList()

    } finally {
        discoveryRunning.set(false)
    }
}

private fun parseDiscoveredDevice(
    jsonStr: String,
    srcIp: String
): Pair<Long, DiscoveredDevice>? {

    val root = try {
        JSONObject(jsonStr)
    } catch (_: Exception) {
        return null
    }

    var deviceJson: JSONObject? = null

    if (root.has("Data")) {
        deviceJson =
            root.optJSONObject("Data")
                ?.optJSONObject("0")
    }

    if (deviceJson == null && root.has("NetUdp")) {
        deviceJson =
            root.optJSONObject("NetUdp")
                ?.optJSONObject("Data")
                ?.optJSONObject("0")
    }

    if (deviceJson == null) return null

    val idRaw = deviceJson.optLong("ID", 0L)
    val ip = deviceJson.optString("IP", srcIp)
    val name = deviceJson.optString("Name", "Aqua_$idRaw")
    val aquaName = deviceJson.optString("AquaName", null)
    val firmware =
        deviceJson.optString("FirmwareBuild", null)

    val finalId =
        if (idRaw != 0L) idRaw
        else ip.hashCode().toLong()

    val discovered = DiscoveredDevice(
        id = finalId,
        name = name,
        ip = ip,
        aquaName = aquaName,
        firmwareBuild = firmware
    )

    return finalId to discovered
}