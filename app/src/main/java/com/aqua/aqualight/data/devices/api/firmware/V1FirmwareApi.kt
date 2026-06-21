package com.aqua.aqualight.data.devices.api.firmware

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1Endpoint
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.api.v1.V1JsonParser
import org.json.JSONObject

class V1FirmwareApi(
    private val client: V1HttpClient
) : FirmwareApi {

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<FirmwareStatus> {
        return when (val result = client.get(connection, V1Endpoint.FIRMWARE_STATUS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid firmware status response")
                ApiResult.success(parseStatus(data))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun startOta(
        connection: AquaDeviceConnection,
        request: FirmwareOtaRequest
    ): ApiResult<FirmwareOtaResult> {
        if (connection.apiToken.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Secure OTA requires the device API token created during setup. Run device setup/pairing again before updating firmware."
            )
        }

        val data = JSONObject()
            .put("url", request.url)
            .put("version", request.version)
            .put("sha256", request.sha256)
            .put("size", request.size)
            .put("productKey", request.productKey)
            .put("productId", request.productId)
            .put("hardwareRevision", request.hardwareRevision)
            .put("startDownload", request.startDownload)
            .put("applyNow", request.applyNow)
            .put("allowInsecureHttp", request.allowInsecureHttp)

        val body = JSONObject()
            .put("data", data)
            .toString()

        return when (val result = client.post(connection, V1Endpoint.FIRMWARE_OTA, body)) {
            is ApiResult.Success -> {
                val response = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid firmware OTA response")
                ApiResult.success(parseOtaResult(response))
            }
            is ApiResult.Error -> result
        }
    }

    private fun parseStatus(data: JSONObject): FirmwareStatus {
        return FirmwareStatus(
            available = data.optBoolean("available", false),
            otaSupported = data.optBoolean("otaSupported", false),
            firmwareVersion = data.optString("firmwareVersion", ""),
            firmwareBuild = data.optString("firmwareBuild", ""),
            hardwareRevision = data.optString("hardwareRevision", ""),
            productKey = data.optString("productKey", ""),
            productId = data.optString("productId", ""),
            updateInProgress = data.optBoolean("updateInProgress", false),
            otaPhase = data.optString("otaPhase", ""),
            otaProgressPercent = data.optDouble("otaProgressPercent", 0.0),
            restartRequired = data.optBoolean("restartRequired", false),
            lastError = data.optString("lastError", "")
        )
    }

    private fun parseOtaResult(data: JSONObject): FirmwareOtaResult {
        return FirmwareOtaResult(
            accepted = data.optBoolean("accepted", false),
            operation = data.optString("operation", ""),
            pendingRequest = data.optBoolean("pendingRequest", false),
            downloadStarted = data.optBoolean("downloadStarted", false),
            flashWriteStarted = data.optBoolean("flashWriteStarted", false),
            restartRequired = data.optBoolean("restartRequired", false),
            message = data.optString("message", "")
        )
    }
}
