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
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

private const val VER_UDP = 20240813
private const val UDP_PORT = 10888

// ---------------------------------------------------------------------
//  ESP32 cihazlarını UDP ile bulup DiscoveredDevice listesi döndürür
// ---------------------------------------------------------------------
suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    val resultMap = linkedMapOf<Long, DiscoveredDevice>()
    val buffer = ByteArray(4096)

    // 📡 Broadcast adresini DHCP'den hesapla, hata olursa 255.255.255.255'e düş
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

    val broadcastAddress: InetAddress = try {
        if (dhcp != null) {
            val ipBytes = dhcp.ipAddress.toInetBytes()
            val maskBytes = dhcp.netmask.toInetBytes()
            val broadcastBytes = ByteArray(4) { i ->
                ((ipBytes[i].toInt() and maskBytes[i].toInt()) or maskBytes[i].inv()
                    .toInt()).toByte()
            }
            InetAddress.getByAddress(broadcastBytes)
        } else {
            InetAddress.getByName("255.255.255.255")
        }
    } catch (e: Exception) {
        Log.e("UDP_DISCOVERY", "DHCP/broadcast error, fallback 255.255.255.255", e)
        InetAddress.getByName("255.255.255.255")
    }

    Log.d("UDP_DISCOVERY", "Broadcast to: ${broadcastAddress.hostAddress}:$UDP_PORT")

    DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.soTimeout = 300

        // 🔁 ESP32'lere "RefreshUDP" isteği gönder
        val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
        val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)
        val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, UDP_PORT)
        socket.send(sendPacket)

        val start = System.currentTimeMillis()

        // ⏱ timeout süresi boyunca paketleri topla
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)

                val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                Log.d("UDP_RECEIVED", "from ${packet.address.hostAddress}: $jsonStr")

                val pair = parseDiscoveredDevice(jsonStr, packet.address.hostAddress) ?: continue
                val (key, device) = pair

                // Aynı cihaza ait son gelen paketi yazsın
                resultMap[key] = device

            } catch (e: SocketTimeoutException) {
                // küçük bekleme, döngü devam
            } catch (e: Exception) {
                Log.e("UDP_RECEIVED", "parse error", e)
            }
        }
    }

    return@withContext resultMap.values.toList()
}

// ---------------------------------------------------------------------
//  JSON'u elle parse ediyoruz: hem "Data" hem "NetUdp.Data" formatını destekler
//  return: Pair<uniqueKey, DiscoveredDevice>
// ---------------------------------------------------------------------
private fun parseDiscoveredDevice(
    jsonStr: String,
    srcIp: String
): Pair<Long, DiscoveredDevice>? {
    val root = try {
        JSONObject(jsonStr)
    } catch (e: Exception) {
        return null
    }

    // 1) {"Data":{"0":{...}}}
    var deviceJson: JSONObject? = null
    if (root.has("Data")) {
        val dataObj = root.optJSONObject("Data")
        deviceJson = dataObj?.optJSONObject("0")
    }

    // 2) {"NetUdp":{"Data":{"0":{...}}}}
    if (deviceJson == null && root.has("NetUdp")) {
        val netUdp = root.optJSONObject("NetUdp")
        val dataObj = netUdp?.optJSONObject("Data")
        deviceJson = dataObj?.optJSONObject("0")
    }

    if (deviceJson == null) return null

    val idRaw = deviceJson.optLong("ID", 0L)
    val ip = if (deviceJson.has("IP")) deviceJson.optString("IP", srcIp) else srcIp
    val name = deviceJson.optString("Name", "Device")
    val aquaNameRaw = deviceJson.optString("AquaName", "")
    val firmware = deviceJson.optString("FirmwareBuild", null)

    val aquaName = if (aquaNameRaw.isNullOrBlank()) {
        if (idRaw != 0L) "Aqua_$idRaw" else "Aqua"
    } else {
        aquaNameRaw.trim()
    }

    // ID yoksa IP hash'i kullan (hem DTO id hem map key aynı olsun)
    val finalId = if (idRaw != 0L) idRaw else ip.hashCode().toLong()

    val device = DiscoveredDevice(
        id = finalId,
        aquaName = aquaName,
        name = name,
        ip = ip,
        firmwareBuild = firmware
    )

    return finalId to device
}