package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceType
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.coroutineContext

object UdpDeviceDiscovery {

    private const val TAG_DISCOVERY = "UDP_DISCOVERY"
    private const val TAG_RECEIVED = "UDP_RECEIVED"

    private const val VER_UDP = 20240813
    private const val UDP_PORT = 10888
    private const val SOCKET_TIMEOUT_MS = 300
    private const val BUFFER_SIZE = 4096

    suspend fun discover(
        context: Context,
        timeoutMs: Long = 3_000L
    ): List<DiscoveredAquaDevice> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resultMap = linkedMapOf<Long, DiscoveredAquaDevice>()
        val buffer = ByteArray(BUFFER_SIZE)

        val wifiManager = appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

        val multicastLock = wifiManager.createMulticastLock(
            "AquaLightUdpDiscovery"
        ).apply {
            setReferenceCounted(false)
        }

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = SOCKET_TIMEOUT_MS
            bind(InetSocketAddress(UDP_PORT))
        }

        try {
            runCatching {
                multicastLock.acquire()
            }

            coroutineContext.ensureActive()

            val broadcastTargets = getBroadcastTargets(appContext)

            Log.d(
                TAG_DISCOVERY,
                "Broadcast targets: ${broadcastTargets.joinToString { it.hostAddress.orEmpty() }}"
            )

            sendDiscoveryPackets(
                socket = socket,
                targets = broadcastTargets
            )

            val startTime = SystemClock.elapsedRealtime()

            while (
                coroutineContext.isActive &&
                SystemClock.elapsedRealtime() - startTime < timeoutMs
            ) {
                coroutineContext.ensureActive()

                try {
                    val packet = DatagramPacket(
                        buffer,
                        buffer.size
                    )

                    socket.receive(packet)

                    val jsonString = String(
                        packet.data,
                        0,
                        packet.length,
                        StandardCharsets.UTF_8
                    )

                    val sourceIp = packet.address.hostAddress ?: continue

                    Log.d(
                        TAG_RECEIVED,
                        "from $sourceIp: $jsonString"
                    )

                    val discoveredDevice = parseDiscoveredDevice(
                        jsonString = jsonString,
                        sourceIp = sourceIp
                    ) ?: continue

                    Log.d(
                        TAG_DISCOVERY,
                        "Accepted device id=${discoveredDevice.id}, ip=${discoveredDevice.ip}, aquaName=${discoveredDevice.aquaName}, name=${discoveredDevice.name}, type=${discoveredDevice.deviceType}"
                    )

                    resultMap[discoveredDevice.id] = discoveredDevice
                } catch (exception: SocketTimeoutException) {
                    // Normal durum. Timeout küçük tutulur ki coroutine cancellation kontrolü yapılabilsin.
                } catch (exception: Exception) {
                    Log.e(
                        TAG_RECEIVED,
                        "Packet parse error",
                        exception
                    )
                }
            }
        } finally {
            runCatching {
                if (multicastLock.isHeld) {
                    multicastLock.release()
                }
            }

            socket.close()
        }

        return@withContext resultMap.values.toList()
    }

    private suspend fun sendDiscoveryPackets(
        socket: DatagramSocket,
        targets: List<InetAddress>
    ) {
        val requestJson = """{"Command":"RefreshUDP","VerUdp":$VER_UDP}"""
        val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

        repeat(3) { round ->
            coroutineContext.ensureActive()

            targets.forEach { target ->
                try {
                    val sendPacket = DatagramPacket(
                        sendData,
                        sendData.size,
                        target,
                        UDP_PORT
                    )

                    socket.send(sendPacket)

                    Log.d(
                        TAG_DISCOVERY,
                        "Sent discovery round=${round + 1} to ${target.hostAddress}:$UDP_PORT"
                    )
                } catch (exception: Exception) {
                    Log.e(
                        TAG_DISCOVERY,
                        "Send failed to ${target.hostAddress}",
                        exception
                    )
                }
            }

            delay(120L)
        }
    }

    private fun parseDiscoveredDevice(
        jsonString: String,
        sourceIp: String
    ): DiscoveredAquaDevice? {
        val root = try {
            JSONObject(jsonString)
        } catch (exception: Exception) {
            Log.e(
                TAG_RECEIVED,
                "Invalid JSON: $jsonString",
                exception
            )
            return null
        }

        val deviceJson = extractDeviceJson(root)

        if (deviceJson == null) {
            Log.d(
                TAG_RECEIVED,
                "Device JSON not found in response: $jsonString"
            )
            return null
        }

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
            Log.d(TAG_RECEIVED, "Rejected: invalid id")
            return null
        }

        if (ip.isBlank()) {
            Log.d(TAG_RECEIVED, "Rejected: blank ip")
            return null
        }

        val aquaName = deviceJson
            .optString("AquaName", "")
            .ifBlank { "" }

        val name = deviceJson
            .optString("Name", "")
            .ifBlank { "Aqua_$finalId" }

        if (aquaName.isBlank() && name.isBlank()) {
            Log.d(TAG_RECEIVED, "Rejected: blank AquaName and Name")
            return null
        }

        val firmwareBuild = deviceJson
            .optString("FirmwareBuild", "")
            .ifBlank { "" }

        val udpVersion = root.optNullableInt("VerUdp")
            ?: deviceJson.optNullableInt("VerUdp")

        val productId = deviceJson
            .optString("ProductId", "")
            .ifBlank { null }

        val productFamily = deviceJson
            .optString("ProductFamily", "")
            .ifBlank { null }

        val productModel = deviceJson
            .optString("ProductModel", "")
            .ifBlank { null }

        val hardwareRevision = deviceJson
            .optString("HardwareRevision", "")
            .ifBlank { null }

        val firmwareVersion = deviceJson
            .optString("FirmwareVersion", "")
            .ifBlank { null }

        val apiVersion = deviceJson.optNullableInt("ApiVersion")

        val supportedFeatures = deviceJson.readStringSet(
            key = "SupportedFeatures"
        )

        val supportedScreens = deviceJson.readStringSet(
            key = "SupportedScreens"
        )

        val channelCount = deviceJson.optNullableInt("ChannelCount")
        val sensorCount = deviceJson.optNullableInt("SensorCount")

        val tabLight = deviceJson.optFlexibleBoolean(
            key = "TabLight"
        )

        val tabTimer = deviceJson.optFlexibleBoolean(
            key = "TabTimer"
        )

        val tabTemperature = deviceJson.optFlexibleBoolean(
            key = "TabTemperature"
        )

        val resolvedType = resolveDeviceType(
            productId = productId,
            aquaName = aquaName,
            name = name
        )

        if (resolvedType == AquaDeviceType.UNKNOWN) {
            Log.d(
                TAG_RECEIVED,
                "Rejected unsupported device: aquaName=$aquaName, name=$name, productId=$productId"
            )
            return null
        }

        return DiscoveredAquaDevice(
            id = finalId,
            ip = ip,

            aquaName = aquaName,
            name = name,

            productId = productId,
            productFamily = productFamily,
            productModel = productModel,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            apiVersion = apiVersion,

            firmwareBuild = firmwareBuild,
            udpVersion = udpVersion,

            tabLight = tabLight,
            tabTimer = tabTimer,
            tabTemperature = tabTemperature,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens,

            channelCount = channelCount,
            sensorCount = sensorCount,

            deviceType = resolvedType
        )
    }

    private fun resolveDeviceType(
        productId: String?,
        aquaName: String,
        name: String
    ): AquaDeviceType {
        val byProductId = AquaDeviceCatalog.resolveTypeByProductId(
            productId = productId
        )

        if (byProductId != AquaDeviceType.UNKNOWN) {
            return byProductId
        }

        return AquaDeviceCatalog.resolveTypeByLegacyIdentity(
            aquaName = aquaName,
            name = name
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

        root.optJSONObject("Data")?.let { dataObject ->
            dataObject.keys().forEach { key ->
                val item = dataObject.optJSONObject(key)
                if (item != null) {
                    return item
                }
            }
        }

        root.optJSONArray("Data")?.let { dataArray ->
            for (index in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(index)
                if (item != null) {
                    return item
                }
            }
        }

        if (
            root.has("AquaName") ||
            root.has("Name") ||
            root.has("ProductId")
        ) {
            return root
        }

        return null
    }

    private fun getBroadcastTargets(
        context: Context
    ): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()

        runCatching {
            targets.add(getDirectedBroadcastAddress(context))
        }

        runCatching {
            targets.add(InetAddress.getByName("255.255.255.255"))
        }

        return targets.toList()
    }

    private fun getDirectedBroadcastAddress(
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

    private fun JSONObject.optNullableInt(
        key: String
    ): Int? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return try {
            when (val value = get(key)) {
                is Number -> value.toInt()
                is String -> value.trim().toIntOrNull()
                else -> null
            }
        } catch (exception: Exception) {
            null
        }
    }

    private fun JSONObject.optFlexibleBoolean(
        key: String
    ): Boolean {
        if (!has(key) || isNull(key)) {
            return false
        }

        return try {
            when (val value = get(key)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> {
                    when (value.trim().lowercase(Locale.US)) {
                        "1", "true", "yes", "on" -> true
                        else -> false
                    }
                }

                else -> false
            }
        } catch (exception: Exception) {
            false
        }
    }

    private fun JSONObject.readStringSet(
        key: String
    ): Set<String> {
        if (!has(key) || isNull(key)) {
            return emptySet()
        }

        return try {
            when (val value = get(key)) {
                is JSONArray -> {
                    buildSet {
                        for (index in 0 until value.length()) {
                            val item = value.optString(index, "")
                                .trim()

                            if (item.isNotBlank()) {
                                add(item)
                            }
                        }
                    }
                }

                is String -> {
                    value.split(",")
                        .map { item -> item.trim() }
                        .filter { item -> item.isNotBlank() }
                        .toSet()
                }

                else -> emptySet()
            }
        } catch (exception: Exception) {
            emptySet()
        }
    }
}