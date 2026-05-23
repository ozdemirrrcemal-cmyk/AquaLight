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
            ).use {
                writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = connection.responseCode

            val responseText = runCatching {
                connection.inputStream
                .bufferedReader()
                .use {
                    reader ->
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
}