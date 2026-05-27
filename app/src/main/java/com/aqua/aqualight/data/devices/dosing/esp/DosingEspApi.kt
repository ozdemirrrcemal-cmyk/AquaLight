package com.aqua.aqualight.data.devices.dosing.esp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DosingEspApi(
    private val connectTimeoutMillis: Int = 6_000,
    private val readTimeoutMillis: Int = 8_000
) {

    suspend fun getJson(
        deviceIp: String,
        payload: JSONObject
    ): JSONObject {
        return withContext(
            Dispatchers.IO
        ) {
            val encodedJson =
                encode(
                    value = payload.toString()
                )

            val url =
                URL(
                    "${createBaseUrl(deviceIp)}/get?Json=$encodedJson&sRet=1"
                )

            android.util.Log.d(
                TAG,
                "GET $url"
            )

            val connection =
                openConnection(
                    url = url,
                    method = "GET"
                )

            try {
                val responseText =
                    readResponse(
                        connection = connection
                    )

                JSONObject(
                    responseText
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun postJson(
        deviceIp: String,
        payload: JSONObject
    ): JSONObject {
        return withContext(
            Dispatchers.IO
        ) {
            val url =
                URL(
                    "${createBaseUrl(deviceIp)}/set"
                )

            val body =
                "Json=${encode(payload.toString())}&sRet=1&"

            android.util.Log.d(
                TAG,
                "POST $url BODY=$body"
            )

            val connection =
                openConnection(
                    url = url,
                    method = "POST"
                ).apply {
                    doOutput =
                        true

                    setRequestProperty(
                        "Content-Type",
                        "text/plain; charset=UTF-8"
                    )
                }

            try {
                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use { writer ->
                    writer.write(
                        body
                    )

                    writer.flush()
                }

                val responseText =
                    readResponse(
                        connection = connection
                    )

                JSONObject(
                    responseText
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun openConnection(
        url: URL,
        method: String
    ): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod =
                method

            connectTimeout =
                connectTimeoutMillis

            readTimeout =
                readTimeoutMillis

            doInput =
                true

            useCaches =
                false

            setRequestProperty(
                "Accept",
                "application/json, text/plain, */*"
            )
        }
    }

    private fun readResponse(
        connection: HttpURLConnection
    ): String {
        val responseCode =
            connection.responseCode

        val stream =
            if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

        val responseText =
            stream.bufferedReader().use(
                BufferedReader::readText
            )

        android.util.Log.d(
            TAG,
            "HTTP $responseCode RESPONSE=$responseText"
        )

        if (responseCode !in 200..299) {
            throw IllegalStateException(
                "ESP32 request failed. HTTP $responseCode: $responseText"
            )
        }

        if (responseText.isBlank()) {
            throw IllegalStateException(
                "ESP32 returned an empty response."
            )
        }

        return responseText
    }

    private fun createBaseUrl(
        deviceIp: String
    ): String {
        val normalizedIp =
            deviceIp.trim()
                .removeSuffix(
                    suffix = "/"
                )

        return if (
            normalizedIp.startsWith(
                prefix = "http://",
                ignoreCase = true
            ) ||
            normalizedIp.startsWith(
                prefix = "https://",
                ignoreCase = true
            )
        ) {
            normalizedIp
        } else {
            "http://$normalizedIp"
        }
    }

    private fun encode(
        value: String
    ): String {
        return URLEncoder.encode(
            value,
            Charsets.UTF_8.name()
        )
    }

    companion object {
        private const val TAG = "DOSING_ESP"
    }
}