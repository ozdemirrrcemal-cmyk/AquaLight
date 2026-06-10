package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
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
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

        val targets = getBroadcastTargets(appContext)

        Log.d(
            TAG_DISCOVERY,
            "Broadcast targets: ${targets.joinToString { it.hostAddress.orEmpty() }}:$UDP_PORT"
        )

        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            soTimeout = SOCKET_TIMEOUT_MS
            bind(InetSocketAddress(UDP_PORT))
        }

        try {
            coroutineContext.ensureActive()

            sendDiscoveryPackets(
                socket = socket,
                targets = targets
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

                    resultMap[discoveredDevice.id] = discoveredDevice
                } catch (_: SocketTimeoutException) {
                    // Normal.
                } catch (exception: Exception) {
                    Log.e(
                        TAG_RECEIVED,
                        "Packet parse error",
                        exception
                    )
                }
            }
        } finally {
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

        repeat(3) {
            coroutineContext.ensureActive()

            targets.forEach { target ->
                runCatching {
                    val sendPacket = DatagramPacket(
                        sendData,
                        sendData.size,
                        target,
                        UDP_PORT
                    )

                    socket.send(sendPacket)
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
            return null
        }

        if (isSelfRefreshPacket(root)) {
            return null
        }

        val deviceJson = extractDeviceJson(root) ?: return null

        val idRaw = deviceJson.optLong(
            "ID",
            0L
        )

        val deviceUid = deviceJson.firstNonBlankString(
            "DeviceUid",
            "DeviceUID",
            "UID",
            "DeviceId",
            "DeviceID"
        )

        val macAddress = deviceJson.firstNonBlankString(
            "MacAddress",
            "MAC",
            "Mac",
            "macAddress"
        )

        val serialNumber = deviceJson.firstNonBlankString(
            "SerialNumber",
            "Serial",
            "FirmwareSerial"
        )

        val espChipId = deviceJson.optNullableLong("ESPChipID")

        val ip = deviceJson
            .optString("IP", sourceIp)
            .ifBlank {
                sourceIp
            }

        val finalId = when {
            idRaw > 0L -> idRaw
            espChipId != null && espChipId > 0L -> espChipId
            !deviceUid.isNullOrBlank() -> createStableIdFromIdentity(deviceUid)
            !serialNumber.isNullOrBlank() -> createStableIdFromIdentity(serialNumber)
            !macAddress.isNullOrBlank() -> createStableIdFromIdentity(macAddress)
            else -> createStableIdFromIp(ip)
        }

        if (finalId <= 0L) {
            return null
        }

        if (ip.isBlank()) {
            return null
        }

        val aquaName = deviceJson
            .optString("AquaName", "")
            .ifBlank {
                ""
            }

        val name = deviceJson
            .optString("Name", "")
            .ifBlank {
                "Aqua_$finalId"
            }

        if (aquaName.isBlank() && name.isBlank()) {
            return null
        }

        val firmwareBuild = deviceJson
            .optString("FirmwareBuild", "")
            .ifBlank {
                ""
            }

        val udpVersion = root
            .optNullableInt("VerUdp")
            ?: deviceJson.optNullableInt("VerUdp")

        val productId = deviceJson
            .optString("ProductId", "")
            .ifBlank {
                null
            }

        val productFamily = deviceJson
            .optString("ProductFamily", "")
            .ifBlank {
                null
            }

        val productModel = deviceJson
            .optString("ProductModel", "")
            .ifBlank {
                null
            }

        val hardwareRevision = deviceJson
            .optString("HardwareRevision", "")
            .ifBlank {
                null
            }

        val firmwareVersion = deviceJson
            .optString("FirmwareVersion", "")
            .ifBlank {
                null
            }

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
            return null
        }

        return DiscoveredAquaDevice(
            id = finalId,
            ip = ip,

            aquaName = aquaName,
            name = name,

            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,

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

    private fun isSelfRefreshPacket(
        root: JSONObject
    ): Boolean {
        return root.optString("Command", "") == "RefreshUDP" &&
            !root.has("Data") &&
            !root.has("NetUdp")
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
            ?.let {
                return it
            }

        root.optJSONObject("NetUdp")
            ?.optJSONObject("Data")
            ?.optJSONObject("0")
            ?.let {
                return it
            }

        return null
    }

    private fun getBroadcastTargets(
        context: Context
    ): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()

        getConnectivityBroadcastAddresses(context).forEach { address ->
            targets.add(address)
        }

        runCatching {
            targets.add(getDhcpBroadcastAddress(context))
        }

        runCatching {
            targets.add(InetAddress.getByName("255.255.255.255"))
        }

        return targets.toList()
    }

    private fun getConnectivityBroadcastAddresses(
        context: Context
    ): List<InetAddress> {
        val connectivityManager = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        return connectivityManager.allNetworks
            .filter { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .flatMap { network ->
                val linkProperties = connectivityManager.getLinkProperties(network)
                    ?: return@flatMap emptyList()

                linkProperties.linkAddresses
                    .mapNotNull { linkAddress ->
                        linkAddress.toBroadcastAddress()
                    }
            }
    }

    private fun LinkAddress.toBroadcastAddress(): InetAddress? {
        val address = address

        if (address !is Inet4Address) {
            return null
        }

        val prefix = prefixLength

        if (prefix !in 1..32) {
            return null
        }

        val addressBytes = address.address
        val ip = bytesToInt(addressBytes)
        val mask = if (prefix == 32) {
            -1
        } else {
            -1 shl (32 - prefix)
        }

        val broadcast = ip or mask.inv()

        return InetAddress.getByAddress(
            byteArrayOf(
                ((broadcast shr 24) and 0xFF).toByte(),
                ((broadcast shr 16) and 0xFF).toByte(),
                ((broadcast shr 8) and 0xFF).toByte(),
                (broadcast and 0xFF).toByte()
            )
        )
    }

    private fun bytesToInt(
        bytes: ByteArray
    ): Int {
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun getDhcpBroadcastAddress(
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
        return createStableIdFromIdentity(
            identity = "legacy-ip:$ip"
        )
    }

    private fun createStableIdFromIdentity(
        identity: String
    ): Long {
        val normalized = identity.trim().lowercase(Locale.US)

        if (normalized.isBlank()) {
            return 0L
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(StandardCharsets.UTF_8))

        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or (digest[index].toLong() and 0xFFL)
        }

        return (value and Long.MAX_VALUE).takeIf { stableId ->
            stableId > 0L
        } ?: 0L
    }

    private fun JSONObject.optNullableLong(
        key: String
    ): Long? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return try {
            when (val value = get(key)) {
                is Number -> value.toLong()
                is String -> value.trim().toLongOrNull()
                else -> null
            }
        } catch (exception: Exception) {
            null
        }
    }

    private fun JSONObject.firstNonBlankString(
        vararg keys: String
    ): String? {
        keys.forEach { key ->
            val value = optString(key, "")
                .trim()

            if (value.isNotBlank()) {
                return value
            }
        }

        return null
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
                        .map { item ->
                            item.trim()
                        }
                        .filter { item ->
                            item.isNotBlank()
                        }
                        .toSet()
                }

                else -> emptySet()
            }
        } catch (exception: Exception) {
            emptySet()
        }
    }
}