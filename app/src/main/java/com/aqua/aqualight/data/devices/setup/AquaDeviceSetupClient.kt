package com.aqua.aqualight.data.devices.setup

import android.net.Network
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.discovery.model.DiscoveredAquaDevice
import com.aqua.aqualight.data.network.LocalNetworkAddressPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.CRC32

class AquaDeviceSetupClient {

    init {
        LocalNetworkAddressPolicy.requireLocalCleartextHost(SETUP_DEVICE_IP)
    }

    data class SetupResult(
        val success: Boolean,
        val responseCode: Int?,
        val responseBody: String?,
        val errorMessage: String?
    )

    data class HomeWifiNetwork(
        val ssid: String,
        val rssi: Int,
        val bssid: String = "",
        val channel: Int = 0,
        val security: String = ""
    )

    data class DeviceWifiStatus(
        val connected: Boolean,
        val clientIp: String,
        val apEnabled: Boolean = false,
        val configuredApEnabled: Boolean? = null,
        val connectionState: String = "",
        val lastDisconnectReason: String = "",
        val lastDisconnectReasonCode: Int = 0,
        val nextRetryRemainingMs: Long = 0L
    ) {
        val setupAccessPointClosed: Boolean
            get() = !apEnabled && configuredApEnabled != true

        val diagnosticText: String
            get() = buildList {
                if (connectionState.isNotBlank()) add("state=$connectionState")
                if (lastDisconnectReason.isNotBlank() && lastDisconnectReason != "none") {
                    add("reason=$lastDisconnectReason")
                }
                if (lastDisconnectReasonCode != 0) add("code=$lastDisconnectReasonCode")
                if (nextRetryRemainingMs > 0) add("retryInMs=$nextRetryRemainingMs")
            }.joinToString(", ")
    }

    data class DeviceApiToken(
        val token: String,
        val deviceUid: String,
        val shortId: String,
        val serialNumber: String,
        val tokenVersion: Int? = null
    ) {
        val isTokenGateEnabled: Boolean
            get() = token.isNotBlank()
    }

    private data class SecurityStatus(
        val tokenGateEnabled: Boolean,
        val dynamicPairingEnabled: Boolean,
        val paired: Boolean,
        val deviceUid: String,
        val shortId: String,
        val serialNumber: String,
        val tokenVersion: Int?
    )

    class DeviceSecurityException(
        message: String
    ) : Exception(message)

    suspend fun pairForSetup(
        network: Network,
        existingToken: String? = null
    ): DeviceApiToken = withContext(Dispatchers.IO) {
        val token = existingToken.orEmpty().trim()
        val status = readSecurityStatus(
            network = network,
            deviceApiToken = token
        )

        if (!status.tokenGateEnabled) {
            return@withContext DeviceApiToken(
                token = "",
                deviceUid = status.deviceUid,
                shortId = status.shortId,
                serialNumber = status.serialNumber,
                tokenVersion = status.tokenVersion
            )
        }

        if (status.paired && token.isBlank()) {
            throw DeviceSecurityException(
                "Cihaz daha önce eşleştirilmiş. Güvenli firmware token istediği için uygulama bu cihazı mevcut token olmadan yeniden eşleyemez. Cihazı fabrika ayarlarına alın veya mevcut token ile tekrar deneyin."
            )
        }

        val identity = JSONObject().apply {
            if (status.deviceUid.isNotBlank()) {
                put("deviceUid", status.deviceUid)
            }
            if (status.shortId.isNotBlank()) {
                put("shortId", status.shortId)
            }
            if (status.serialNumber.isNotBlank()) {
                put("serialNumber", status.serialNumber)
            }
        }

        val body = JSONObject().put(
            "data",
            identity
        )

        val result = performJsonRequest(
            network = network,
            method = "POST",
            path = "/api/v1/security/pair",
            body = body,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 12_000,
            deviceApiToken = token
        )

        if (!result.success) {
            throw DeviceSecurityException(
                result.errorMessage ?: "Cihaz eşleştirme isteğini kabul etmedi."
            )
        }

        val data = result.responseBody
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?.optJSONObject("data")
            ?: throw DeviceSecurityException("Cihaz eşleştirme yanıtı okunamadı.")

        val returnedToken = data.optString("token", "").trim()
        val paired = data.optBoolean("paired", false)
        val alreadyPaired = data.optBoolean("alreadyPaired", false)
        val resolvedToken = returnedToken.ifBlank {
            token.takeIf { existing -> existing.isNotBlank() && (paired || alreadyPaired) }.orEmpty()
        }

        if (resolvedToken.isBlank()) {
            throw DeviceSecurityException("Cihaz eşleşti ancak API token döndürmedi.")
        }

        DeviceApiToken(
            token = resolvedToken,
            deviceUid = data.optString("deviceUid", status.deviceUid).trim(),
            shortId = data.optString("shortId", status.shortId).trim(),
            serialNumber = data.optString("serialNumber", status.serialNumber).trim(),
            tokenVersion = data.optIntOrNull("tokenVersion") ?: status.tokenVersion
        )
    }

