package com.aqua.aqualight.ui.tabs.devices

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.experimental.inv  // <-- Byte.inv() için
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

// ---------------------------------------------------------------------
//  GSON ile JSON map'i okuyacağımız DTO'lar
//  Örnek JSON: { "Data": { "0": { "ID":..., "IP":..., ... } } }
// ---------------------------------------------------------------------
data class UdpPacketRoot(
    @SerializedName("Data")
    val data: Map<String, UdpDeviceDto>?
)

data class UdpDeviceDto(
    @SerializedName("ID")          val id: Long?,
    @SerializedName("IP")          val ip: String?,
    @SerializedName("Name")        val name: String?,
    @SerializedName("AquaName")    val aquaName: String?,
    @SerializedName("FirmwareBuild") val firmwareBuild: String?
)

// ---------------------------------------------------------------------
//  ESP32 cihazlarını UDP ile bulup DiscoveredDevice listesi döndürür
// ---------------------------------------------------------------------
suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    val resultMap = linkedMapOf<Long, DiscoveredDevice>()
    val gson = Gson()
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

    val ipBytes = dhcp.ipAddress.toInetBytes()
    val maskBytes = dhcp.netmask.toInetBytes()

    // maskBytes[i] Byte olduğu için .inv() extension'ı kullanıyoruz (yukarıda import var)
    val broadcastBytes = ByteArray(4) { i ->
        ((ipBytes[i].toInt() and maskBytes[i].toInt()) or maskBytes[i].inv().toInt()).toByte()
    }
    val broadcastAddress = InetAddress.getByAddress(broadcastBytes)

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

                // JSON -> DTO
                val root = gson.fromJson(jsonStr, UdpPacketRoot::class.java)
                val dev = root.data?.values?.firstOrNull() ?: continue

                val id = dev.id ?: 0L
                val ip = dev.ip ?: packet.address.hostAddress

                val discovered = DiscoveredDevice(
                    id = if (id != 0L) id else ip.hashCode().toLong(),
                    name = dev.name ?: "Aqua_$id",
                    ip = ip,
                    aquaName = dev.aquaName,
                    firmwareBuild = dev.firmwareBuild
                )

                // Aynı cihaza ait son paketi yazsın
                resultMap[discovered.id] = discovered

            } catch (e: SocketTimeoutException) {
                // timeout, döngü devam
            } catch (e: Exception) {
                Log.e("UDP_RECEIVED", "parse error", e)
            }
        }
    } finally {
        socket.close()
    }

    return@withContext resultMap.values.toList()
}