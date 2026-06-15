package com.aqua.aqualight.data.devices.api.light

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.legacy.LegacyEndpoint
import com.aqua.aqualight.data.devices.api.legacy.LegacyHttpClient
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import org.json.JSONObject

class LegacyLightApi(
    private val client: LegacyHttpClient,
    private val parser: LegacyLightPayloadParser = LegacyLightPayloadParser()
) : LightApi {

    override suspend fun readDeviceState(
        connection: AquaDeviceConnection
    ): ApiResult<LightDeviceState> {
        return when (val response = client.get(
            connection = connection,
            endpoint = LegacyEndpoint.GET,
            query = buildFullStateQuery()
        )) {
            is ApiResult.Success -> parser.parseDeviceState(response.value)
            is ApiResult.Error -> response
        }
    }

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<LightStatus> {
        return when (val state = readDeviceState(connection)) {
            is ApiResult.Success -> ApiResult.success(state.value.status)
            is ApiResult.Error -> state
        }
    }

    override suspend fun readPrograms(
        connection: AquaDeviceConnection
    ): ApiResult<List<LightProgram>> {
        return when (val state = readDeviceState(connection)) {
            is ApiResult.Success -> ApiResult.success(
                buildLegacyProgramSummary(state.value)
            )
            is ApiResult.Error -> state
        }
    }

    override suspend fun writeProgram(
        connection: AquaDeviceConnection,
        program: LightProgram
    ): ApiResult<Unit> {
        return ApiResult.failure(
            code = ApiErrorCode.UNSUPPORTED_FIRMWARE,
            message = "Legacy light program write is intentionally not enabled in the data layer yet"
        )
    }

    override suspend fun setManual(
        connection: AquaDeviceConnection,
        request: LightManualRequest
    ): ApiResult<Unit> {
        return sendLegacySet(
            connection = connection,
            json = buildManualJson(request)
        )
    }

    override suspend fun resumeAuto(
        connection: AquaDeviceConnection
    ): ApiResult<Unit> {
        return sendLegacySet(
            connection = connection,
            json = buildResumeAutoJson()
        )
    }

    override suspend fun setAutomation(
        connection: AquaDeviceConnection,
        request: LightAutomationRequest
    ): ApiResult<Unit> {
        return ApiResult.failure(
            code = ApiErrorCode.UNSUPPORTED_FIRMWARE,
            message = "Legacy light automation write is intentionally not enabled in the data layer yet"
        )
    }

    override suspend fun setThermalProtection(
        connection: AquaDeviceConnection,
        request: LightThermalProtectionRequest
    ): ApiResult<Unit> {
        val json = JSONObject()
        var hasUpdate = false

        request.limitTemperatureCelsius?.let { value ->
            val sensorIndexes = request.sensorIndexes
                .filter { index -> index >= 0 }
                .distinct()
                .sorted()

            if (sensorIndexes.isEmpty()) {
                return ApiResult.failure(
                    code = ApiErrorCode.INVALID_REQUEST,
                    message = "No temperature sensor is available for thermal limit"
                )
            }

            val data = JSONObject().apply {
                sensorIndexes.forEach { index ->
                    put(
                        index.toString(),
                        JSONObject().apply {
                            put("TempLightErr", value.coerceIn(MIN_TEMPERATURE_CELSIUS, MAX_TEMPERATURE_CELSIUS))
                        }
                    )
                }
            }

            json.put(
                KEY_TEMPERATURE,
                JSONObject().apply {
                    put("Data", data)
                }
            )
            hasUpdate = true
        }

        val lightSettings = JSONObject()
        request.lightReductionPercent?.let { value ->
            lightSettings.put(
                "LightDownErr",
                value.coerceIn(MIN_PERCENT, MAX_PERCENT)
            )
            hasUpdate = true
        }
        request.recoveryIntervalSeconds?.let { value ->
            lightSettings.put(
                "TimeDownErr",
                value.coerceIn(MIN_RECOVERY_SECONDS, MAX_RECOVERY_SECONDS)
            )
            hasUpdate = true
        }

        if (lightSettings.length() > 0) {
            json.put(KEY_LIGHT_PROGRAM, lightSettings)
        }

        if (!hasUpdate) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "No thermal protection setting was provided"
            )
        }

        return sendLegacySet(
            connection = connection,
            json = json
        )
    }

    override suspend fun setCoolingController(
        connection: AquaDeviceConnection,
        request: LightCoolingControllerRequest
    ): ApiResult<Unit> {
        if (request.controllerIndex < 0) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Cooling controller is missing"
            )
        }

        val controllerSettings = JSONObject()
        request.enabled?.let { enabled ->
            controllerSettings.put("Enabled", enabled)
        }
        request.fanStartTemperatureCelsius?.let { value ->
            controllerSettings.put(
                "TMin",
                value.coerceIn(MIN_TEMPERATURE_CELSIUS, MAX_TEMPERATURE_CELSIUS)
            )
        }
        request.fanFullSpeedTemperatureCelsius?.let { value ->
            controllerSettings.put(
                "TMax",
                value.coerceIn(MIN_TEMPERATURE_CELSIUS, MAX_TEMPERATURE_CELSIUS)
            )
        }

        if (controllerSettings.length() == 0) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "No cooling controller setting was provided"
            )
        }

        val start = request.fanStartTemperatureCelsius
        val full = request.fanFullSpeedTemperatureCelsius
        if (start != null && full != null && full <= start) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Fan full speed temperature must be higher than fan start temperature"
            )
        }

        val json = JSONObject().apply {
            put(
                KEY_COOLING,
                JSONObject().apply {
                    put(
                        "Data",
                        JSONObject().apply {
                            put(request.controllerIndex.toString(), controllerSettings)
                        }
                    )
                }
            )
        }

        return sendLegacySet(
            connection = connection,
            json = json
        )
    }

    override suspend fun syncTime(
        connection: AquaDeviceConnection,
        request: LightTimeSyncRequest
    ): ApiResult<Unit> {
        val setTime = JSONObject().apply {
            put("Y", request.year.coerceIn(MIN_YEAR, MAX_YEAR))
            put("Mn", request.month.coerceIn(1, 12))
            put("D", request.day.coerceIn(1, 31))
            put("WD", request.weekDay.coerceIn(1, 7))
            put("H", request.hour.coerceIn(0, 23))
            put("M", request.minute.coerceIn(0, 59))
            put("S", request.second.coerceIn(0, 59))
        }

        val json = JSONObject().apply {
            put(
                KEY_TIME,
                JSONObject().apply {
                    put("SetTime", setTime)
                }
            )
        }

        return sendLegacySet(
            connection = connection,
            json = json
        )
    }

    private suspend fun sendLegacySet(
        connection: AquaDeviceConnection,
        json: JSONObject
    ): ApiResult<Unit> {
        return when (val response = client.set(
            connection = connection,
            command = buildSetCommand(json)
        )) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> response
        }
    }

    private fun buildFullStateQuery(): Map<String, String> {
        val request = JSONObject().apply {
            put(KEY_TIME, allObject())
            put(KEY_TEMPERATURE, allObject())
            put(KEY_LED_PWM, allObject())
            put(KEY_FAN_PWM, allObject())
            put(KEY_LIGHT_PROGRAM, allObject())
            put(KEY_COOLING, allObject())
        }

        return mapOf(
            PARAM_JSON to request.toString(),
            PARAM_RETURN to returnObject().toString()
        )
    }

    private fun buildManualJson(
        request: LightManualRequest
    ): JSONObject {
        val values = request.channelValues.normalized()
        val data = JSONObject().apply {
            put(
                LEGACY_WHITE_INDEX.toString(),
                manualChannelObject(
                    percent = if (request.powerOn) values.white else 0
                )
            )
            put(
                LEGACY_RED_INDEX.toString(),
                manualChannelObject(
                    percent = if (request.powerOn) values.red else 0
                )
            )
            put(
                LEGACY_GREEN_INDEX.toString(),
                manualChannelObject(
                    percent = if (request.powerOn) values.green else 0
                )
            )
            put(
                LEGACY_BLUE_INDEX.toString(),
                manualChannelObject(
                    percent = if (request.powerOn) values.blue else 0
                )
            )
        }

        return JSONObject().apply {
            put(
                KEY_LED_PWM,
                JSONObject().apply {
                    put("Data", data)
                }
            )
        }
    }

    private fun buildResumeAutoJson(): JSONObject {
        val data = JSONObject().apply {
            listOf(
                LEGACY_WHITE_INDEX,
                LEGACY_RED_INDEX,
                LEGACY_GREEN_INDEX,
                LEGACY_BLUE_INDEX
            ).forEach { index ->
                put(
                    index.toString(),
                    resumeAutoChannelObject()
                )
            }
        }

        return JSONObject().apply {
            put(
                KEY_LED_PWM,
                JSONObject().apply {
                    put("Data", data)
                }
            )
        }
    }

    private fun buildSetCommand(
        json: JSONObject
    ): String {
        return buildString {
            append(PARAM_JSON)
            append('=')
            append(json.toString())
            append('&')
            append(PARAM_RETURN)
            append('=')
            append(returnObject().toString())
        }
    }

    private fun manualChannelObject(
        percent: Int
    ): JSONObject {
        return JSONObject().apply {
            put(
                "VManual",
                JSONObject().apply {
                    put("V", LightApiMath.percentToDeviceValue(percent))
                    put("TOffMs", MANUAL_OVERRIDE_TIMEOUT_MILLIS)
                }
            )
        }
    }

    private fun resumeAutoChannelObject(): JSONObject {
        return JSONObject().apply {
            put(
                "VManual",
                JSONObject().apply {
                    put("V", MANUAL_RESUME_VALUE)
                    put("TOffMs", 0)
                }
            )
        }
    }

    private fun buildLegacyProgramSummary(
        state: LightDeviceState
    ): List<LightProgram> {
        if (state.scheduleChannels.isEmpty()) {
            return emptyList()
        }

        val points = state.scheduleChannels.flatMap { channel ->
            channel.points
        }

        if (points.isEmpty()) {
            return emptyList()
        }

        val start = points.minOf { point -> point.minuteOfDay }
        val end = points.maxOf { point -> point.minuteOfDay }
        val peakPoint = points.maxByOrNull { point -> point.percent }
        val peak = peakPoint?.minuteOfDay ?: start

        return listOf(
            LightProgram(
                id = LEGACY_PROGRAM_ID,
                name = LEGACY_PROGRAM_NAME,
                isActive = true,
                startMinute = start,
                peakStartMinute = peak,
                peakEndMinute = peak,
                endMinute = end,
                channelValues = state.channels,
                repeatDays = LEGACY_ALL_DAYS
            )
        )
    }

    private fun allObject(): JSONObject {
        return JSONObject().apply {
            put("All", 0)
        }
    }

    private fun returnObject(): JSONObject {
        return JSONObject().apply {
            put("iPostCount", System.currentTimeMillis() % 100000)
        }
    }

    private companion object {
        const val KEY_TIME = "Time"
        const val KEY_TEMPERATURE = "LTemperature"
        const val KEY_LED_PWM = "LPWMChanelLED"
        const val KEY_FAN_PWM = "LPWMChanelFan"
        const val KEY_LIGHT_PROGRAM = "LLight"
        const val KEY_COOLING = "LCool"
        const val PARAM_JSON = "Json"
        const val PARAM_RETURN = "sRet"
        const val LEGACY_WHITE_INDEX = 0
        const val LEGACY_RED_INDEX = 1
        const val LEGACY_GREEN_INDEX = 2
        const val LEGACY_BLUE_INDEX = 3
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
        const val MIN_TEMPERATURE_CELSIUS = 0
        const val MAX_TEMPERATURE_CELSIUS = 100
        const val MIN_RECOVERY_SECONDS = 1
        const val MAX_RECOVERY_SECONDS = 3600
        const val MIN_YEAR = 2020
        const val MAX_YEAR = 2099
        const val MANUAL_OVERRIDE_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000
        const val MANUAL_RESUME_VALUE = -1.0
        const val LEGACY_PROGRAM_ID = "legacy-light-schedule"
        const val LEGACY_PROGRAM_NAME = "Legacy light schedule"
        val LEGACY_ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
