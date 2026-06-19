package com.aqua.aqualight.data.devices.api.cooling

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1Endpoint
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.api.v1.V1JsonParser
import org.json.JSONObject

class V1CoolingApi(
    private val client: V1HttpClient
) : CoolingApi {

    override suspend fun readStatus(connection: AquaDeviceConnection): ApiResult<CoolingStatus> {
        return when (val result = client.get(connection, V1Endpoint.COOLING_STATUS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid cooling status response")
                ApiResult.success(
                    CoolingStatus(
                        enabled = data.optBoolean("available", false) && data.optBoolean("configured", true),
                        fanCount = data.optInt("fanChannelCount", 0),
                        enabledFanCount = data.optInt("configuredFanCount", 0),
                        currentTemperatureCelsius = data.optNullableDouble("currentTemperatureC"),
                        fanStartTemperatureCelsius = data.optDouble("minTemperatureC", 30.0).toInt(),
                        fanFullSpeedTemperatureCelsius = data.optDouble("maxTemperatureC", 50.0).toInt()
                    )
                )
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun writeSettings(connection: AquaDeviceConnection, settings: CoolingSettings): ApiResult<Unit> {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("mode", if (settings.enabled) "auto" else "off")
                .put("minTemperatureC", settings.fanStartTemperatureCelsius)
                .put("maxTemperatureC", settings.fanFullSpeedTemperatureCelsius)
                .put("save", true)
        ).toString()
        return when (val result = client.put(connection, V1Endpoint.COOLING_CONFIG, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
            ?: optString(key, "").trim().toDoubleOrNull()
    }
}
