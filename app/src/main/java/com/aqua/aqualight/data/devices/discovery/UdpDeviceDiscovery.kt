package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import kotlinx.coroutines.Dispatchers
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
import java.util.zip.CRC32
import kotlin.coroutines.coroutineContext

object UdpDeviceDiscovery {

    private const val TAG_DISCOVERY = "UDP_DISCOVERY"
    private const val TAG_RECEIVED = "UDP_RECEIVED"

    private const val DISCOVERY_SCHEMA = "aql.discovery.v1"
    private const val MESSAGE_DEVICE_ANNOUNCE = "device_announce"
    private const val UDP_PORT = 10888
    private const val SOCKET_TIMEOUT_MS = 300
    private const val DISCOVERY_REFRESH_INTERVAL_MS = 600L
    private const val BUFFER_SIZE = 1536

    private data class DiscoveryNetwork(
        val network: Network?,
        val targets: List<InetAddress>,
        val label: String
    )

    suspend fun discover(
        context: Context,
        timeoutMs: Long = 3_000L,
        stopWhen: ((DiscoveredAquaDevice) -> Boolean)? = null,
        shouldStopEarly: (() -> Boolean)? = null
    ): List<DiscoveredAquaDevice> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resultMap = linkedMapOf<String, DiscoveredAquaDevice>()
        val discoveryNetworks = getDiscoveryNetworks(appContext)
        val multicastLock = acquireMulticastLock(appContext)
        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs

        try {
            discoveryNetworks.forEach { discoveryNetwork ->
                coroutineContext.ensureActive()
                if (SystemClock.elapsedRealtime() >= deadlineMs || shouldStopEarly?.invoke() == true) {
                    return@forEach
                }

                val remainingMs = deadlineMs - SystemClock.elapsedRealtime()
                runDiscoverySession(
                    discoveryNetwork = discoveryNetwork,
                    timeoutMs = remainingMs,
                    resultMap = resultMap,
                    stopWhen = stopWhen,
                    shouldStopEarly = shouldStopEarly
                )

                if (resultMap.values.any { device -> stopWhen?.invoke(device) == true }) {
                    return@forEach
                }
            }
        } finally {
            runCatching { multicastLock?.release() }
        }

