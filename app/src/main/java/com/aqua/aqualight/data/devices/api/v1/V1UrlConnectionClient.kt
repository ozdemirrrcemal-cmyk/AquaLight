package com.aqua.aqualight.data.devices.api.v1

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.network.LocalNetworkAddressPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class V1UrlConnectionClient : V1HttpClient {

    override suspend fun get(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint
    ): ApiResult<String> = request(
        connection = connection,
        endpoint = endpoint,
        method = "GET",
        body = null
    )

    override suspend fun post(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint,
        body: String
    ): ApiResult<String> = request(
        connection = connection,
        endpoint = endpoint,
        method = "POST",
        body = body
    )

    override suspend fun put(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint,
        body: String
    ): ApiResult<String> = request(
        connection = connection,
        endpoint = endpoint,
        method = "PUT",
        body = body
    )

    private suspend fun request(
        connection: AquaDeviceConnection,
        endpoint: V1Endpoint,
        method: String,
        body: String?
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        if (!connection.useHttps) {
            LocalNetworkAddressPolicy.requireLocalCleartextHost(connection.host)
        }

        var http: HttpURLConnection? = null
        try {
            http = URL("${connection.baseUrl}${endpoint.path}").openConnection() as HttpURLConnection
            http.requestMethod = method
            http.connectTimeout = connection.connectTimeoutMillis
            http.readTimeout = connection.readTimeoutMillis
            http.doInput = true
            http.useCaches = false
            http.setRequestProperty("Accept", "application/json")
            http.setRequestProperty("Connection", "close")

            if (body != null) {
                http.doOutput = true
                http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                BufferedWriter(OutputStreamWriter(http.outputStream, Charsets.UTF_8)).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val code = http.responseCode
            val payload = runCatching {
                http.inputStream.bufferedReader().use { reader -> reader.readText() }
            }.getOrElse {
                http.errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            }

            if (code in 200..299 && isOk(payload)) {
                ApiResult.success(payload)
            } else {
                ApiResult.failure(
                    code = ApiErrorCode.NETWORK,
                    message = parseErrorMessage(payload) ?: "AquaLight V1 $method ${endpoint.path} failed with HTTP $code"
                )
            }
        } catch (exception: SocketTimeoutException) {
            ApiResult.failure(
                code = ApiErrorCode.TIMEOUT,
                message = exception.message ?: "AquaLight V1 request timed out",
                cause = exception
            )
        } catch (exception: Exception) {
            ApiResult.failure(
                code = ApiErrorCode.NETWORK,
                message = exception.message ?: "AquaLight V1 request failed",
                cause = exception
            )
        } finally {
            http?.disconnect()
        }
    }

    private fun isOk(payload: String): Boolean {
        if (payload.isBlank()) return true
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        return root.optBoolean("ok", false)
    }

    private fun parseErrorMessage(payload: String): String? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }
}
