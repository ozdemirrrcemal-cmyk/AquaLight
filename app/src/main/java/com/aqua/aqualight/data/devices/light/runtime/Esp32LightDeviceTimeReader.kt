package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject
import java.util.Calendar

class Esp32LightDeviceTimeReader(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun readTime(
        ip: String
    ): Result<LightDeviceTimeState> {
        val queryJson = JSONObject()
            .put(
                "TimeL",
                JSONObject().put("All", 0)
            )
            .toString()

        val response = httpClient.getJson(
            ip = ip,
            json = queryJson,
            requestTag = "light_device_time"
        ).getOrElse { error ->
            return Result.failure(error)
        }

        return runCatching {
            parseTime(response)
        }
    }

    private fun parseTime(
        response: String
    ): LightDeviceTimeState {
        val root = JSONObject(response)

        val time = root.optJSONObject("TimeL")
            ?: throw IllegalStateException("Device time is missing")

        val year = time.optInt("Y", 0)
        val month = time.optInt("Mn", 0)
        val day = time.optInt("D", 0)
        val weekDay = time.optInt("WD", 0)
        val hour = time.optInt("H", -1)
        val minute = time.optInt("M", -1)
        val second = time.optInt("S", 0)

        if (
            year <= 0 ||
            month !in 1..12 ||
            day !in 1..31 ||
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            throw IllegalStateException("Device time is invalid")
        }

        return LightDeviceTimeState(
            year = year,
            month = month,
            day = day,
            weekDay = weekDay,
            hour = hour,
            minute = minute,
            second = second.coerceIn(0, 59),
            source = LightDeviceTimeState.Source.DEVICE
        )
    }

    companion object {

        fun phoneFallback(): LightDeviceTimeState {
            val calendar = Calendar.getInstance()

            return LightDeviceTimeState(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
                weekDay = calendar.get(Calendar.DAY_OF_WEEK),
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                second = calendar.get(Calendar.SECOND),
                source = LightDeviceTimeState.Source.PHONE_FALLBACK
            )
        }
    }
}