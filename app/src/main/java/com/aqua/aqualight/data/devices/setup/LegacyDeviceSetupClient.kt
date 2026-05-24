package com.aqua.aqualight.data.devices.setup

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LegacyDeviceSetupClient {

    data class SetupResult(
        val success: Boolean,
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
        val statusText: String
    )

    suspend fun scanHomeWifiNetworks(
        network: Network
    ): List<HomeWifiNetwork> = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put(
                "WiFiSC",
                JSONObject().apply {
                    put("Scan", 0)
                }
            )
        }

        val responseText = performGetRequest(
            network = network,
            requestJson = requestJson,
            connectTimeoutMs = 12_000,
            readTimeoutMs = 15_000
        )

        parseWifiScanResponse(responseText)
    }

    suspend fun readDeviceWifiStatus(
        network: Network
    ): DeviceWifiStatus = withContext(Dispatchers.IO) {
        val requestJson = JSONObject().apply {
            put(
                "WiFiSC",
                JSONObject().apply {
                    put("ServerEnabled", 0)
                    put("ServerSSID", 0)
                    put("ServerPassword", 0)
                    put("ClientEnabled", 0)
                    put("ClientSSID", 0)
                    put("ClientPassword", 0)
                }
            )
        }

        val responseText = performGetRequest(
            network = network,
            requestJson = requestJson,
            connectTimeoutMs = 8_000,
            readTimeoutMs = 8_000
        )

        parseDeviceWifiStatus(responseText)
    }

    fun parseDeviceWifiStatus(
        responseText: String?
    ): DeviceWifiStatus {
        if (responseText.isNullOrBlank()) {
            return DeviceWifiStatus(
                connected = false,
                clientIp = "",
                statusText = "Empty response"
            )
        }

        val root = try {
            JSONObject(responseText)
        } catch (exception: Exception) {
            return DeviceWifiStatus(
                connected = false,
                clientIp = "",
                statusText = "Invalid JSON"
            )
        }

        val wifiObject = root.optJSONObject("WiFiSC")

        val rootIp = root
            .optString("IP", "")
            .trim()

        val clientIpFromWifi = wifiObject
            ?.optString("ClientIP", "")
            ?.trim()
            .orEmpty()

        val clientIp = clientIpFromWifi.ifBlank {
            rootIp
        }

        val clientEnabled = wifiObject?.optBoolean("ClientEnabled", true) ?: true

        val statusText = wifiObject
            ?.optString("ClientStatus", "")
            ?.trim()
            .orEmpty()

        val hasValidClientIp = clientIp.isNotBlank() &&
            clientIp != "0.0.0.0" &&
            clientIp != "192.168.4.1" &&
            !clientIp.startsWith("192.168.4.")

        return DeviceWifiStatus(
            connected = hasValidClientIp && clientEnabled,
            clientIp = clientIp,
            statusText = statusText.ifBlank {
                if (hasValidClientIp) {
                    "Connected"
                } else {
                    "Not connected"
                }
            }
        )
    }

    suspend fun sendHomeWifiCredentials(
        network: Network,
        setupSsid: String,
        setupPassword: String,
        homeSsid: String,
        homePassword: String,
        disableSetupAccessPoint: Boolean = true
    ): SetupResult = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put(
                "WiFiSC",
                JSONObject().apply {
                    put("ServerEnabled", if (disableSetupAccessPoint) 0 else 1)
                    put("ServerSSID", setupSsid)
                    put("ServerPassword", setupPassword)

                    put("ClientEnabled", 1)
                    put("ClientSSID", homeSsid)
                    put("ClientPassword", homePassword)
                }
            )

            put(
                "Main",
                JSONObject().apply {
                    put("SaveConfig", 0)
                }
            )
        }

        val body = buildRawFormBody(json)

        var connection: HttpURLConnection? = null

        return@withContext try {
            connection = network.openConnection(
                URL("$BASE_URL/set?")
            ) as HttpURLConnection

            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=utf-8"
            )

            BufferedWriter(
                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                )
            ).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = connection.responseCode

            val responseText = runCatching {
                connection.inputStream
                    .bufferedReader()
                    .use { reader ->
                        reader.readText()
                    }
            }.getOrNull()

            SetupResult(
                success = responseCode in 200..299,
                responseCode = responseCode,
                responseBody = responseText,
                errorMessage = null
            )
        } catch (exception: Exception) {
            SetupResult(
                success = false,
                responseCode = null,
                responseBody = null,
                errorMessage = exception.message ?: exception.toString()
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun performGetRequest(
        network: Network,
        requestJson: JSONObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): String {
        val url = buildString {
            append("$BASE_URL/get?")
            append(buildEncodedFormBody(requestJson))
        }

        val connection = network.openConnection(
            URL(url)
        ) as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doInput = true

            connection.inputStream
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRawFormBody(
        json: JSONObject
    ): String {
        val sRet = JSONObject().apply {
            put("iPostCount", System.currentTimeMillis() % 100000)
        }

        return buildString {
            append("Json=")
            append(json.toString())
            append("&sRet=")
            append(sRet.toString())
        }
    }

    private fun buildEncodedFormBody(
        json: JSONObject
    ): String {
        val sRet = JSONObject().apply {
            put("iPostCount", System.currentTimeMillis() % 100000)
        }

        return buildString {
            append("Json=")
            append(
                URLEncoder.encode(
                    json.toString(),
                    "UTF-8"
                )
            )

            append("&sRet=")

            append(
                URLEncoder.encode(
                    sRet.toString(),
                    "UTF-8"
                )
            )
        }
    }

    private fun parseWifiScanResponse(
        responseText: String
    ): List<HomeWifiNetwork> {
        val root = JSONObject(responseText)

        val scanObject = root
            .optJSONObject("WiFiSC")
            ?.optJSONObject("Scan")
            ?: root.optJSONObject("Scan")
            ?: return emptyList()

        return buildList {
            val keys = scanObject.keys()

            while (keys.hasNext()) {
                val ssid = keys.next().trim()

                if (ssid.isBlank()) {
                    continue
                }

                val rssi = scanObject.optInt(
                    ssid,
                    -100
                )

                add(
                    HomeWifiNetwork(
                        ssid = ssid,
                        rssi = rssi
                    )
                )
            }
        }
            .distinctBy { network ->
                network.ssid
            }
            .sortedByDescending { network ->
                network.rssi
            }
    }

    private companion object {
        const val BASE_URL = "http://192.168.4.1"
    }
}