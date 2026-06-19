package com.aqua.aqualight.data.devices.api.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1Endpoint
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.api.v1.V1JsonParser
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class V1LightApi(
    private val client: V1HttpClient
) : LightApi {

    override suspend fun readDeviceState(connection: AquaDeviceConnection): ApiResult<LightDeviceState> {
        val statusResult = readStatus(connection)
        if (statusResult is ApiResult.Error) return statusResult

        val channelsResult = client.get(connection, V1Endpoint.LIGHT_CHANNELS)
        if (channelsResult is ApiResult.Error) return channelsResult

        val channelsData = V1JsonParser.envelopeData((channelsResult as ApiResult.Success).value)
            ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid light channels response")

        val ledChannels = parsePwmChannels(channelsData.optJSONArray("channels") ?: JSONArray())
        val values = channelsToValues(ledChannels)
        val status = (statusResult as ApiResult.Success).value.copy(
            redPercent = values.red,
            greenPercent = values.green,
            bluePercent = values.blue,
            whitePercent = values.white,
            outputPercent = values.maxPercent
        )

        return ApiResult.success(
            LightDeviceState(
                status = status,
                channels = values,
                ledChannels = ledChannels
            )
        )
    }

    override suspend fun readStatus(connection: AquaDeviceConnection): ApiResult<LightStatus> {
        return when (val result = client.get(connection, V1Endpoint.LIGHT_STATUS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid light status response")
                ApiResult.success(parseStatus(data))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun readPrograms(connection: AquaDeviceConnection): ApiResult<List<LightProgram>> {
        return when (val result = client.get(connection, V1Endpoint.LIGHT_PROGRAMS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid light programs response")
                ApiResult.success(parsePrograms(data.optJSONArray("programs") ?: JSONArray()))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun writeProgram(connection: AquaDeviceConnection, program: LightProgram): ApiResult<Unit> {
        val points = JSONArray()
        points.put(JSONObject().put("timeMs", program.startMinute * 60_000L).put("percent", 0))
        points.put(JSONObject().put("timeMs", program.peakStartMinute * 60_000L).put("percent", program.channelValues.maxPercent))
        points.put(JSONObject().put("timeMs", program.peakEndMinute * 60_000L).put("percent", program.channelValues.maxPercent))
        points.put(JSONObject().put("timeMs", program.endMinute * 60_000L).put("percent", 0))
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("channelIndex", 0)
                .put("points", points)
                .put("save", true)
        ).toString()

        return when (val result = client.post(connection, V1Endpoint.LIGHT_PROGRAMS, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    override suspend fun setManual(connection: AquaDeviceConnection, request: LightManualRequest): ApiResult<Unit> {
        val values = request.channelValues.normalized()
        val perChannel = listOf(
            0 to values.white,
            1 to values.red,
            2 to values.green,
            3 to values.blue
        )

        perChannel.forEach { (channelIndex, percent) ->
            val data = if (request.powerOn) {
                JSONObject()
                    .put("channelIndex", channelIndex)
                    .put("percent", percent)
                    .put("durationMs", 24 * 60 * 60 * 1000)
            } else {
                JSONObject()
                    .put("channelIndex", channelIndex)
                    .put("clear", true)
            }
            when (val result = client.post(connection, V1Endpoint.LIGHT_MANUAL, JSONObject().put("data", data).toString())) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> return result
            }
        }

        return ApiResult.success(Unit)
    }

    override suspend fun resumeAuto(connection: AquaDeviceConnection): ApiResult<Unit> {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("all", true)
                .put("clear", true)
        ).toString()
        return when (val result = client.post(connection, V1Endpoint.LIGHT_MANUAL, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    override suspend fun setAutomation(connection: AquaDeviceConnection, request: LightAutomationRequest): ApiResult<Unit> =
        ApiResult.success(Unit)

    override suspend fun setThermalProtection(connection: AquaDeviceConnection, request: LightThermalProtectionRequest): ApiResult<Unit> =
        ApiResult.failure(ApiErrorCode.UNSUPPORTED_FIRMWARE, "Thermal protection is profile-managed by AquaLight V1 firmware")

    override suspend fun setCoolingController(connection: AquaDeviceConnection, request: LightCoolingControllerRequest): ApiResult<Unit> =
        ApiResult.failure(ApiErrorCode.UNSUPPORTED_FIRMWARE, "Light cooling controller is managed through /api/v1/cooling")

    override suspend fun syncTime(connection: AquaDeviceConnection, request: LightTimeSyncRequest): ApiResult<Unit> {
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("year", request.year)
                .put("month", request.month)
                .put("day", request.day)
                .put("weekDay", request.weekDay)
                .put("hour", request.hour)
                .put("minute", request.minute)
                .put("second", request.second)
        ).toString()
        return when (val result = client.post(connection, V1Endpoint.TIME_SYNC, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    private fun parseStatus(data: JSONObject): LightStatus {
        return LightStatus(
            mode = parseMode(data.optString("mode", "")),
            isPowerOn = data.optString("mode", "").equals("off", ignoreCase = true).not(),
            outputPercent = data.optNullableDouble("estimatedPowerNowW")?.roundToInt() ?: 0,
            currentWatt = data.optNullableDouble("estimatedPowerNowW"),
            maxWatt = data.optNullableDouble("powerLimitW"),
            powerLoadPercent = LightApiMath.powerLoadPercent(
                currentWatt = data.optNullableDouble("estimatedPowerNowW"),
                maxWatt = data.optNullableDouble("powerLimitW")
            ),
            thermalReductionPercent = data.optJSONObject("temperatureProtection")
                ?.optNullableDouble("dimmingFactor")
                ?.let { ((1.0 - it) * 100.0).roundToInt().coerceIn(0, 100) }
        )
    }

    private fun parsePwmChannels(array: JSONArray): List<LightPwmChannelState> {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val runtime = item.optJSONObject("runtime") ?: JSONObject()
                val calibration = item.optJSONObject("calibration") ?: JSONObject()
                val hardware = item.optJSONObject("hardware") ?: JSONObject()
                val percentNow = runtime.optNullableDouble("percentNow")?.roundToInt()?.coerceIn(0, 100)
                add(
                    LightPwmChannelState(
                        index = item.optInt("index", item.optInt("listIndex", i)),
                        name = item.optString("name", ""),
                        role = parseRole(item.optString("channelKey", item.optString("name", ""))),
                        regime = parseRegime(item.optString("regime", "")),
                        gpioPwm = hardware.optString("gpioLedcChannel", ""),
                        color = if (item.has("color") && !item.isNull("color")) item.optLong("color") else null,
                        lumen = calibration.optNullableDouble("lumens"),
                        lux = calibration.optNullableDouble("lux"),
                        maxWatt = calibration.optNullableDouble("watts"),
                        group = item.optNullableInt("group"),
                        currentValue = runtime.optNullableDouble("valueNow"),
                        currentPercent = percentNow,
                        minValue = calibration.optNullableDouble("valueMin"),
                        maxValue = calibration.optNullableDouble("valueMax"),
                        isInverted = hardware.optBoolean("inverted", false),
                        frequency = hardware.optNullableInt("frequencyHz")
                    )
                )
            }
        }
    }

    private fun channelsToValues(channels: List<LightPwmChannelState>): LightChannelValues {
        fun value(role: LightChannelRole): Int = channels.firstOrNull { it.role == role }?.currentPercent ?: 0
        return LightChannelValues(
            red = value(LightChannelRole.RED),
            green = value(LightChannelRole.GREEN),
            blue = value(LightChannelRole.BLUE),
            white = value(LightChannelRole.WHITE)
        )
    }

    private fun parsePrograms(array: JSONArray): List<LightProgram> {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val points = item.optJSONArray("points") ?: JSONArray()
                val first = points.optJSONObject(0)
                val last = points.optJSONObject(points.length() - 1)
                add(
                    LightProgram(
                        id = item.optInt("index", i).toString(),
                        name = item.optString("channelName", "Light program ${i + 1}"),
                        isActive = true,
                        startMinute = ((first?.optLong("timeMs", 0L) ?: 0L) / 60_000L).toInt(),
                        peakStartMinute = ((first?.optLong("timeMs", 0L) ?: 0L) / 60_000L).toInt(),
                        peakEndMinute = ((last?.optLong("timeMs", 0L) ?: 0L) / 60_000L).toInt(),
                        endMinute = ((last?.optLong("timeMs", 0L) ?: 0L) / 60_000L).toInt(),
                        channelValues = LightChannelValues()
                    )
                )
            }
        }
    }

    private fun parseMode(value: String): LightMode = when (value.lowercase()) {
        "manual" -> LightMode.MANUAL
        "program", "auto" -> LightMode.AUTO
        "on" -> LightMode.MANUAL
        "off" -> LightMode.IDLE
        else -> LightMode.UNKNOWN
    }

    private fun parseRole(value: String): LightChannelRole {
        val normalized = value.lowercase()
        return when {
            normalized.contains("red") || normalized == "r" -> LightChannelRole.RED
            normalized.contains("green") || normalized == "g" -> LightChannelRole.GREEN
            normalized.contains("blue") || normalized == "b" -> LightChannelRole.BLUE
            normalized.contains("white") || normalized == "w" -> LightChannelRole.WHITE
            normalized.contains("fan") -> LightChannelRole.FAN
            else -> LightChannelRole.UNKNOWN
        }
    }

    private fun parseRegime(value: String): LightPwmRegime = when (value.lowercase()) {
        "auto", "program" -> LightPwmRegime.AUTO
        "on" -> LightPwmRegime.ON
        "off" -> LightPwmRegime.OFF
        else -> LightPwmRegime.UNKNOWN
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getDouble(key) }.getOrNull()
            ?: optString(key, "").trim().toDoubleOrNull()
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
            ?: optString(key, "").trim().toIntOrNull()
    }
}
