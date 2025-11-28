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

suspend fun discoverDevices(
    context: Context,
    timeoutMs: Long = 2000L
): List<DiscoveredDevice> = withContext(Dispatchers.IO) {

    val result = linkedMapOf<Long, DiscoveredDevice>()
    val buffer = ByteArray(2048)

    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val dhcp = wifiManager.dhcpInfo

    fun Int.toInetBytes(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 24 and 0xFF).toByte()
    )

    val ipBytes = dhcp.ipAddress.toInetBytes()
    val maskBytes = dhcp.netmask.toInetBytes()
    val broadcastBytes = ByteArray(4) { i ->
        // maskBytes[i].inv() → unresolved demesin diye toInt().inv()
        ((ipBytes[i].toInt() and maskBytes[i].toInt()) or maskBytes[i].toInt().inv()).toByte()
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

                val parsed = parseDiscoveredDevice(jsonStr, packet.address.hostAddress)
                if (parsed != null) {
                    val (key, device) = parsed
                    result[key] = device
                }
            } catch (e: SocketTimeoutException) {
                // timeout → tekrar dene
            } catch (e: Exception) {
                Log.e("UDP_RECEIVED", "parse error", e)
            }
        }
    } finally {
        socket.close()
    }

    return@withContext result.values.toList()
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

    val id = deviceJson.optLong("ID", 0L)
    val ip = deviceJson.optString("IP", srcIp)
    val name = deviceJson.optString("Name", "Aqua_$id")
    val aquaName = if (deviceJson.has("AquaName")) deviceJson.optString("AquaName") else null
    val firmware = if (deviceJson.has("FirmwareBuild")) deviceJson.optString("FirmwareBuild") else null

    val device = DiscoveredDevice(
        id = id,
        name = name,
        ip = ip,
        aquaName = aquaName,
        firmwareBuild = firmware
    )

    val key = if (id != 0L) id else ip.hashCode().toLong()
    return key to device
}