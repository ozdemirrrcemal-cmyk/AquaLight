package com.aqua.aqualight.data.devices.firmware

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AquaLightFirmwareUpdateManager(
    context: Context,
    private val manifestUrl: String = DEFAULT_STABLE_MANIFEST_URL
) {

    private val devicesStore = DevicesDataStoreManager.create(
        context = context.applicationContext
    )

    suspend fun checkFirmwareUpdate(
        device: DevicesDataStoreManager.DeviceInfo
    ): ApiResult<FirmwareUpdateCheckResult> {
        val host = device.ip.trim()
        if (host.isBlank() || host == "—") {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Device IP address is missing"
            )
        }

        val manifest = when (val result = loadManifest()) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> return result
        }

        val artifact = manifest.findCompatibleArtifact(device) ?: return ApiResult.failure(
            code = ApiErrorCode.UNSUPPORTED_FIRMWARE,
            message = "No compatible AquaLight firmware artifact was found for this device"
        )

        val currentVersion = device.firmwareVersion.ifBlank {
            device.firmwareBuild
        }

        return ApiResult.success(
            FirmwareUpdateCheckResult(
                version = artifact.version,
                currentVersion = currentVersion,
                alreadyUpToDate = !isNewerVersion(artifact.version, currentVersion),
                size = artifact.size,
                artifact = artifact
            )
        )
    }

    suspend fun startFirmwareUpdate(
        device: DevicesDataStoreManager.DeviceInfo
    ): ApiResult<FirmwareUpdateStartResult> {
        val check = when (val result = checkFirmwareUpdate(device)) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> return result
        }

        return startFirmwareUpdate(
            device = device,
            check = check
        )
    }

    suspend fun startFirmwareUpdate(
        device: DevicesDataStoreManager.DeviceInfo,
        check: FirmwareUpdateCheckResult,
        onProgress: suspend (FirmwareUpdateProgress) -> Unit = {}
    ): ApiResult<FirmwareUpdateStartResult> {
        val host = device.ip.trim()
        if (host.isBlank() || host == "—") {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Device IP address is missing"
            )
        }

        if (check.alreadyUpToDate) {
            return ApiResult.success(
                FirmwareUpdateStartResult(
                    version = check.version,
                    alreadyUpToDate = true,
                    message = "Device is already on firmware ${check.version}"
                )
            )
        }

        val artifact = check.artifact
        val baseUrl = "http://${host}:80"

        onProgress(
            FirmwareUpdateProgress(
                stage = FirmwareUpdateStage.PREPARING,
                message = "Checking device security..."
            )
        )

        val token = when (val result = ensureApiTokenIfRequired(baseUrl, device)) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> return result
        }

        val requestBody = JSONObject()
            .put(
                "data",
                JSONObject()
                    .put("url", artifact.url)
                    .put("version", artifact.version)
                    .put("sha256", artifact.sha256)
                    .put("size", artifact.size)
                    .put("productKey", artifact.productKey)
                    .put("productId", artifact.productId)
                    .put("hardwareRevision", artifact.hardwareRevision)
                    .put("startDownload", true)
                    .put("applyNow", true)
            )
            .toString()

        onProgress(
            FirmwareUpdateProgress(
                stage = FirmwareUpdateStage.DOWNLOADING,
                message = "Firmware download is starting...",
                progressPercent = 0
            )
        )

        val startResponse = postJson(
            url = "$baseUrl/api/v1/firmware/ota",
            body = requestBody,
            token = token,
            connectTimeoutMillis = DEVICE_CONNECT_TIMEOUT_MS,
            readTimeoutMillis = DEVICE_OTA_START_READ_TIMEOUT_MS
        )

        if (!startResponse.isSuccessful) {
            return ApiResult.failure(
                code = startResponse.errorCode(),
                message = startResponse.errorMessage("Firmware OTA request failed")
            )
        }

        val pollMessage = pollFirmwareProgress(
            baseUrl = baseUrl,
            token = token,
            onProgress = onProgress
        )

        if (pollMessage?.contains("failed", ignoreCase = true) == true ||
            pollMessage?.contains("error", ignoreCase = true) == true
        ) {
            return ApiResult.failure(
                code = ApiErrorCode.UNKNOWN,
                message = pollMessage
            )
        }

        val completionMessage = waitForUpdatedFirmware(
            baseUrl = baseUrl,
            token = token,
            targetVersion = artifact.version,
            onProgress = onProgress
        )

        return ApiResult.success(
            FirmwareUpdateStartResult(
                version = artifact.version,
                alreadyUpToDate = false,
                message = completionMessage
                    ?: pollMessage
                    ?: "Firmware update started. The device may restart during update."
            )
        )
    }

    private suspend fun loadManifest(): ApiResult<AquaLightOtaManifest> {
        val response = getJson(
            url = manifestUrl,
            token = null,
            connectTimeoutMillis = INTERNET_CONNECT_TIMEOUT_MS,
            readTimeoutMillis = INTERNET_READ_TIMEOUT_MS
        )

        if (!response.isSuccessful) {
            return ApiResult.failure(
                code = response.errorCode(),
                message = response.errorMessage("Firmware manifest could not be downloaded")
            )
        }

        return try {
            val root = JSONObject(response.body.orEmpty())
            val artifacts = root.optJSONArray("artifacts") ?: JSONArray()
            val parsedArtifacts = buildList {
                for (index in 0 until artifacts.length()) {
                    val artifact = artifacts.optJSONObject(index) ?: continue
                    parseArtifact(artifact)?.let(::add)
                }
            }

            if (parsedArtifacts.isEmpty()) {
                ApiResult.failure(
                    code = ApiErrorCode.INVALID_RESPONSE,
                    message = "Firmware manifest does not contain OTA artifacts"
                )
            } else {
                ApiResult.success(
                    AquaLightOtaManifest(
                        channel = root.optString("channel"),
                        version = root.optString("version"),
                        artifacts = parsedArtifacts
                    )
                )
            }
        } catch (exception: Exception) {
            ApiResult.failure(
                code = ApiErrorCode.PARSE,
                message = exception.message ?: "Firmware manifest could not be parsed",
                cause = exception
            )
        }
    }

    private fun parseArtifact(
        artifact: JSONObject
    ): AquaLightFirmwareArtifact? {
        val product = artifact.optJSONObject("product")
        val compatibility = artifact.optJSONObject("compatibility")
        val firmware = artifact.optJSONObject("firmware") ?: return null

        val productKey = compatibility?.optString("productKey").orNonBlank()
            ?: product?.optString("productKey").orNonBlank()
            ?: return null

        val productId = compatibility?.optString("productId").orNonBlank()
            ?: product?.optString("productId").orNonBlank()
            ?: ""

        return AquaLightFirmwareArtifact(
            env = artifact.optString("env"),
            productKey = productKey,
            productId = productId,
            skuCode = product?.optString("skuCode").orEmpty(),
            hardwareRevision = compatibility?.optString("hardwareRevision").orNonBlank()
                ?: product?.optString("hardwareRevision").orNonBlank()
                ?: "",
            version = firmware.optString("version"),
            url = firmware.optString("url"),
            sha256 = firmware.optString("sha256"),
            size = firmware.optLong("size", 0L)
        ).takeIf { parsed ->
            parsed.version.isNotBlank() &&
                parsed.url.isNotBlank() &&
                parsed.sha256.length == 64 &&
                parsed.size > 0L
        }
    }

    private suspend fun ensureApiTokenIfRequired(
        baseUrl: String,
        device: DevicesDataStoreManager.DeviceInfo
    ): ApiResult<String?> {
        val securityStatus = getJson(
            url = "$baseUrl/api/v1/security/status",
            token = device.apiToken.takeIf { token -> token.isNotBlank() },
            connectTimeoutMillis = DEVICE_CONNECT_TIMEOUT_MS,
            readTimeoutMillis = DEVICE_READ_TIMEOUT_MS
        )

        if (!securityStatus.isSuccessful) {
            // Older firmware or token gate disabled builds may not expose /security/status.
            // OTA can still proceed without a token when firmware has the gate compiled off.
            if (securityStatus.statusCode == HTTP_NOT_FOUND) {
                return ApiResult.success(null)
            }
            return ApiResult.success(
                device.apiToken.takeIf { token -> token.isNotBlank() }
            )
        }

        val statusData = securityStatus.dataObject()
        val tokenGateEnabled = statusData?.optBoolean("tokenGateEnabled", false) ?: false
        if (!tokenGateEnabled) {
            return ApiResult.success(null)
        }

        val storedToken = device.apiToken.trim()
        val paired = statusData?.optBoolean("paired", false) ?: false
        if (paired && storedToken.isNotBlank()) {
            return ApiResult.success(storedToken)
        }

        if (paired && storedToken.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Device is already paired but this phone does not have its API token. Reset device pairing or re-add the device."
            )
        }

        return pairDevice(
            baseUrl = baseUrl,
            device = device,
            currentToken = storedToken.takeIf { token -> token.isNotBlank() }
        )
    }

    private suspend fun pairDevice(
        baseUrl: String,
        device: DevicesDataStoreManager.DeviceInfo,
        currentToken: String?
    ): ApiResult<String?> {
        val identityPayload = JSONObject()
            .put("deviceUid", device.deviceUid)
            .put("serialNumber", device.serialNumber.ifBlank { device.serial })
            .put("shortId", device.shortId)
            .put("firmwareSerial", device.firmwareSerial)
            .put("macAddress", device.macAddress)
            .put("rotateToken", false)

        val body = JSONObject()
            .put("data", identityPayload)
            .toString()

        val response = postJson(
            url = "$baseUrl/api/v1/security/pair",
            body = body,
            token = currentToken,
            connectTimeoutMillis = DEVICE_CONNECT_TIMEOUT_MS,
            readTimeoutMillis = DEVICE_READ_TIMEOUT_MS
        )

        if (!response.isSuccessful) {
            return ApiResult.failure(
                code = response.errorCode(),
                message = response.errorMessage("Device pairing failed")
            )
        }

        val data = response.dataObject()
        val token = data?.optString("token").orNonBlank()
        if (token == null) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_RESPONSE,
                message = "Device did not return an API token"
            )
        }

        devicesStore.updateDeviceApiToken(
            id = device.id,
            apiToken = token
        )

        return ApiResult.success(token)
    }

    private suspend fun pollFirmwareProgress(
        baseUrl: String,
        token: String?,
        onProgress: suspend (FirmwareUpdateProgress) -> Unit
    ): String? {
        repeat(FIRMWARE_STATUS_POLL_COUNT) {
            delay(FIRMWARE_STATUS_POLL_INTERVAL_MS)
            val response = getJson(
                url = "$baseUrl/api/v1/firmware/status",
                token = token,
                connectTimeoutMillis = DEVICE_CONNECT_TIMEOUT_MS,
                readTimeoutMillis = DEVICE_READ_TIMEOUT_MS
            )

            if (!response.isSuccessful) {
                if (response.statusCode == 0) {
                    onProgress(
                        FirmwareUpdateProgress(
                            stage = FirmwareUpdateStage.RESTARTING,
                            message = "Device is restarting..."
                        )
                    )
                    return "Firmware update started. Device is restarting or temporarily offline."
                }
                return@repeat
            }

            val data = response.dataObject() ?: return@repeat
            val phase = data.optString("otaPhase").ifBlank {
                data.optString("phase")
            }
            val restartScheduled = data.optBoolean("restartScheduled", false) ||
                data.optBoolean("restartRequired", false)
            val progress = data.optDouble("otaProgressPercent", -1.0)
                .toInt()
                .takeIf { percent -> percent in 0..100 }

            onProgress(
                FirmwareUpdateProgress(
                    stage = stageForPhase(phase, restartScheduled),
                    message = progressMessageForPhase(phase, progress, restartScheduled),
                    progressPercent = progress,
                    phase = phase
                )
            )

            if (restartScheduled) {
                return "Firmware written successfully. Device is restarting."
            }

            if (phase.equals("completed", ignoreCase = true) ||
                phase.equals("success", ignoreCase = true) ||
                phase.equals("succeeded", ignoreCase = true)
            ) {
                return "Firmware update completed. Device is restarting."
            }

            if (phase.equals("failed", ignoreCase = true) ||
                phase.equals("error", ignoreCase = true)
            ) {
                val error = data.optString("lastError").ifBlank {
                    "Firmware update failed on device"
                }
                return error
            }

            if ((progress ?: -1) >= 100) {
                return "Firmware download completed. Device is applying update."
            }
        }

        return null
    }

    private suspend fun waitForUpdatedFirmware(
        baseUrl: String,
        token: String?,
        targetVersion: String,
        onProgress: suspend (FirmwareUpdateProgress) -> Unit
    ): String? {
        onProgress(
            FirmwareUpdateProgress(
                stage = FirmwareUpdateStage.RESTARTING,
                message = "Device is restarting...",
                progressPercent = 100
            )
        )

        repeat(FIRMWARE_REBOOT_POLL_COUNT) {
            delay(FIRMWARE_REBOOT_POLL_INTERVAL_MS)
            val response = getJson(
                url = "$baseUrl/api/v1/firmware/status",
                token = token,
                connectTimeoutMillis = DEVICE_CONNECT_TIMEOUT_MS,
                readTimeoutMillis = DEVICE_READ_TIMEOUT_MS
            )

            if (!response.isSuccessful) {
                return@repeat
            }

            val data = response.dataObject() ?: return@repeat
            val version = data.optString("firmwareVersion").ifBlank {
                data.optString("version")
            }

            if (version.versionEquals(targetVersion)) {
                val message = "Firmware update completed. New version: $targetVersion"
                onProgress(
                    FirmwareUpdateProgress(
                        stage = FirmwareUpdateStage.COMPLETED,
                        message = message,
                        progressPercent = 100
                    )
                )
                return message
            }

            if (version.isNotBlank()) {
                onProgress(
                    FirmwareUpdateProgress(
                        stage = FirmwareUpdateStage.RECONNECTING,
                        message = "Device is back online. Verifying firmware version...",
                        progressPercent = 100
                    )
                )
            }
        }

        return null
    }

    private fun stageForPhase(
        phase: String,
        restartScheduled: Boolean
    ): FirmwareUpdateStage {
        if (restartScheduled) {
            return FirmwareUpdateStage.RESTARTING
        }

        return when (phase.trim().lowercase()) {
            "starting", "safemode", "safe_mode", "safe-mode" -> FirmwareUpdateStage.PREPARING
            "downloading", "download" -> FirmwareUpdateStage.DOWNLOADING
            "writing", "write", "flashing" -> FirmwareUpdateStage.WRITING
            "verifying", "verify" -> FirmwareUpdateStage.VERIFYING
            "succeeded", "success", "completed" -> FirmwareUpdateStage.RESTARTING
            "failed", "error" -> FirmwareUpdateStage.FAILED
            else -> FirmwareUpdateStage.PREPARING
        }
    }

    private fun progressMessageForPhase(
        phase: String,
        progressPercent: Int?,
        restartScheduled: Boolean
    ): String {
        if (restartScheduled) {
            return "Device is restarting..."
        }

        val percentSuffix = progressPercent?.let { percent -> " %$percent" }.orEmpty()
        return when (phase.trim().lowercase()) {
            "downloading", "download" -> "Firmware is downloading...$percentSuffix"
            "writing", "write", "flashing" -> "Firmware is writing...$percentSuffix"
            "verifying", "verify" -> "Verifying firmware..."
            "succeeded", "success", "completed" -> "Device is restarting..."
            "failed", "error" -> "Firmware update failed"
            "safemode", "safe_mode", "safe-mode" -> "Preparing device safe mode..."
            "starting" -> "Firmware update is starting..."
            else -> "Firmware update is running...$percentSuffix"
        }
    }

    private suspend fun getJson(
        url: String,
        token: String?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int
    ): HttpTextResponse = requestText(
        method = "GET",
        url = url,
        body = null,
        token = token,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis
    )

    private suspend fun postJson(
        url: String,
        body: String,
        token: String?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int
    ): HttpTextResponse = requestText(
        method = "POST",
        url = url,
        body = body,
        token = token,
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis
    )

    private suspend fun requestText(
        method: String,
        url: String,
        body: String?,
        token: String?,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int
    ): HttpTextResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json,text/json,text/plain,*/*")
            connection.setRequestProperty("Connection", "close")

            token?.takeIf { value -> value.isNotBlank() }?.let { value ->
                connection.setRequestProperty("X-AquaLight-Device-Token", value)
                connection.setRequestProperty("Authorization", "Bearer $value")
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                BufferedWriter(
                    OutputStreamWriter(
                        connection.outputStream,
                        Charsets.UTF_8
                    )
                ).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            val text = runCatching {
                connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { reader -> reader.readText() }
            }.getOrElse {
                connection.errorStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
            }

            HttpTextResponse(
                statusCode = statusCode,
                body = text
            )
        } catch (exception: java.net.SocketTimeoutException) {
            HttpTextResponse(
                statusCode = 0,
                body = null,
                exception = exception,
                timeout = true
            )
        } catch (exception: Exception) {
            HttpTextResponse(
                statusCode = 0,
                body = null,
                exception = exception
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun HttpTextResponse.dataObject(): JSONObject? {
        return try {
            val root = JSONObject(body.orEmpty())
            root.optJSONObject("data") ?: root
        } catch (_: Exception) {
            null
        }
    }

    private fun HttpTextResponse.errorCode(): ApiErrorCode {
        return when {
            timeout -> ApiErrorCode.TIMEOUT
            statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> ApiErrorCode.INVALID_REQUEST
            statusCode == 0 -> ApiErrorCode.NETWORK
            else -> ApiErrorCode.NETWORK
        }
    }

    private fun HttpTextResponse.errorMessage(
        fallback: String
    ): String {
        val apiMessage = runCatching {
            val root = JSONObject(body.orEmpty())
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
        }.getOrNull().orEmpty()

        if (apiMessage.isNotBlank()) {
            return apiMessage
        }

        exception?.message?.takeIf { message -> message.isNotBlank() }?.let { message ->
            return message
        }

        return if (statusCode > 0) {
            "$fallback (HTTP $statusCode)"
        } else {
            fallback
        }
    }

    private fun AquaLightOtaManifest.findCompatibleArtifact(
        device: DevicesDataStoreManager.DeviceInfo
    ): AquaLightFirmwareArtifact? {
        val deviceProductKey = device.productKey.storageKey.normalized()
        val deviceProductId = device.productId.normalized()
        val deviceSku = device.skuCode.normalized()
        val deviceHardware = device.hardwareRevision.normalized()

        return artifacts.firstOrNull { artifact ->
            artifact.productKey.normalized() == deviceProductKey &&
                hardwareMatches(deviceHardware, artifact.hardwareRevision.normalized())
        } ?: artifacts.firstOrNull { artifact ->
            deviceProductId.isNotBlank() &&
                artifact.productId.normalized() == deviceProductId &&
                hardwareMatches(deviceHardware, artifact.hardwareRevision.normalized())
        } ?: artifacts.firstOrNull { artifact ->
            deviceSku.isNotBlank() &&
                artifact.skuCode.normalized() == deviceSku &&
                hardwareMatches(deviceHardware, artifact.hardwareRevision.normalized())
        }
    }

    private fun hardwareMatches(
        deviceHardware: String,
        artifactHardware: String
    ): Boolean {
        if (deviceHardware.isBlank() || artifactHardware.isBlank()) {
            return true
        }
        return deviceHardware == artifactHardware
    }

    private fun isNewerVersion(
        candidate: String,
        current: String
    ): Boolean {
        val candidateParts = candidate.versionParts()
        val currentParts = current.versionParts()
        if (candidateParts.isEmpty()) {
            return false
        }
        if (currentParts.isEmpty()) {
            return true
        }

        val max = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until max) {
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) {
                return left > right
            }
        }
        return false
    }

    private fun String.versionParts(): List<Int> {
        return trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .mapNotNull { part -> part.toIntOrNull() }
    }

    private fun String.versionEquals(
        other: String
    ): Boolean {
        val left = versionParts()
        val right = other.versionParts()
        return left.isNotEmpty() && right.isNotEmpty() && left == right
    }

    private fun String.normalized(): String {
        return trim().lowercase()
    }

    private fun String?.orNonBlank(): String? {
        return this?.trim()?.takeIf { value -> value.isNotEmpty() }
    }


    data class FirmwareUpdateCheckResult(
        val version: String,
        val currentVersion: String,
        val alreadyUpToDate: Boolean,
        val size: Long,
        val artifact: AquaLightFirmwareArtifact
    )

    data class FirmwareUpdateStartResult(
        val version: String,
        val alreadyUpToDate: Boolean,
        val message: String
    )

    data class FirmwareUpdateProgress(
        val stage: FirmwareUpdateStage,
        val message: String,
        val progressPercent: Int? = null,
        val phase: String = ""
    )

    enum class FirmwareUpdateStage {
        PREPARING,
        DOWNLOADING,
        WRITING,
        VERIFYING,
        RESTARTING,
        RECONNECTING,
        COMPLETED,
        FAILED
    }

    data class AquaLightOtaManifest(
        val channel: String,
        val version: String,
        val artifacts: List<AquaLightFirmwareArtifact>
    )

    data class AquaLightFirmwareArtifact(
        val env: String,
        val productKey: String,
        val productId: String,
        val skuCode: String,
        val hardwareRevision: String,
        val version: String,
        val url: String,
        val sha256: String,
        val size: Long
    )

    private data class HttpTextResponse(
        val statusCode: Int,
        val body: String?,
        val exception: Exception? = null,
        val timeout: Boolean = false
    ) {
        val isSuccessful: Boolean
            get() = statusCode in 200..299
    }

    companion object {
        private const val DEFAULT_STABLE_MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/latest/download/manifest-stable.json"

        private const val INTERNET_CONNECT_TIMEOUT_MS = 10_000
        private const val INTERNET_READ_TIMEOUT_MS = 20_000
        private const val DEVICE_CONNECT_TIMEOUT_MS = 3_500
        private const val DEVICE_READ_TIMEOUT_MS = 6_000
        private const val DEVICE_OTA_START_READ_TIMEOUT_MS = 12_000
        private const val FIRMWARE_STATUS_POLL_COUNT = 30
        private const val FIRMWARE_STATUS_POLL_INTERVAL_MS = 2_000L
        private const val FIRMWARE_REBOOT_POLL_COUNT = 30
        private const val FIRMWARE_REBOOT_POLL_INTERVAL_MS = 2_000L
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
    }
}
