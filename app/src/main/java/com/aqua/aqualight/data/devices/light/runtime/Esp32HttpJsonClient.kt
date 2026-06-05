package com.aqua.aqualight.data.devices.light.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class Esp32HttpJsonClient {

    suspend fun getJson(
        ip: String,
        json: String,
        requestTag: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val encodedJson = URLEncoder.encode(
                    json,
                    Charsets.UTF_8.name()
                )

                val encodedTag = URLEncoder.encode(
                    "\"$requestTag\"",
                    Charsets.UTF_8.name()
                )

                val url = URL(
                    "http://$ip/get?Json=$encodedJson&sRet=$encodedTag"
                )

                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS

                    val responseCode = connection.responseCode
                    val responseText = readResponseText(connection)

                    if (responseCode in 200..299) {
                        responseText
                    } else {
                        throw IllegalStateException("HTTP $responseCode")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    suspend fun postSet(
        ip: String,
        json: String,
        requestTag: String
    ): LightCommandResult {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null

            runCatching {
                val url = URL("http://$ip/set?")
                val body = buildFormBody(
                    json = json,
                    requestTag = requestTag
                )

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                }

                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use { writer ->
                    writer.write(body)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseText = readResponseText(connection)

                if (responseCode in 200..299) {
                    LightCommandResult.success(responseText)
                } else {
                    LightCommandResult.failure("ESP32 command failed: HTTP $responseCode")
                }
            }.getOrElse { error ->
                LightCommandResult.failure(
                    error.message ?: "ESP32 command could not be sent"
                )
            }.also {
                connection?.disconnect()
            }
        }
    }

    private fun buildFormBody(
        json: String,
        requestTag: String
    ): String {
        val encodedJson = URLEncoder.encode(
            json,
            Charsets.UTF_8.name()
        )

        val encodedTag = URLEncoder.encode(
            "\"$requestTag\"",
            Charsets.UTF_8.name()
        )

        return "Json=$encodedJson&sRet=$encodedTag"
    }

    private fun readResponseText(
        connection: HttpURLConnection
    ): String {
        val stream = runCatching {
            connection.inputStream
        }.getOrNull() ?: runCatching {
            connection.errorStream
        }.getOrNull() ?: return ""

        return BufferedReader(
            stream.reader(Charsets.UTF_8)
        ).use { reader ->
            reader.readText()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 2_000
        private const val READ_TIMEOUT_MS = 3_000
    }
}