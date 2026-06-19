package com.aqua.aqualight.data.devices.api.timer

import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.v1.V1Endpoint
import com.aqua.aqualight.data.devices.api.v1.V1HttpClient
import com.aqua.aqualight.data.devices.api.v1.V1JsonParser
import org.json.JSONArray
import org.json.JSONObject

class V1TimerApi(
    private val client: V1HttpClient
) : TimerApi {

    override suspend fun readStatus(
        connection: AquaDeviceConnection
    ): ApiResult<TimerStatus> {
        return when (val result = client.get(connection, V1Endpoint.TIMER_STATUS)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid timer status response")
                ApiResult.success(parseStatus(data))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun readSchedules(
        connection: AquaDeviceConnection
    ): ApiResult<List<TimerSchedule>> {
        return when (val result = client.get(connection, V1Endpoint.TIMER_SCHEDULES)) {
            is ApiResult.Success -> {
                val data = V1JsonParser.envelopeData(result.value)
                    ?: return ApiResult.failure(ApiErrorCode.INVALID_RESPONSE, "Invalid timer schedules response")
                ApiResult.success(parseSchedules(data.optJSONArray("schedules") ?: JSONArray()))
            }
            is ApiResult.Error -> result
        }
    }

    override suspend fun writeSchedule(
        connection: AquaDeviceConnection,
        schedule: TimerSchedule
    ): ApiResult<Unit> {
        val durationMs = ((schedule.endMinute - schedule.startMinute).coerceAtLeast(0) * 60_000L)
        val body = JSONObject().put(
            "data",
            JSONObject()
                .put("name", schedule.id.ifBlank { "Timer ${schedule.channelIndex + 1}" })
                .put("enabled", schedule.enabled)
                .put("channelIndex", schedule.channelIndex)
                .put("startTimeMs", schedule.startMinute.coerceIn(0, 1439) * 60_000L)
                .put("intervalOnMs", durationMs)
                .put("intervalOffMs", 0)
                .put("repeatCount", 1)
                .put("weekdays", weekdaysArray(schedule.repeatDays))
                .put("save", true)
        ).toString()

        return when (val result = client.post(connection, V1Endpoint.TIMER_SCHEDULES, body)) {
            is ApiResult.Success -> ApiResult.success(Unit)
            is ApiResult.Error -> result
        }
    }

    private fun parseStatus(data: JSONObject): TimerStatus {
        val active = data.optBoolean("active", false)
        val activeChannel = data.optNullableInt("activeChannel")
        val mode = data.optString("mode", "").ifBlank { if (active) "active" else "off" }
        val scheduleCount = data.optInt("enabledScheduleCount", data.optInt("scheduleCount", 0))
        return TimerStatus(
            isRunning = active,
            activeChannel = activeChannel,
            nextEventText = if (scheduleCount > 0) "$mode · $scheduleCount schedule" else mode
        )
    }

    private fun parseSchedules(array: JSONArray): List<TimerSchedule> {
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val startMinute = ((item.optLong("startTimeMs", 0L) / 60_000L).toInt()).coerceIn(0, 1439)
                val intervalOnMinute = ((item.optLong("intervalOnMs", 0L) / 60_000L).toInt()).coerceAtLeast(0)
                add(
                    TimerSchedule(
                        id = item.optInt("index", item.optInt("listIndex", i)).toString(),
                        channelIndex = item.optNullableInt("channelIndex") ?: 0,
                        enabled = item.optBoolean("enabled", false),
                        startMinute = startMinute,
                        endMinute = (startMinute + intervalOnMinute).coerceIn(0, 1440),
                        repeatDays = item.optJSONArray("weekdays").toDaySet()
                    )
                )
            }
        }
    }

    private fun weekdaysArray(days: Set<Int>): JSONArray {
        val array = JSONArray()
        for (day in 0..6) {
            array.put(days.isEmpty() || days.contains(day))
        }
        return array
    }

    private fun JSONArray?.toDaySet(): Set<Int> {
        if (this == null) return emptySet()
        return buildSet {
            for (i in 0 until length().coerceAtMost(7)) {
                if (optBoolean(i, false)) add(i)
            }
        }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return runCatching { getInt(key) }.getOrNull()
            ?: optString(key, "").trim().toIntOrNull()
    }
}
