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

        val sRet = JSONObject().apply {
            put("iPostCount", System.currentTimeMillis() % 100000)
        }

        val url = buildString {
            append("http://192.168.4.1/get?")
            append("Json=")
            append(
                URLEncoder.encode(
                    requestJson.toString(),
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

        val connection = network.openConnection(
            URL(url)
        ) as HttpURLConnection

        return@withContext try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.doInput = true

            val responseText = connection.inputStream
                .bufferedReader()
                .use { reader ->
                    reader.readText()
                }

            parseWifiScanResponse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun sendHomeWifiCredentials(
        network: Network,
        homeSsid: String,
        homePassword: String,
        disableSetupAccessPoint: Boolean = true
    ): SetupResult = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put(
                "WiFiSC",
                JSONObject().apply {
                    put("ServerEnabled", if (disableSetupAccessPoint) 0 else 1)
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

        val sRet = JSONObject().apply {
            put("iPostCount", System.currentTimeMillis() % 100000)
        }

        val body = buildString {
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

        return@withContext try {
            val connection = network.openConnection(
                URL("http://192.168.4.1/set?")
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

            connection.disconnect()

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
                val ssid = keys.next()
                    .trim()

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
}