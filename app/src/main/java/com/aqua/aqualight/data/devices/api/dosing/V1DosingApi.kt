package com.aqua.aqualight.data.devices.api.dosing

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1Endpoint
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.api.v1.V1JsonParser
import org.json.JSONArray
import org.json.JSONObject

class V1DosingApi(
    private val client: V1HttpClient
) : DosingApi {

    override suspend fun readStatus(connection: AquaDeviceConnection): ApiResult<DosingStatus> {
        return when (val result = client.get(connection, V1Endpoint.DOSING_STATUS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid dosing status response")
                ApiResult.success(parseStatus(data))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun readChannels(connection: AquaDeviceConnection): ApiResult<List<DosingChannelStatus>> {
        return when (val result = client.get(connection, V1Endpoint.DOSING_CHANNELS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid dosing channels response")
                ApiResult.success(parseChannels(data.optJSONArray("channels") ?: JSONArray()))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun readSchedules(connection: AquaDeviceConnection): ApiResult<List<DosingSchedule>> {
        return when (val result = client.get(connection, V1Endpoint.DOSING_SCHEDULES)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid dosing schedules response")
                ApiResult.success(parseSchedules(data.optJSONArray("schedules") ?: JSONArray()))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: DosingSchedule
    ): ApiResult<Unit> {
        val firstRunAt = schedule.runAtMinutes.firstOrNull()?.coerceIn(0, 1439) ?: 0
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("name", schedule.id.ifBlank { "Dose ${schedule.channelIndex + 1}" })
                .put("enabled", schedule.enabled)
                .put("pumpIndex", schedule.channelIndex)
                .put("timeMs", firstRunAt * 60_000L)
                .put("weekdays", allWeekdays())
                .put("amountMl", schedule.doseMl)
                .put("doseCount", schedule.runAtMinutes.size.coerceAtLeast(1))
                .put("intervalBetweenDosesMs", intervalBetweenRunsMs(schedule.runAtMinutes))
                .put("save", true)
        ).toString()

        return when (val result = client.post(connection, V1Endpoint.DOSING_SCHEDULES, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    override suspend fun runCalibration(
        connection: AquaDeviceConnection,
        channelIndex: Int
    ): ApiResult<Unit> {
        return ApiResult.failure(
            code = ApiErrorCode.UNSUPPORTED_FIRMWARE,
            message = "Dosing calibration workflow requires the dedicated AquaLight V1 calibration screen"
        )
    }

    private fun parseStatus(data: JSONObject): DosingStatus {
        val active = data.optBoolean("active", false)
        val activeChannel = data.optNullableInt("activePumpIndex")
            ?: data.optNullableInt("activeChannel")
        val mode = data.optString("mode", "").ifBlank { if (active) "running" else "idle" }
        val scheduleCount = data.optInt("enabledScheduleCount", data.optInt("scheduleCount", 0))
        return DosingStatus(
            channelCount = data.optInt("pumpChannelCount", data.optInt("channelCount", 0)),
            activeChannel = activeChannel,
            nextDoseText = if (scheduleCount > 0) "$mode · $scheduleCount schedule" else mode
        )
    }

    private fun parseChannels(array: JSONArray): List<DosingChannelStatus> {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val calibration = item.optJSONObject("calibration") ?: JSONObject()
                val reservoir = item.optJSONObject("reservoir") ?: JSONObject()
                add(
                    DosingChannelStatus(
                        channelIndex = item.optInt("index", item.optInt("listIndex", i)),
                        enabled = item.optString("mode", "").equals("auto", ignoreCase = true) ||
                            item.optString("runtimeRegime", "").equals("auto", ignoreCase = true),
                        remainingVolumeMl = reservoir.optNullableDouble("remainingMl"),
                        calibrationMlPerSecond = calibration.optNullableDouble("doseMsPerMl")
                            ?.takeIf { it > 0.0 }
                            ?.let { 1000.0 / it }
                    )
                )
            }
        }
    }

    private fun parseSchedules(array: JSONArray): List<DosingSchedule> {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    DosingSchedule(
                        id = item.optInt("scheduleId", item.optInt("index", item.optInt("listIndex", i))).toString(),
                        channelIndex = item.optNullableInt("pumpIndex") ?: 0,
                        enabled = item.optBoolean("enabled", false),
                        doseMl = item.optNullableDouble("amountMl") ?: 0.0,
                        runAtMinutes = listOf(((item.optLong("timeMs", 0L) / 60_000L).toInt()).coerceIn(0, 1439))
                    )
                )
            }
        }
    }

    private fun allWeekdays(): JSONArray = JSONArray().apply {
        repeat(7) { put(true) }
    }

    private fun intervalBetweenRunsMs(runAtMinutes: List<Int>): Long {
        val sorted = runAtMinutes.map { it.coerceIn(0, 1439) }.sorted()
        if (sorted.size < 2) return 0L
        return ((sorted[1] - sorted[0]).coerceAtLeast(0) * 60_000L)
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
