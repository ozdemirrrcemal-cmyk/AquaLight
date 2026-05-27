package com.aqua.aqualight.data.devices.dosing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object EspDosingHttpClient {

    suspend fun getJson(
        deviceIp: String,
        requestJson: String
    ): JSONObject? {
        val safeDeviceIp =
            deviceIp.trim()

        if (safeDeviceIp.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val encodedJson =
                    encode(
                        value = requestJson
                    )

                val url =
                    URL(
                        "http://$safeDeviceIp/get?Json=$encodedJson&sRet=0"
                    )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    2500

                connection.readTimeout =
                    2500

                connection.useCaches =
                    false

                val responseCode =
                    connection.responseCode

                if (responseCode !in 200..299) {
                    connection.disconnect()
                    return@runCatching null
                }

                val responseBody =
                    connection.inputStream
                        .bufferedReader()
                        .use { reader ->
                            reader.readText()
                        }

                connection.disconnect()

                JSONObject(
                    responseBody
                )
            }.getOrNull()
        }
    }

    suspend fun postJson(
        deviceIp: String,
        requestJson: String
    ): JSONObject? {
        val safeDeviceIp =
            deviceIp.trim()

        if (safeDeviceIp.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    URL(
                        "http://$safeDeviceIp/set"
                    )

                val body =
                    "Json=${encode(requestJson)}&sRet=0"

                val bodyBytes =
                    body.toByteArray(
                        StandardCharsets.UTF_8
                    )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.connectTimeout =
                    2500

                connection.readTimeout =
                    2500

                connection.useCaches =
                    false

                connection.doOutput =
                    true

                connection.setRequestProperty(
                    "Content-Type",
                    "text/plain; charset=utf-8"
                )

                connection.setRequestProperty(
                    "Content-Length",
                    bodyBytes.size.toString()
                )

                BufferedOutputStream(
                    connection.outputStream
                ).use { outputStream ->
                    outputStream.write(
                        bodyBytes
                    )

                    outputStream.flush()
                }

                val responseCode =
                    connection.responseCode

                if (responseCode !in 200..299) {
                    connection.disconnect()
                    return@runCatching null
                }

                val responseBody =
                    connection.inputStream
                        .bufferedReader()
                        .use { reader ->
                            reader.readText()
                        }

                connection.disconnect()

                JSONObject(
                    responseBody
                )
            }.getOrNull()
        }
    }

    private fun encode(
        value: String
    ): String {
        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }
}