    suspend fun scanHomeWifiNetworks(
        network: Network,
        deviceApiToken: String = ""
    ): List<HomeWifiNetwork> = withContext(Dispatchers.IO) {
        val response = performJsonRequest(
            network = network,
            method = "POST",
            path = "/api/v1/network/scan",
            body = JSONObject().put(
                "data",
                JSONObject().put("maxResults", 30)
            ),
            connectTimeoutMs = 12_000,
            readTimeoutMs = 20_000,
            deviceApiToken = deviceApiToken
        ).responseBody

        parseWifiScanResponse(response.orEmpty())
    }

    suspend fun readDeviceWifiStatus(
        network: Network,
        deviceApiToken: String = ""
    ): DeviceWifiStatus = withContext(Dispatchers.IO) {
        val result = performJsonRequest(
            network = network,
            method = "GET",
            path = "/api/v1/network/status",
            body = null,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 8_000,
            deviceApiToken = deviceApiToken
        )

        parseDeviceWifiStatus(result.responseBody)
    }

    suspend fun readLanDeviceWifiStatus(
        host: String,
        deviceApiToken: String
    ): DeviceWifiStatus = withContext(Dispatchers.IO) {
        val safeHost = host.trim()
        LocalNetworkAddressPolicy.requireLocalCleartextHost(safeHost)

        val result = performJsonRequest(
            network = null,
            baseUrl = "http://$safeHost:80",
            method = "GET",
            path = "/api/v1/network/status",
            body = null,
            connectTimeoutMs = 6_000,
            readTimeoutMs = 8_000,
            deviceApiToken = deviceApiToken
        )

        if (!result.success) {
            return@withContext DeviceWifiStatus(connected = false, clientIp = "")
        }

        parseDeviceWifiStatus(result.responseBody)
    }

    suspend fun readLanDeviceIdentity(
        host: String,
        deviceApiToken: String = ""
    ): DiscoveredAquaDevice? = withContext(Dispatchers.IO) {
        val safeHost = host.trim()
        if (!isValidHomeNetworkIp(safeHost)) {
            return@withContext null
        }

        LocalNetworkAddressPolicy.requireLocalCleartextHost(safeHost)

        val result = performJsonRequest(
            network = null,
            baseUrl = "http://$safeHost:80",
            method = "GET",
            path = "/api/v1/device/identity",
            body = null,
            connectTimeoutMs = 6_000,
            readTimeoutMs = 8_000,
            deviceApiToken = deviceApiToken
        )

        if (!result.success) {
            return@withContext null
        }

        parseLanDeviceIdentity(
            responseText = result.responseBody,
            host = safeHost
        )
    }

    fun parseDeviceWifiStatus(responseText: String?): DeviceWifiStatus {
        val data = responseText
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?.optJSONObject("data")
            ?: return DeviceWifiStatus(connected = false, clientIp = "")

        val clientIp = data.optString("ipAddress", "").trim()
            .ifBlank { data.optString("currentIpAddress", "").trim() }
        val connected = data.optBoolean("connected", false)
        val apEnabled = data.optBoolean("apEnabled", false)
        val configuredApEnabled = if (data.has("configuredApEnabled") && !data.isNull("configuredApEnabled")) {
            data.optBoolean("configuredApEnabled", false)
        } else {
            null
        }

        return DeviceWifiStatus(
            connected = connected && isValidHomeNetworkIp(clientIp),
            clientIp = clientIp,
            apEnabled = apEnabled,
            configuredApEnabled = configuredApEnabled,
            connectionState = data.optString("connectionState", "").trim(),
            lastDisconnectReason = data.optString("lastDisconnectReason", "").trim(),
            lastDisconnectReasonCode = data.optInt("lastDisconnectReasonCode", 0),
            nextRetryRemainingMs = data.optLong("nextRetryRemainingMs", 0L)
        )
    }

