package com.aqua.aqualight.ui.tabs.devices

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

data class UdpPacketDevice(
    @SerializedName("ID") val id: Long? = null,
    @SerializedName("IP") val ip: String? = null,
    @SerializedName("Name") val name: String? = null,
    @SerializedName("AquaName") val aquaName: String? = null,
    @SerializedName("CloneName") val cloneName: String? = null,
    @SerializedName("FirmwareBuild") val firmwareBuild: String? = null,
    @SerializedName("TabLight") val tabLight: Int? = null,
    @SerializedName("TabTimer") val tabTimer: Int? = null,
    @SerializedName("TabTemperature") val tabTemperature: Int? = null
)

data class UdpPacketRoot(
    @SerializedName("Data") val data: Map<String, UdpPacketDevice>?,
    @SerializedName("Command") val command: String?,
    @SerializedName("VerUdp") val verUdp: Long?
)

suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    val resultMap = linkedMapOf<Long, DiscoveredDevice>()
    val gson = Gson()
    val buffer = ByteArray(2048)

    // 📡 Wi-Fi broadcast adresini DHCP'den hesapla
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
        // Fallback: global broadcast
        InetAddress.getByName("255.255.255.255")
    }

    Log.d("UDP_DISCOVERY", "Broadcast to: ${broadcastAddress.hostAddress}:$UDP_PORT")

    val socket = DatagramSocket().apply {
        broadcast = true
        soTimeout = 300
    }

    try {
        val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
        val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

        val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, UDP_PORT)
        socket.send(sendPacket)

        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                Log.d("UDP_RECEIVED", "from ${packet.address.hostAddress}: $jsonStr")

                val root = gson.fromJson(jsonStr, UdpPacketRoot::class.java)
                val dev = root.data?.values?.firstOrNull() ?: continue

                val id = dev.id ?: 0L
                val ip = dev.ip ?: packet.address.hostAddress

                val discovered = DiscoveredDevice(
                    name = dev.name ?: "Aqua_$id",
                    ip = ip,
                    aquaName = dev.aquaName,
                    firmwareBuild = dev.firmwareBuild
                )

                if (id != 0L) {
                    resultMap[id] = discovered
                } else {
                    resultMap[ip.hashCode().toLong()] = discovered
                }
            } catch (e: SocketTimeoutException) {
                // zaman doldu, döngü devam etsin
            } catch (e: Exception) {
                Log.e("UDP_RECEIVED", "parse error", e)
            }
        }
    } finally {
        socket.close()
    }

    return@withContext resultMap.values.toList()
}