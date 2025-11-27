package com.aqua.aqualight.ui.tabs.devices

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

// ESP32'nin UDP versiyonu (MNetUdp.hpp -> constexpr int VerUdp = 20240813;)
private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

// ESP32'nin gönderdiği "Data":{"0":{...}} içeriği
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

// Tüm UDP paketi
data class UdpPacketRoot(
    @SerializedName("Data") val data: Map<String, UdpPacketDevice>?,
    @SerializedName("Command") val command: String?,
    @SerializedName("VerUdp") val verUdp: Long?
)

/**
 * ESP32 cihazlarını UDP üzerinden bulur.
 * - 255.255.255.255:10888'e {"Command":"RefreshUDP","VerUdp":...} gönderir
 * - Gelen JSON'lardaki Data.0 içinden cihaz bilgilerini çıkarır
 */
suspend fun discoverDevices(timeoutMs: Long = 1500L): List<DiscoveredDevice> =
    withContext(Dispatchers.IO) {

        val resultMap = linkedMapOf<Long, DiscoveredDevice>()
        val gson = Gson()
        val buffer = ByteArray(2048)

        val socket = DatagramSocket().apply {
            broadcast = true
            soTimeout = 300  // her receive için timeout
        }

        try {
            // 📨 ESP32'lere "RefreshUDP" isteği gönder
            val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
            val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")

            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, UDP_PORT)
            socket.send(sendPacket)

            val start = System.currentTimeMillis()

            // ⏱ Timeout sonuna kadar gelen paketleri topla
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)  // soTimeout ile bekler

                    val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)

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

                    // Aynı ID'den tekrar gelirse son geleni yazsın
                    if (id != 0L) {
                        resultMap[id] = discovered
                    } else {
                        // ID yoksa IP üzerinden ekle (nadiren olur)
                        resultMap[ip.hashCode().toLong()] = discovered
                    }
                } catch (e: SocketTimeoutException) {
                    // Bu küçük beklemenin zamanı geldi, döngü devam edebilir
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } finally {
            socket.close()
        }

        return@withContext resultMap.values.toList()
    }