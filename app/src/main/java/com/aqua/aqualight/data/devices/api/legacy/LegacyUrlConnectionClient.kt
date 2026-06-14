package com.aqua.aqualight.data.devices.api.legacy

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LegacyUrlConnectionClient : LegacyHttpClient {

    override suspend fun get(
        connection: AquaDeviceConnection,
        endpoint: LegacyEndpoint,
        query: Map<String, String>
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        var http: HttpURLConnection? = null

        try {
            val url = buildUrl(
                connection = connection,
                endpoint = endpoint,
                query = query
            )

            http = URL(url).openConnection() as HttpURLConnection
            http.requestMethod = "GET"
            http.connectTimeout = connection.connectTimeoutMillis
            http.readTimeout = connection.readTimeoutMillis
            http.doInput = true
            http.useCaches = false
            http.setRequestProperty("Accept", "application/json,text/json,text/plain,*/*")
            http.setRequestProperty("Connection", "close")

            val responseCode = http.responseCode
            val body = readResponseBody(http)

            if (responseCode in HTTP_SUCCESS_RANGE) {
                ApiResult.success(body.orEmpty())
            } else {
                ApiResult.failure(
                    code = ApiErrorCode.NETWORK,
                    message = "Legacy GET failed with HTTP $responseCode"
                )
            }
        } catch (exception: java.net.SocketTimeoutException) {
            ApiResult.failure(
                code = ApiErrorCode.TIMEOUT,
                message = exception.message ?: "Legacy GET timed out",
                cause = exception
            )
        } catch (exception: Exception) {
            ApiResult.failure(
                code = ApiErrorCode.NETWORK,
                message = exception.message ?: "Legacy GET failed",
                cause = exception
            )
        } finally {
            http?.disconnect()
        }
    }

    override suspend fun set(
        connection: AquaDeviceConnection,
        command: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        var http: HttpURLConnection? = null

        try {
            http = URL("${connection.baseUrl}${LegacyEndpoint.SET.path}?").openConnection() as HttpURLConnection
            http.requestMethod = "POST"
            http.connectTimeout = connection.connectTimeoutMillis
            http.readTimeout = connection.readTimeoutMillis
            http.doInput = true
            http.doOutput = true
            http.useCaches = false
            http.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            http.setRequestProperty("Connection", "close")

            BufferedWriter(
                OutputStreamWriter(
                    http.outputStream,
                    Charsets.UTF_8
                )
            ).use { writer ->
                writer.write(command)
                writer.flush()
            }

            val responseCode = http.responseCode
            val body = readResponseBody(http)

            if (responseCode in HTTP_SUCCESS_RANGE) {
                ApiResult.success(body.orEmpty())
            } else {
                ApiResult.failure(
                    code = ApiErrorCode.NETWORK,
                    message = "Legacy SET failed with HTTP $responseCode"
                )
            }
        } catch (exception: java.net.SocketTimeoutException) {
            ApiResult.failure(
                code = ApiErrorCode.TIMEOUT,
                message = exception.message ?: "Legacy SET timed out",
                cause = exception
            )
        } catch (exception: Exception) {
            ApiResult.failure(
                code = ApiErrorCode.NETWORK,
                message = exception.message ?: "Legacy SET failed",
                cause = exception
            )
        } finally {
            http?.disconnect()
        }
    }

    private fun buildUrl(
        connection: AquaDeviceConnection,
        endpoint: LegacyEndpoint,
        query: Map<String, String>
    ): String {
        if (query.isEmpty()) {
            return "${connection.baseUrl}${endpoint.path}"
        }

        val encodedQuery = query.entries.joinToString(separator = "&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }

        return "${connection.baseUrl}${endpoint.path}?$encodedQuery"
    }

    private fun readResponseBody(
        connection: HttpURLConnection
    ): String? {
        return runCatching {
            connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> reader.readText() }
        }.getOrElse {
            connection.errorStream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader -> reader.readText() }
        }
    }

    private fun urlEncode(
        value: String
    ): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
    }
}
