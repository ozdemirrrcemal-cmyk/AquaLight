package com.aqua.aqualight.data.devices.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
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
import java.util.Locale
import kotlin.coroutines.coroutineContext

object UdpDeviceDiscovery {

    private const val TAG_DISCOVERY = "UDP_DISCOVERY"
    private const val TAG_RECEIVED = "UDP_RECEIVED"

    private const val VER_UDP = 20240813
    private const val UDP_PORT = 10888
    private const val SOCKET_TIMEOUT_MS = 300
    private const val BUFFER_SIZE = 4096

    /**
     * GEÇİCİ TEST UYUMLULUĞU
     *
     * true:
     * Eski firmware ProductId / ProtocolVersion vermese bile
     * AquaName + Name + TabLight/TabTimer/TabTemperature üzerinden
     * cihaz katalog eşleştirmesi yapılır.
     *
     * FIRMWARE_READY:
     * Firmware UDP tarafı şu alanları verdiğinde bunu false yap:
     *
     * ProductId
     * ProtocolVersion veya ApiVersion
     * DeviceUid veya ShortId veya MacAddress veya FirmwareSerial veya SerialNumber veya ID
     *
     * Sonra:
     * 1. LEGACY_DISCOVERY_START / LEGACY_DISCOVERY_END arası fallback kodları silinebilir.
     * 2. resolveLegacyDefinition(...) fonksiyonu silinebilir.
     * 3. AquaDeviceDefinition ve AquaProductKey importları başka yerde kullanılmıyorsa silinebilir.
     */
    private const val ALLOW_LEGACY_DISCOVERY = true

    suspend fun discover(
        context: Context,
        timeoutMs: Long = 3_000L,
        stopWhen: ((DiscoveredAquaDevice) -> Boolean)? = null,
        shouldStopEarly: (() -> Boolean)? = null
    ): List<DiscoveredAquaDevice> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val resultMap = linkedMapOf<String, DiscoveredAquaDevice>()
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
                SystemClock.elapsedRealtime() - startTime < timeoutMs &&
                shouldStopEarly?.invoke() != true
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

                    resultMap[discoveredDevice.identityKey()] = discoveredDevice

