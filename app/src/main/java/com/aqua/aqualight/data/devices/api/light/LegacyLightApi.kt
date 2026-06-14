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
        val command = buildManualCommand(request)
        return when (val response = client.set(
            connection = connection,
            command = command
        )) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> response
        }
    }


    override suspend fun resumeAuto(
        connection: AquaDeviceConnection
    ): ApiResult<Unit> {
        val command = buildResumeAutoCommand()
        return when (val response = client.set(
            connection = connection,
            command = command
        )) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> response
        }
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

    private fun buildManualCommand(
        request: LightManualRequest
    ): String {
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

        val json = JSONObject().apply {
            put(
                KEY_LED_PWM,
                JSONObject().apply {
                    put("Data", data)
                }
            )
        }

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


    private fun buildResumeAutoCommand(): String {
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

        val json = JSONObject().apply {
            put(
                KEY_LED_PWM,
                JSONObject().apply {
                    put("Data", data)
                }
            )
        }

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
        const val MANUAL_OVERRIDE_TIMEOUT_MILLIS = 24 * 60 * 60 * 1000
        const val MANUAL_RESUME_VALUE = -1.0
        const val LEGACY_PROGRAM_ID = "legacy-light-schedule"
        const val LEGACY_PROGRAM_NAME = "Legacy light schedule"
        val LEGACY_ALL_DAYS = setOf(1, 2, 3, 4, 5, 6, 7)
    }
}
