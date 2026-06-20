package com.aqua.aqualight.data.devices.setup

import android.net.Network
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

    data class PairingResult(
        val success: Boolean,
        val token: String,
        val responseCode: Int?,
        val responseBody: String?,
        val errorMessage: String?
    )

    data class HomeWifiNetwork(
        val ssid: String,
        val rssi: Int
    )

    data class DeviceWifiStatus(
        val connected: Boolean,
        val clientIp: String,
        val configuredClientEnabled: Boolean = false,
        val clientSsid: String = "",
        val clientPasswordSet: Boolean = false,
        val wifiStatusCode: Int? = null,
        val lastClientWifiStatus: Int? = null,
        val lastClientConnectMessage: String = ""
    ) {
        fun hasAcceptedCredentials(expectedSsid: String): Boolean {
            return configuredClientEnabled &&
                clientSsid.equals(expectedSsid.trim(), ignoreCase = false) &&
                clientPasswordSet
        }
    }

    suspend fun scanHomeWifiNetworks(network: Network): List<HomeWifiNetwork> = withContext(Dispatchers.IO) {
        val response = performJsonRequest(
            network = network,
            method = "POST",
            path = "/api/v1/network/scan",
            body = JSONObject().put(
                "data",
                JSONObject().put("maxResults", 30)
            ),
            connectTimeoutMs = 12_000,
            readTimeoutMs = 20_000
        ).responseBody

        parseWifiScanResponse(response.orEmpty())
    }

    suspend fun readDeviceWifiStatus(network: Network): DeviceWifiStatus = withContext(Dispatchers.IO) {
        val result = performJsonRequest(
            network = network,
            method = "GET",
            path = "/api/v1/network/status",
            body = null,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 8_000
        )

        parseDeviceWifiStatus(result.responseBody)
    }

    suspend fun pairDevice(
        network: Network,
        deviceUid: String,
        serialNumber: String,
        shortId: String,
        currentToken: String = ""
    ): PairingResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("deviceUid", deviceUid)
                .put("serialNumber", serialNumber)
                .put("shortId", shortId)
                .put("rotateToken", false)
        )

        val result = performJsonRequest(
            network = network,
            method = "POST",
            path = "/api/v1/security/pair",
            body = body,
            connectTimeoutMs = 10_000,
            readTimeoutMs = 15_000,
            apiToken = currentToken
        )

        val token = parsePairingToken(result.responseBody)

        PairingResult(
            success = result.success && token.isNotBlank(),
            token = token,
            responseCode = result.responseCode,
            responseBody = result.responseBody,
            errorMessage = result.errorMessage
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
        val configuredClientEnabled = if (data.has("configuredClientEnabled")) {
            data.optBoolean("configuredClientEnabled", false)
        } else {
            data.optBoolean("clientEnabled", false)
        }

        return DeviceWifiStatus(
            connected = connected && isValidHomeNetworkIp(clientIp),
            clientIp = clientIp,
            configuredClientEnabled = configuredClientEnabled,
            clientSsid = data.optString("clientSsid", "").trim(),
            clientPasswordSet = data.optBoolean("clientPasswordSet", false),
            wifiStatusCode = data.optInt("wifiStatusCode").takeIf { data.has("wifiStatusCode") },
            lastClientWifiStatus = data.optInt("lastClientWifiStatus").takeIf { data.has("lastClientWifiStatus") },
            lastClientConnectMessage = data.optString("lastClientConnectMessage", "").trim()
        )
    }

    suspend fun sendHomeWifiCredentials(
        network: Network,
        setupSsid: String,
        setupPassword: String,
        homeSsid: String,
        homePassword: String,
        disableSetupAccessPoint: Boolean,
        apiToken: String = ""
    ): SetupResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("clientEnabled", true)
                .put("clientSsid", homeSsid)
                .put("clientPassword", homePassword)
                .put("setupApEnabled", !disableSetupAccessPoint)
                .put("setupApPassword", setupPassword)
                .put("applyNow", true)
        )

        performJsonRequest(
            network = network,
            method = "PUT",
            path = "/api/v1/network/wifi",
            body = body,
            connectTimeoutMs = 10_000,
            readTimeoutMs = if (disableSetupAccessPoint) 15_000 else 20_000,
            acceptNetworkTransition = disableSetupAccessPoint,
            apiToken = apiToken
        )
    }

    private fun performJsonRequest(
        network: Network,
        method: String,
        path: String,
        body: JSONObject?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        acceptNetworkTransition: Boolean = false,
        apiToken: String = ""
    ): SetupResult {
        var connection: HttpURLConnection? = null
        var bodySent = false

        return try {
            connection = network.openConnection(URL("$BASE_URL$path")) as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doInput = true
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Connection", "close")
            apiToken.trim().takeIf { token -> token.isNotBlank() }?.let { token ->
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

    private fun parsePairingToken(responseBody: String?): String {
        val data = responseBody
            ?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            ?.optJSONObject("data")
            ?: return ""

        return data.optString("token", "").trim()
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
                add(HomeWifiNetwork(ssid = ssid, rssi = item.optInt("rssi", -100)))
            }
        }
            .distinctBy { network -> network.ssid }
            .sortedByDescending { network -> network.rssi }
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

    private companion object {
        const val SETUP_DEVICE_IP = "192.168.4.1"
        const val BASE_URL = "http://$SETUP_DEVICE_IP"
    }
}