                    if (stopWhen?.invoke(discoveredDevice) == true) {
                        break
                    }
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
        } catch (_: Exception) {
            return null
        }

        if (isSelfRefreshPacket(root)) {
            return null
        }

        val deviceJson = extractDeviceJson(root) ?: return null

        /**
         * TICARI DISCOVERY CONTRACT
         *
         * Firmware düzeldiğinde UDP cihaz bilgisinde şu alanlar zorunlu olmalı:
         *
         * ProductId:
         *   com.aqua.light.wrgb_pro_elite
         *
         * ProtocolVersion veya ApiVersion:
         *   1
         *
         * Stable identity alanlarından en az biri:
         *   DeviceUid / ShortId / MacAddress / FirmwareSerial / SerialNumber / ID / ESPChipID
         *
         * ProductId cihazın modelini çözer.
         * ProtocolVersion uygulamanın bu protokolü destekleyip desteklemediğini kontrol eder.
         * Stable identity ise aynı fiziksel cihazı IP değişse bile takip etmeyi sağlar.
         */
        val productId = deviceJson.firstNonBlankString(
            "ProductId",
            "productId",
            "product_id"
        )

        val definitionFromProductId = productId?.let { value ->
            AquaDeviceCatalog.findByProductId(
                productId = value
            )
        }

        /**
         * Firmware ProductId gönderiyor ama uygulama katalogunda yoksa bu cihaz desteklenmez.
         *
         * Bu kontrol ticari seviye için doğru.
         * Çünkü yanlış ProductId gönderen cihazı AquaName/Name ile tahmin edip kabul etmek istemeyiz.
         */
        if (
            !productId.isNullOrBlank() &&
            definitionFromProductId == null
        ) {
            Log.w(
                TAG_RECEIVED,
                "Unsupported ProductId=$productId from $sourceIp"
            )
            return null
        }

        /**
         * LEGACY_DISCOVERY_START
         *
         * Eski firmware ProductId vermediği için geçici olarak
         * AquaName / Name / TabLight / TabTimer / TabTemperature alanlarını okuyoruz.
         *
         * FIRMWARE_READY:
         * Firmware ProductId gönderdiğinde bu legacy alanlar cihaz modelini çözmek için kullanılmayacak.
         */
        val legacyAquaName = deviceJson.firstNonBlankString(
            "AquaName"
        )

        val legacyName = deviceJson.firstNonBlankString(
            "Name"
        )

        val legacyTabLight = deviceJson.optFlexibleBoolean(
            key = "TabLight"
        )

        val legacyTabTimer = deviceJson.optFlexibleBoolean(
            key = "TabTimer"
        )

        val legacyTabTemperature = deviceJson.optFlexibleBoolean(
            key = "TabTemperature"
        )
        /**
         * LEGACY_DISCOVERY_END
         */

        val definition = definitionFromProductId
            ?: run {
                // LEGACY_DISCOVERY_START
                // Geçici eski firmware desteği.
                //
                // Eski firmware ProductId vermediği için AquaName/Name/Tab alanlarından
                // katalog eşleştiriyoruz.
                //
                // FIRMWARE_READY:
                // Bu blok silinebilir. ProductId yoksa cihaz direkt reddedilmelidir.

                if (!ALLOW_LEGACY_DISCOVERY) {
                    return null
                }

                resolveLegacyDefinition(
                    aquaName = legacyAquaName,
                    name = legacyName,
                    tabLight = legacyTabLight,
                    tabTimer = legacyTabTimer,
                    tabTemperature = legacyTabTemperature
                )
                // LEGACY_DISCOVERY_END
            }
            ?: return null

        val isLegacyDiscovery = definitionFromProductId == null

        val protocolVersion = deviceJson.optNullableInt("ProtocolVersion")
            ?: deviceJson.optNullableInt("ApiVersion")
            ?: if (
                ALLOW_LEGACY_DISCOVERY &&
                isLegacyDiscovery
            ) {
                // LEGACY_DISCOVERY_START
                // Eski firmware ProtocolVersion vermiyor.
                //
                // Test için katalogdaki v1 protokolü varsayıyoruz.
                //
                // FIRMWARE_READY:
                // Bu fallback silinecek. ProtocolVersion veya ApiVersion zorunlu olacak.
                1
                // LEGACY_DISCOVERY_END
            } else {
                null
            }

        if (!definition.isProtocolVersionSupported(protocolVersion)) {
            Log.w(
                TAG_RECEIVED,
                "Unsupported protocolVersion=$protocolVersion productId=${definition.productId} from $sourceIp"
            )
            return null
        }

        val idRaw = deviceJson.optLong(
            "ID",
            0L
        )

        val espChipId = deviceJson.optLong(
            "ESPChipID",
            0L
        )

        val ip = deviceJson
            .optString("IP", sourceIp)
            .ifBlank {
                sourceIp
            }

        if (ip.isBlank()) {
            return null
        }

        val rawDeviceUid = deviceJson.firstNonBlankString(
            "DeviceUid",
            "DeviceUID",
            "UID",
            "deviceUid",
            "device_uid"
        )

        val macAddress = deviceJson.firstNonBlankString(
            "MacAddress",
            "MAC",
            "macAddress",
            "mac_address"
        )

        val deviceUid = rawDeviceUid
            ?: buildDeviceUidFromMac(
                macAddress = macAddress
            )

        val serialNumber = deviceJson.firstNonBlankString(
            "SerialNumber",
            "Serial",
            "serialNumber",
            "serial_number"
        )

        val firmwareSerial = deviceJson.firstNonBlankString(
            "FirmwareSerial",
            "firmwareSerial"
        ) ?: serialNumber

        val numericIdentity = idRaw.takeIf { value -> value > 0L }
            ?: espChipId.takeIf { value -> value > 0L }

        val shortId = deviceJson.firstNonBlankString(
            "ShortId",
            "ShortID",
            "DeviceCode",
            "shortId",
            "short_id"
        ) ?: deriveShortId(
            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            firmwareSerial = firmwareSerial,
            fallbackNumericId = numericIdentity
        )

        if (
            !hasStableDiscoveryIdentity(
                deviceUid = deviceUid,
                macAddress = macAddress,
                serialNumber = serialNumber,
                firmwareSerial = firmwareSerial,
                shortId = shortId,
                numericId = numericIdentity
            )
        ) {
            Log.w(
                TAG_RECEIVED,
                "Missing stable identity for productId=${definition.productId} from $sourceIp"
            )
            return null
        }

        val finalId = when {
            idRaw > 0L -> {
                idRaw
            }

            espChipId > 0L -> {
                espChipId
            }

            !deviceUid.isNullOrBlank() -> {
                createStableIdFromString(deviceUid)
            }

            !serialNumber.isNullOrBlank() -> {
                createStableIdFromString(serialNumber)
            }

            !firmwareSerial.isNullOrBlank() -> {
                createStableIdFromString(firmwareSerial)
            }

            !macAddress.isNullOrBlank() -> {
                createStableIdFromString(macAddress)
            }

            !shortId.isNullOrBlank() -> {
                createStableIdFromString(shortId)
            }

            else -> {
                return null
            }
        }

        if (finalId <= 0L) {
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

        val productFamily = deviceJson.firstNonBlankString(
            "ProductFamily",
            "AquaName"
        ) ?: definition.productFamily

        val productLine = deviceJson.firstNonBlankString(
            "ProductLine"
        ) ?: definition.productLine

        val productModel = deviceJson.firstNonBlankString(
            "ProductModel",
            "Name"
        ) ?: definition.productModel

        val displayName = deviceJson.firstNonBlankString(
            "DisplayName",
            "UserName",
            "CustomName"
        ) ?: definition.displayName

        val defaultVariant = definition.variants.firstOrNull()

        val skuId = deviceJson.firstNonBlankString(
            "SkuId",
            "SKUId",
            "skuId",
            "sku_id"
        ) ?: defaultVariant?.skuId

        val skuCode = deviceJson.firstNonBlankString(
            "SkuCode",
            "SKU",
            "skuCode",
            "sku_code"
        ) ?: defaultVariant?.skuCode

        val hardwareRevision = deviceJson.firstNonBlankString(
            "HardwareRevision"
        )

        val firmwareVersion = deviceJson.firstNonBlankString(
            "FirmwareVersion"
        )

        val supportedFeatures = deviceJson.readStringSet(
            key = "SupportedFeatures"
        )

        val supportedScreens = deviceJson.readStringSet(
            key = "SupportedScreens"
        )

        val channelCount = deviceJson.optNullableInt(
            key = "ChannelCount"
        )

        val sensorCount = deviceJson.optNullableInt(
            key = "SensorCount"
        )

        return DiscoveredAquaDevice(
            id = finalId,
            ip = ip,

            productId = definition.productId,
            productKey = definition.productKey,
            category = definition.category,
            setupCode = definition.setupCode,

            productFamily = productFamily,
            productLine = productLine,
            productModel = productModel,
            displayName = displayName,
            skuId = skuId,
            skuCode = skuCode,

            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            shortId = shortId,
            firmwareSerial = firmwareSerial,

            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            protocolVersion = protocolVersion,

            firmwareBuild = firmwareBuild,
            udpVersion = udpVersion,

            tabLight = legacyTabLight,
            tabTimer = legacyTabTimer,
            tabTemperature = legacyTabTemperature,

            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens,

            channelCount = channelCount,
            sensorCount = sensorCount,

            aquaName = productFamily,
            name = productModel
        )
    }

    /**
     * LEGACY_DISCOVERY_START
     *
     * Geçici eski firmware destek fonksiyonu.
     *
     * Eski firmware UDP'de ProductId göndermediği için:
     * AquaName + Name + TabLight + TabTimer + TabTemperature
     * alanlarından en yakın katalog ürününü bulur.
     *
     * WRGB test cihazı için:
     *
     * AquaName = AquaLight
     * Name = WRGB Pro Elite
     * TabLight = 1
     *
     * FIRMWARE_READY:
     * Firmware UDP tarafı şu alanları verdiğinde bu fonksiyonu tamamen sil:
     *
     * ProductId = com.aqua.light.wrgb_pro_elite
     * ProtocolVersion = 1
     * DeviceUid veya ShortId veya MacAddress veya FirmwareSerial
     *
     * Bu fonksiyon silinince şu importlar da başka yerde kullanılmıyorsa silinir:
     *
     * import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition
     * import com.aqua.aqualight.data.devices.catalog.AquaProductKey
     */
    private fun resolveLegacyDefinition(
        aquaName: String?,
        name: String?,
        tabLight: Boolean,
        tabTimer: Boolean,
        tabTemperature: Boolean
    ): AquaDeviceDefinition? {
        val identity = "${aquaName.orEmpty()} ${name.orEmpty()}"
            .lowercase(Locale.US)

        val productKey = when {
            identity.contains("dose") ||
                identity.contains("dosing") -> {
                AquaProductKey.DOSING_DOSE_PRO_4
            }

            identity.contains("cool") ||
                identity.contains("cooling") -> {
                AquaProductKey.COOLING_COOL_PRO
            }

            identity.contains("multi") -> {
                AquaProductKey.TIMER_MULTI_CONTROL
            }

            identity.contains("timer") -> {
                AquaProductKey.TIMER_TIMER_PRO
            }

            identity.contains("wrgb") ||
                identity.contains("light") ||
                identity.contains("aqualight") -> {
                AquaProductKey.LIGHT_WRGB_PRO_ELITE
            }

            tabLight -> {
                AquaProductKey.LIGHT_WRGB_PRO_ELITE
            }

            tabTimer -> {
                AquaProductKey.TIMER_TIMER_PRO
            }

            tabTemperature -> {
                AquaProductKey.COOLING_COOL_PRO
            }

            else -> {
                null
            }
        }

        return productKey?.let { value ->
            AquaDeviceCatalog.findByProductKey(
                productKey = value
            )
        }
    }
    // LEGACY_DISCOVERY_END

    private fun isSelfRefreshPacket(
        root: JSONObject
    ): Boolean {
        return root.optString("Command", "") == "RefreshUDP" &&
            !root.has("Data") &&
            !root.has("NetUdp")
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

    private fun hasStableDiscoveryIdentity(
        deviceUid: String?,
        macAddress: String?,
        serialNumber: String?,
        firmwareSerial: String?,
        shortId: String?,
        numericId: Long?
    ): Boolean {
        return !deviceUid.isNullOrBlank() ||
            !macAddress.isNullOrBlank() ||
            !serialNumber.isNullOrBlank() ||
            !firmwareSerial.isNullOrBlank() ||
            !shortId.isNullOrBlank() ||
            numericId != null
    }

    private fun buildDeviceUidFromMac(
        macAddress: String?
    ): String? {
        val normalizedMac = normalizeHardwareToken(
            value = macAddress
        )

        if (normalizedMac.length < 12) {
            return null
        }

        return "AQL-ESP32-${normalizedMac.takeLast(12)}"
    }

    private fun normalizeHardwareToken(
        value: String?
    ): String {
        return value
            ?.filter { char ->
                char.isLetterOrDigit()
            }
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    private fun createStableIdFromString(
        value: String
    ): Long {
        return value.trim()
            .lowercase(Locale.US)
            .hashCode()
            .toLong() and 0x00000000FFFFFFFFL
    }

    private fun deriveShortId(
        deviceUid: String?,
        macAddress: String?,
        serialNumber: String?,
        firmwareSerial: String?,
        fallbackNumericId: Long?
    ): String? {
        val source = deviceUid
            ?.ifBlank { null }
            ?: macAddress
                ?.ifBlank { null }
            ?: serialNumber
                ?.ifBlank { null }
            ?: firmwareSerial
                ?.ifBlank { null }
            ?: fallbackNumericId
                ?.takeIf { value -> value > 0L }
                ?.toString()
            ?: return null

        return source
            .filter { char ->
                char.isLetterOrDigit()
            }
            .uppercase(Locale.US)
            .takeLast(6)
            .ifBlank {
                null
            }
    }

    private fun DiscoveredAquaDevice.identityKey(): String {
        return deviceUid
            ?.ifBlank { null }
            ?: macAddress
                ?.ifBlank { null }
            ?: serialNumber
                ?.ifBlank { null }
            ?: firmwareSerial
                ?.ifBlank { null }
            ?: shortId
                ?.ifBlank { null }
            ?: id.toString()
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            emptySet()
        }
    }
}