package com.aqua.aqualight.data.devices.firmware

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DeviceFirmwareUpdateRepository(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    ),
    private val manifestUrl: String = DEFAULT_STABLE_MANIFEST_URL
) {

    suspend fun startLatestUpdate(
        deviceId: Long
    ): FirmwareUpdateResult = withContext(Dispatchers.IO) {
        if (deviceId <= 0L) {
            return@withContext FirmwareUpdateResult.Error("Device id is missing")
        }

        val device = devicesStore.devicesFlow
            .first()
            .firstOrNull { savedDevice -> savedDevice.id == deviceId }
            ?: return@withContext FirmwareUpdateResult.Error("Device not found")

        val host = device.ip.trim()
        if (host.isBlank()) {
            return@withContext FirmwareUpdateResult.Error("Device IP address is missing")
        }

        val statusResponse = getJson(
            url = "http://$host/api/v1/firmware/status",
            connectTimeoutMs = DEVICE_CONNECT_TIMEOUT_MS,
            readTimeoutMs = DEVICE_READ_TIMEOUT_MS
        )
        if (!statusResponse.isOk) {
            return@withContext FirmwareUpdateResult.Error(
                statusResponse.errorMessage.ifBlank {
                    "Could not read device firmware status"
                }
            )
        }

        val statusData = statusResponse.json.optJSONObject("data")
            ?: return@withContext FirmwareUpdateResult.Error("Invalid firmware status response")

        if (!statusData.optBoolean("available", false) || !statusData.optBoolean("otaSupported", false)) {
            return@withContext FirmwareUpdateResult.Error("OTA update is not available on this device")
        }

        val deviceProductKey = statusData.optString("productKey", "").trim()
            .ifBlank { device.productKey.name.takeUnless { it == "UNKNOWN" }.orEmpty() }
        val deviceProductId = statusData.optString("productId", "").trim()
            .ifBlank { device.productId }
        val deviceHardwareRevision = statusData.optString("hardwareRevision", "").trim()
            .ifBlank { device.hardwareRevision }
        val currentVersion = statusData.optString("firmwareVersion", "").trim()
            .ifBlank { device.firmwareVersion.ifBlank { device.firmwareBuild } }

        val manifestResponse = getJson(
            url = manifestUrl,
            connectTimeoutMs = MANIFEST_CONNECT_TIMEOUT_MS,
            readTimeoutMs = MANIFEST_READ_TIMEOUT_MS
        )
        if (!manifestResponse.isOk) {
            return@withContext FirmwareUpdateResult.Error(
                manifestResponse.errorMessage.ifBlank {
                    "Could not download OTA manifest"
                }
            )
        }

        val artifact = findCompatibleArtifact(
            manifest = manifestResponse.json,
            productKey = deviceProductKey,
            productId = deviceProductId,
            hardwareRevision = deviceHardwareRevision
        ) ?: return@withContext FirmwareUpdateResult.Error(
            "No compatible OTA firmware found for this device"
        )

        if (!isNewerVersion(artifact.version, currentVersion)) {
            return@withContext FirmwareUpdateResult.UpToDate(
                currentVersion = currentVersion.ifBlank { artifact.version },
                latestVersion = artifact.version
            )
        }

        val requestBody = JSONObject()
            .put(
                "data",
                JSONObject()
                    .put("url", artifact.url)
                    .put("sha256", artifact.sha256)
                    .put("size", artifact.size)
                    .put("version", artifact.version)
                    .put("productKey", artifact.productKey)
                    .put("productId", artifact.productId)
                    .put("hardwareRevision", artifact.hardwareRevision)
                    .put("allowInsecureHttp", false)
                    .put("startDownload", true)
                    .put("applyNow", true)
            )

        val otaResponse = postJson(
            url = "http://$host/api/v1/firmware/ota",
            body = requestBody,
            connectTimeoutMs = DEVICE_CONNECT_TIMEOUT_MS,
            readTimeoutMs = OTA_START_READ_TIMEOUT_MS
        )

        if (!otaResponse.isOk) {
            return@withContext FirmwareUpdateResult.Error(
                otaResponse.errorMessage.ifBlank {
                    "Device rejected OTA update request"
                }
            )
        }

        FirmwareUpdateResult.Started(
            currentVersion = currentVersion,
            targetVersion = artifact.version,
            filename = artifact.filename
        )
    }

    private fun findCompatibleArtifact(
        manifest: JSONObject,
        productKey: String,
        productId: String,
        hardwareRevision: String
    ): FirmwareArtifact? {
        val artifacts = manifest.optJSONArray("artifacts") ?: return null

        for (index in 0 until artifacts.length()) {
            val artifactObject = artifacts.optJSONObject(index) ?: continue
            val compatibility = artifactObject.optJSONObject("compatibility") ?: continue
            val firmware = artifactObject.optJSONObject("firmware") ?: continue

            val candidateProductKey = compatibility.optString("productKey", "").trim()
            val candidateProductId = compatibility.optString("productId", "").trim()
            val candidateHardwareRevision = compatibility.optString("hardwareRevision", "").trim()

            val productKeyMatches = productKey.isBlank() || candidateProductKey.equals(productKey, ignoreCase = true)
            val productIdMatches = productId.isBlank() || candidateProductId.equals(productId, ignoreCase = true)
            val hardwareMatches = hardwareRevision.isBlank() || candidateHardwareRevision.equals(hardwareRevision, ignoreCase = true)

            if (!productKeyMatches || !productIdMatches || !hardwareMatches) {
                continue
            }

            val url = firmware.optString("url", "").trim()
            val sha256 = firmware.optString("sha256", "").trim().lowercase(Locale.US)
            val size = firmware.optLong("size", 0L)
            val version = firmware.optString("version", manifest.optString("version", "")).trim()
            val filename = firmware.optString("filename", "firmware.bin").trim()

            if (url.isBlank() || sha256.length != 64 || size <= 0L || version.isBlank()) {
                continue
            }

            return FirmwareArtifact(
                version = version,
                filename = filename,
                url = url,
                sha256 = sha256,
                size = size,
                productKey = candidateProductKey.ifBlank { productKey },
                productId = candidateProductId.ifBlank { productId },
                hardwareRevision = candidateHardwareRevision.ifBlank { hardwareRevision }
            )
        }

        return null
    }

    private fun getJson(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpJsonResponse {
        return requestJson(
            url = url,
            method = "GET",
            body = null,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        )
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpJsonResponse {
        return requestJson(
            url = url,
            method = "POST",
            body = body,
            connectTimeoutMs = connectTimeoutMs,
            readTimeoutMs = readTimeoutMs
        )
    }

    private fun requestJson(
        url: String,
        method: String,
        body: JSONObject?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): HttpJsonResponse {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (body != null) {
                    doOutput = true
                }
            }

            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", bytes.size.toString())
                connection.outputStream.use { output ->
                    output.write(bytes)
                }
            }

            val code = connection.responseCode
            val text = readResponseText(
                stream = if (code in 200..299) connection.inputStream else connection.errorStream
            )

            val json = text
                .takeIf { value -> value.isNotBlank() }
                ?.let { value -> JSONObject(value) }
                ?: JSONObject()

            val ok = code in 200..299 && json.optBoolean("ok", code in 200..299)
            val message = if (ok) {
                ""
            } else {
                json.optJSONObject("error")?.optString("message", "").orEmpty()
                    .ifBlank { "HTTP $code" }
            }

            HttpJsonResponse(
                isOk = ok,
                httpCode = code,
                json = json,
                errorMessage = message
            )
        } catch (exception: Exception) {
            HttpJsonResponse(
                isOk = false,
                httpCode = 0,
                json = JSONObject(),
                errorMessage = exception.message ?: "Network error"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readResponseText(
        stream: InputStream?
    ): String {
        if (stream == null) {
            return ""
        }

        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            buildString {
                while (true) {
                    val line = reader.readLine() ?: break
                    append(line)
                }
            }
        }
    }

    private fun isNewerVersion(
        latestVersion: String,
        currentVersion: String
    ): Boolean {
        val latest = latestVersion.trim().removePrefix("v")
        val current = currentVersion.trim().removePrefix("v")

        if (latest.isBlank()) {
            return false
        }
        if (current.isBlank()) {
            return true
        }
        if (latest.equals(current, ignoreCase = true)) {
            return false
        }

        val latestParts = latest.split('.', '-', '_')
        val currentParts = current.split('.', '-', '_')
        val max = maxOf(latestParts.size, currentParts.size)

        for (index in 0 until max) {
            val left = latestParts.getOrNull(index)?.toIntOrNull()
            val right = currentParts.getOrNull(index)?.toIntOrNull()

            if (left != null && right != null && left != right) {
                return left > right
            }
            if (left != null && right == null) {
                return true
            }
            if (left == null && right != null) {
                return false
            }
        }

        // If semantic parsing cannot prove order, allow a different manifest version to be applied.
        return true
    }

    private data class FirmwareArtifact(
        val version: String,
        val filename: String,
        val url: String,
        val sha256: String,
        val size: Long,
        val productKey: String,
        val productId: String,
        val hardwareRevision: String
    )

    private data class HttpJsonResponse(
        val isOk: Boolean,
        val httpCode: Int,
        val json: JSONObject,
        val errorMessage: String
    )

    private companion object {
        const val DEFAULT_STABLE_MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/latest/download/manifest-stable.json"
        const val DEVICE_CONNECT_TIMEOUT_MS = 5_000
        const val DEVICE_READ_TIMEOUT_MS = 8_000
        const val OTA_START_READ_TIMEOUT_MS = 15_000
        const val MANIFEST_CONNECT_TIMEOUT_MS = 8_000
        const val MANIFEST_READ_TIMEOUT_MS = 15_000
    }
}

sealed class FirmwareUpdateResult {
    data class Started(
        val currentVersion: String,
        val targetVersion: String,
        val filename: String
    ) : FirmwareUpdateResult()

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String
    ) : FirmwareUpdateResult()

    data class Error(
        val message: String
    ) : FirmwareUpdateResult()
}