        resultMap.values.toList()
    }

    private suspend fun runDiscoverySession(
        discoveryNetwork: DiscoveryNetwork,
        timeoutMs: Long,
        resultMap: LinkedHashMap<String, DiscoveredAquaDevice>,
        stopWhen: ((DiscoveredAquaDevice) -> Boolean)?,
        shouldStopEarly: (() -> Boolean)?
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        val targets = discoveryNetwork.targets
        if (targets.isEmpty()) return

        Log.d(
            TAG_DISCOVERY,
            "${discoveryNetwork.label} targets: ${targets.joinToString { it.hostAddress.orEmpty() }}:$UDP_PORT"
        )

        val socket = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = SOCKET_TIMEOUT_MS
                discoveryNetwork.network?.bindSocket(this)
                bind(InetSocketAddress(UDP_PORT))
            }
        } catch (exception: Exception) {
            Log.w(TAG_DISCOVERY, "Unable to open UDP discovery socket for ${discoveryNetwork.label}", exception)
            return
        }

        try {
            coroutineContext.ensureActive()
            var nextRefreshAtMs = 0L
            val startTime = SystemClock.elapsedRealtime()

            while (
                coroutineContext.isActive &&
                SystemClock.elapsedRealtime() - startTime < timeoutMs &&
                shouldStopEarly?.invoke() != true
            ) {
                coroutineContext.ensureActive()

                val now = SystemClock.elapsedRealtime()
                if (now >= nextRefreshAtMs) {
                    sendDiscoveryPacket(socket, targets)
                    nextRefreshAtMs = now + DISCOVERY_REFRESH_INTERVAL_MS
                }

                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val jsonString = String(packet.data, 0, packet.length, StandardCharsets.UTF_8)
                    val sourceIp = packet.address.hostAddress ?: continue

                    Log.d(TAG_RECEIVED, "from $sourceIp: $jsonString")

                    val discoveredDevice = parseDiscoveredDevice(jsonString, sourceIp) ?: continue
                    resultMap[discoveredDevice.identityKey()] = discoveredDevice

                    if (stopWhen?.invoke(discoveredDevice) == true) {
                        break
                    }
                } catch (_: SocketTimeoutException) {
                    // Normal receive window tick.
                } catch (exception: Exception) {
                    Log.e(TAG_RECEIVED, "Packet parse error", exception)
                }
            }
        } finally {
            socket.close()
        }
    }

    private suspend fun sendDiscoveryPacket(socket: DatagramSocket, targets: List<InetAddress>) {
        val requestJson = """{"schema":"$DISCOVERY_SCHEMA","command":"refresh"}"""
        val sendData = requestJson.toByteArray(StandardCharsets.UTF_8)

        targets.forEach { target ->
            coroutineContext.ensureActive()
            runCatching {
                socket.send(DatagramPacket(sendData, sendData.size, target, UDP_PORT))
            }.onFailure { exception ->
                Log.w(TAG_DISCOVERY, "Discovery packet send failed for ${target.hostAddress}", exception)
            }
        }
    }

    private fun parseDiscoveredDevice(jsonString: String, sourceIp: String): DiscoveredAquaDevice? {
        val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null

        if (root.optString("schema") != DISCOVERY_SCHEMA) {
            return null
        }

        if (root.optString("command").equals("refresh", ignoreCase = true)) {
            return null
        }

        if (root.optString("messageType") != MESSAGE_DEVICE_ANNOUNCE) {
            return null
        }

        val deviceJson = root.optJSONObject("device") ?: return null
        val productJson = root.optJSONObject("product") ?: return null
        val firmwareJson = root.optJSONObject("firmware") ?: JSONObject()
        val networkJson = root.optJSONObject("network") ?: JSONObject()
        val capabilitiesJson = root.optJSONObject("capabilities") ?: JSONObject()
        val limitsJson = root.optJSONObject("limits") ?: JSONObject()
        val modulesJson = root.optJSONArray("modules") ?: JSONArray()

        val productId = productJson.firstNonBlankString("productId") ?: return null
        val definition = AquaDeviceCatalog.findByProductId(productId) ?: run {
            Log.w(TAG_RECEIVED, "Unsupported productId=$productId from $sourceIp")
            return null
        }

        val protocolVersion = firmwareJson.optNullableInt("protocolVersion")
        if (!definition.isProtocolVersionSupported(protocolVersion)) {
            Log.w(TAG_RECEIVED, "Unsupported protocolVersion=$protocolVersion productId=$productId from $sourceIp")
            return null
        }

        val networkIp = networkJson.firstNonBlankString("ip")
        val ip = networkIp?.takeIf { isUsableIp(it) } ?: sourceIp.takeIf { isUsableIp(it) } ?: return null

        val deviceUid = deviceJson.firstNonBlankString("uid", "deviceUid") ?: return null
        val shortId = deviceJson.firstNonBlankString("shortId") ?: deviceUid.substringAfterLast('-', "")
        val macAddress = deviceJson.firstNonBlankString("macAddress")
        val serialNumber = deviceJson.firstNonBlankString("serialNumber") ?: deviceUid
        val firmwareSerial = deviceJson.firstNonBlankString("firmwareSerial") ?: serialNumber
        val chipId = deviceJson.optNullableLong("chipId")
            ?: deviceJson.optNullableLong("espChipId")
        val finalId = chipId?.takeIf { it > 0L }
            ?: stablePositiveId(deviceUid)

        val firmwareVersion = firmwareJson.firstNonBlankString("version")
        val firmwareBuild = firmwareJson.firstNonBlankString("build").orEmpty()
        val apiVersion = firmwareJson.optNullableInt("apiVersion")
        val udpVersion = root.optNullableInt("udpVersion")

        val displayName = deviceJson.firstNonBlankString("displayName", "customName")
            ?: productJson.firstNonBlankString("displayName")
            ?: definition.displayName

        val customName = deviceJson.firstNonBlankString("customName")
        val resolvedDisplayName = customName?.ifBlank { null } ?: displayName

        val supportedFeatures = buildFeatureSet(capabilitiesJson, modulesJson)
        val supportedScreens = definition.screens.map { screen -> screen.name }.toSet()

        return DiscoveredAquaDevice(
            id = finalId,
            ip = ip,
            productId = definition.productId,
            productKey = definition.productKey,
            category = definition.category,
            setupCode = productJson.firstNonBlankString("setupCode") ?: deviceJson.firstNonBlankString("setupCode") ?: definition.setupCode,
            productFamily = productJson.firstNonBlankString("family") ?: definition.productFamily,
            productLine = productJson.firstNonBlankString("line") ?: definition.productLine,
            productModel = productJson.firstNonBlankString("model") ?: definition.productModel,
            displayName = resolvedDisplayName,
            skuId = productJson.firstNonBlankString("skuId") ?: definition.variants.firstOrNull()?.skuId,
            skuCode = productJson.firstNonBlankString("skuCode") ?: definition.variants.firstOrNull()?.skuCode,
            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            shortId = shortId,
            firmwareSerial = firmwareSerial,
            hardwareRevision = productJson.firstNonBlankString("hardwareRevision"),
            firmwareVersion = firmwareVersion,
            protocolVersion = protocolVersion,
            firmwareBuild = firmwareBuild,
            udpVersion = udpVersion,
            tabLight = false,
            tabTimer = false,
            tabTemperature = false,
            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens,
            channelCount = firstPositive(
                limitsJson.optNullableInt("lightChannelCount"),
                limitsJson.optNullableInt("timerChannelCount"),
                limitsJson.optNullableInt("dosingChannelCount"),
                limitsJson.optNullableInt("fanChannelCount"),
                limitsJson.optNullableInt("fanOutputCount")
            ),
            sensorCount = limitsJson.optNullableInt("temperatureSensorCount"),
            aquaName = productJson.firstNonBlankString("brand") ?: "AquaLight",
            name = productJson.firstNonBlankString("displayName") ?: resolvedDisplayName
        )
    }

    private fun buildFeatureSet(capabilities: JSONObject, modules: JSONArray): Set<String> {
        val output = linkedSetOf<String>()
        val keys = capabilities.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (capabilities.optBoolean(key, false)) {
                output.add(key)
            }
        }
        for (i in 0 until modules.length()) {
            modules.optString(i).takeIf { it.isNotBlank() }?.let { output.add("module:$it") }
        }
        return output
    }

    private fun firstPositive(vararg values: Int?): Int? = values.firstOrNull { value -> value != null && value > 0 }

    private fun isUsableIp(value: String): Boolean =
        value.isNotBlank() && value != "0.0.0.0"

    private fun stablePositiveId(value: String): Long {
        val crc = CRC32()
        crc.update(value.toByteArray(StandardCharsets.UTF_8))
        return crc.value.toLong().and(0x7FFFFFFF).coerceAtLeast(1L)
    }

    private fun getDiscoveryNetworks(context: Context): List<DiscoveryNetwork> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return@mapNotNull null
            }

            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            val targets = linkProperties.linkAddresses
                .mapNotNull { linkAddress -> linkAddress.toBroadcastAddress() }
                .toMutableList()

            runCatching { targets.add(getDhcpBroadcastAddress(context)) }
            runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }

            DiscoveryNetwork(
                network = network,
                targets = targets.distinctBy { address -> address.hostAddress },
                label = "wifi:${linkProperties.interfaceName ?: "unknown"}"
            )
        }

        if (networks.isNotEmpty()) {
            return networks
        }

        val fallbackTargets = linkedSetOf<InetAddress>()
        runCatching { fallbackTargets.add(getDhcpBroadcastAddress(context)) }
        runCatching { fallbackTargets.add(InetAddress.getByName("255.255.255.255")) }
        return listOf(
            DiscoveryNetwork(
                network = null,
                targets = fallbackTargets.toList(),
                label = "default"
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.createMulticastLock("AquaLightDeviceDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun LinkAddress.toBroadcastAddress(): InetAddress? {
        val inetAddress = address
        if (inetAddress !is Inet4Address) return null
        val prefix = prefixLength
        if (prefix !in 1..32) return null

        val ip = bytesToInt(inetAddress.address)
        val mask = if (prefix == 32) -1 else (-0x1 shl (32 - prefix))
        val broadcast = ip or mask.inv()
        return InetAddress.getByAddress(intToBytes(broadcast))
    }

    @Suppress("DEPRECATION")
    private fun getDhcpBroadcastAddress(context: Context): InetAddress {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcp = wifiManager.dhcpInfo
        val broadcast = dhcp.ipAddress or dhcp.netmask.inv()
        return InetAddress.getByAddress(intToBytesLittleEndian(broadcast))
    }

    private fun bytesToInt(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun intToBytesLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        (value shr 8 and 0xFF).toByte(),
        (value shr 16 and 0xFF).toByte(),
        (value shr 24 and 0xFF).toByte()
    )

    private fun JSONObject.firstNonBlankString(vararg keys: String): String? {
        keys.forEach { key ->
            if (has(key) && !isNull(key)) {
                val value = optString(key, "").trim()
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
            ?: optString(key, "").trim().toIntOrNull()
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getLong(key) }.getOrNull()
            ?: optString(key, "").trim().toLongOrNull()
    }

    private fun DiscoveredAquaDevice.identityKey(): String {
        return deviceUid?.takeIf { it.isNotBlank() }
            ?: serialNumber?.takeIf { it.isNotBlank() }
            ?: firmwareSerial?.takeIf { it.isNotBlank() }
            ?: macAddress?.takeIf { it.isNotBlank() }
            ?: shortId?.takeIf { it.isNotBlank() }
            ?: id.toString()
    }
}