    suspend fun closeSetupAccessPoint(
        network: Network,
        deviceApiToken: String = ""
    ): SetupResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("setupApEnabled", false)
                .put("applyNow", true)
        )

        performJsonRequest(
            network = network,
            method = "PUT",
            path = "/api/v1/network/wifi",
            body = body,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 15_000,
            acceptNetworkTransition = true,
            deviceApiToken = deviceApiToken
        )
    }

    suspend fun sendHomeWifiCredentials(
        network: Network,
        setupSsid: String,
        setupPassword: String,
        homeSsid: String,
        homePassword: String,
        homeBssid: String = "",
        homeChannel: Int = 0,
        disableSetupAccessPoint: Boolean,
        deviceApiToken: String = ""
    ): SetupResult = withContext(Dispatchers.IO) {
        val wifiData = JSONObject()
            .put("clientEnabled", true)
            .put("clientSsid", homeSsid)
            .put("clientPassword", homePassword)
            .put("setupApEnabled", !disableSetupAccessPoint)
            .put("setupApPassword", setupPassword)
            .put("applyNow", true)

        if (homeBssid.isNotBlank()) {
            wifiData.put("clientBssid", homeBssid)
        }

        if (homeChannel in 1..14) {
            wifiData.put("clientChannel", homeChannel)
        }

        val body = JSONObject().put("data", wifiData)

        performJsonRequest(
            network = network,
            method = "PUT",
            path = "/api/v1/network/wifi",
            body = body,
            connectTimeoutMs = 10_000,
            readTimeoutMs = if (disableSetupAccessPoint) 15_000 else 75_000,
            acceptNetworkTransition = true,
            deviceApiToken = deviceApiToken
        )
    }

    private fun readSecurityStatus(
        network: Network,
        deviceApiToken: String = ""
    ): SecurityStatus {
        val result = performJsonRequest(
            network = network,
            method = "GET",
            path = "/api/v1/security/status",
            body = null,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 8_000,
            deviceApiToken = deviceApiToken
        )

        if (!result.success) {
            throw DeviceSecurityException(
                result.errorMessage ?: "Cihaz güvenlik durumu okunamadı."
            )
        }

        val data = result.responseBody
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?.optJSONObject("data")
            ?: throw DeviceSecurityException("Cihaz güvenlik yanıtı okunamadı.")

        return SecurityStatus(
            tokenGateEnabled = data.optBoolean("tokenGateEnabled", false),
            dynamicPairingEnabled = data.optBoolean("dynamicPairingEnabled", false),
            paired = data.optBoolean("paired", false),
            deviceUid = data.optString("deviceUid", "").trim(),
            shortId = data.optString("shortId", "").trim(),
            serialNumber = data.optString("serialNumber", "").trim(),
            tokenVersion = data.optIntOrNull("tokenVersion")
        )
    }

    private fun performJsonRequest(
        network: Network?,
        baseUrl: String = BASE_URL,
        method: String,
        path: String,
        body: JSONObject?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        acceptNetworkTransition: Boolean = false,
        deviceApiToken: String = ""
    ): SetupResult {
        var connection: HttpURLConnection? = null
        var bodySent = false

        return try {
            val url = URL("$baseUrl$path")
            connection = if (network != null) {
                network.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doInput = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")
            deviceApiToken.trim().takeIf { token -> token.isNotBlank() }?.let { token ->
                connection.setRequestProperty("X-AquaLight-Device-Token", token)
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }
                bodySent = true
            }

            val responseCode = connection.responseCode
            val responseBody = readResponseBody(connection)
            val ok = responseCode in 200..299 && isOkEnvelope(responseBody)

            SetupResult(
                success = ok,
                responseCode = responseCode,
                responseBody = responseBody,
                errorMessage = if (ok) null else parseErrorMessage(responseBody) ?: "HTTP $responseCode"
            )
        } catch (exception: Exception) {
            val acceptedByDevice = acceptNetworkTransition && bodySent && isExpectedNetworkTransitionException(exception)
            if (acceptedByDevice) {
                SetupResult(success = true, responseCode = null, responseBody = null, errorMessage = null)
            } else {
                SetupResult(
                    success = false,
                    responseCode = null,
                    responseBody = null,
                    errorMessage = exception.message ?: exception.toString()
                )
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResponseBody(connection: HttpURLConnection): String? {
        return runCatching {
            connection.inputStream.bufferedReader().use { reader -> reader.readText() }
        }.getOrElse {
            connection.errorStream?.bufferedReader()?.use { reader -> reader.readText() }
        }
    }

    private fun isOkEnvelope(responseBody: String?): Boolean {
        if (responseBody.isNullOrBlank()) return true
        val root = runCatching { JSONObject(responseBody) }.getOrNull() ?: return false
        return root.optBoolean("ok", false)
    }

    private fun parseErrorMessage(responseBody: String?): String? {
        val root = responseBody
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return null
        return root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }

    private fun parseLanDeviceIdentity(
        responseText: String?,
        host: String
    ): DiscoveredAquaDevice? {
        val data = responseText
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?.optJSONObject("data")
            ?: return null

        val productId = data.optString("productId", "").trim()
        val definition = AquaDeviceCatalog.findByProductId(productId) ?: return null
        val protocolVersion = data.optIntOrNull("protocolVersion")

        if (!definition.isProtocolVersionSupported(protocolVersion)) {
            return null
        }

        val deviceUid = data.optString("deviceUid", "").trim().ifBlank {
            data.optString("serialNumber", "").trim()
        }
        if (deviceUid.isBlank()) {
            return null
        }

        val shortId = data.optString("shortId", "").trim().ifBlank {
            deviceUid.substringAfterLast('-', "")
        }
        val serialNumber = data.optString("serialNumber", "").trim().ifBlank { deviceUid }
        val firmwareSerial = data.optString("firmwareSerial", "").trim().ifBlank { serialNumber }
        val displayName = data.optString("displayName", "").trim()
            .ifBlank { data.optString("productDisplayName", "").trim() }
            .ifBlank { definition.displayName }
        val customName = data.optString("customName", "").trim()
        val resolvedDisplayName = customName.ifBlank { displayName }

        return DiscoveredAquaDevice(
            id = stablePositiveId(deviceUid),
            ip = host,
            productId = definition.productId,
            productKey = definition.productKey,
            category = definition.category,
            setupCode = definition.setupCode,
            productFamily = data.optString("productFamily", "").trim().ifBlank { definition.productFamily },
            productLine = data.optString("productLine", "").trim().ifBlank { definition.productLine },
            productModel = data.optString("productModel", "").trim().ifBlank { definition.productModel },
            displayName = resolvedDisplayName,
            skuId = data.optString("skuId", "").trim()
                .ifBlank { definition.variants.firstOrNull()?.skuId.orEmpty() }
                .ifBlank { null },
            skuCode = data.optString("skuCode", "").trim()
                .ifBlank { definition.variants.firstOrNull()?.skuCode.orEmpty() }
                .ifBlank { null },
            deviceUid = deviceUid,
            macAddress = data.optString("macAddress", "").trim().ifBlank { null },
            serialNumber = serialNumber,
            shortId = shortId,
            firmwareSerial = firmwareSerial,
            hardwareRevision = data.optString("hardwareRevision", "").trim().ifBlank { null },
            firmwareVersion = data.optString("firmwareVersion", "").trim().ifBlank { null },
            protocolVersion = protocolVersion,
            firmwareBuild = data.optString("firmwareBuild", "").trim(),
            udpVersion = null,
            tabLight = false,
            tabTimer = false,
            tabTemperature = false,
            supportedFeatures = definition.features.map { feature -> feature.name }.toSet(),
            supportedScreens = definition.screens.map { screen -> screen.name }.toSet(),
            aquaName = data.optString("brand", "").trim().ifBlank { definition.productFamily },
            name = displayName
        )
    }

    private fun parseWifiScanResponse(responseText: String): List<HomeWifiNetwork> {
        val data = runCatching { JSONObject(responseText) }.getOrNull()
            ?.optJSONObject("data")
            ?: return emptyList()

        val networks = data.optJSONArray("networks") ?: JSONArray()
        return buildList {
            for (i in 0 until networks.length()) {
                val item = networks.optJSONObject(i) ?: continue
                val ssid = item.optString("ssid", "").trim()
                if (ssid.isBlank()) continue
                add(
                    HomeWifiNetwork(
                        ssid = ssid,
                        rssi = item.optInt("rssi", -100),
                        bssid = item.optString("bssid", "").trim(),
                        channel = item.optInt("channel", 0),
                        security = item.optString("security", "").trim()
                    )
                )
            }
        }
            .distinctBy { network -> "${network.ssid}|${network.bssid.ifBlank { network.channel.toString() }}" }
            .sortedWith(compareByDescending<HomeWifiNetwork> { network -> network.rssi }.thenBy { network -> network.ssid })
    }

    private fun isValidHomeNetworkIp(ip: String): Boolean {
        return ip.isNotBlank() &&
            ip != "0.0.0.0" &&
            ip != "192.168.4.1" &&
            !ip.startsWith("192.168.4.")
    }

    private fun isExpectedNetworkTransitionException(exception: Exception): Boolean {
        val message = exception.message.orEmpty()
        return exception is SocketTimeoutException ||
            exception is SocketException ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("closed", ignoreCase = true) ||
            message.contains("unreachable", ignoreCase = true) ||
            message.contains("failed to connect", ignoreCase = true)
    }

    private fun stablePositiveId(value: String): Long {
        val crc = CRC32()
        crc.update(value.toByteArray(Charsets.UTF_8))
        return crc.value.toLong().and(0x7FFFFFFF).coerceAtLeast(1L)
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) {
            optInt(name)
        } else {
            null
        }
    }

    private companion object {
        const val SETUP_DEVICE_IP = "192.168.4.1"
        const val BASE_URL = "http://$SETUP_DEVICE_IP"
    }
}
