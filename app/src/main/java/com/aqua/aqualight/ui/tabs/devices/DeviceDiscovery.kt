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

// ⚠️ ÖNEMLİ: 10888 portunu dinliyoruz, yoksa ESP32 cevapları bize düşmez
val socket = DatagramSocket(null).apply {
reuseAddress = true
broadcast = true
soTimeout = 300
bind(java.net.InetSocketAddress(UDP_PORT))

}

try {
// 📤 ESP32'lere RefreshUDP isteği gönder
val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddress, UDP_PORT)    
socket.send(sendPacket)    

val start = System.currentTimeMillis()    

// ⏱ timeout süresi boyunca cevapları topla    
while (System.currentTimeMillis() - start < timeoutMs) {    
    try {    
        val packet = DatagramPacket(buffer, buffer.size)    
        socket.receive(packet)    

        val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)    
        Log.d("UDP_RECEIVED", "from ${packet.address.hostAddress}: $jsonStr")    

        val parsed = parseDiscoveredDevice(jsonStr, packet.address.hostAddress)    
            ?: continue    

        // Aynı cihaz (ID veya IP) için son geleni tut    
        resultMap[parsed.first] = parsed.second    

    } catch (e: SocketTimeoutException) {    
        // küçük bekleme süresi doldu, döngü devam etsin    
    } catch (e: Exception) {    
        Log.e("UDP_RECEIVED", "parse error", e)    
    }    
}

} finally {
socket.close()
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

// 1) {"Data":{"0":{...}}} tipini dene
var deviceJson: JSONObject? = null
if (root.has("Data")) {
val dataObj = root.optJSONObject("Data")
deviceJson = dataObj?.optJSONObject("0")
}

// 2) {"NetUdp":{"Data":{"0":{...}}}} tipini dene
if (deviceJson == null && root.has("NetUdp")) {
val netUdp = root.optJSONObject("NetUdp")
val dataObj = netUdp?.optJSONObject("Data")
deviceJson = dataObj?.optJSONObject("0")
}

if (deviceJson == null) return null

val idRaw = if (deviceJson.has("ID")) deviceJson.optLong("ID", 0L) else 0L
val ip = if (deviceJson.has("IP")) deviceJson.optString("IP", srcIp) else srcIp
val name = if (deviceJson.has("Name")) deviceJson.optString("Name") else "Aqua_$idRaw"
val aquaName = if (deviceJson.has("AquaName")) deviceJson.optString("AquaName") else null
val firmware = if (deviceJson.has("FirmwareBuild")) deviceJson.optString("FirmwareBuild") else null

// ID yoksa IP hash’ini kullan
val finalId = if (idRaw != 0L) idRaw else ip.hashCode().toLong()

val discovered = DiscoveredDevice(
id = finalId,
name = name,
ip = ip,
aquaName = aquaName,
firmwareBuild = firmware
)

return finalId to discovered

}