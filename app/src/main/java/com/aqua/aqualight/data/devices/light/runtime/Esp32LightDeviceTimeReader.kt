package com.aqua.aqualight.data.devices.light.runtime

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Calendar

class Esp32LightDeviceTimeReader(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun readTime(
        ip: String
    ): Result<LightDeviceTimeState> {
        val queryJson = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put("TimeCurrent", 0)
                    .put("EnabledAutoSyncNTP", 0)
                    .put("EnabledAutoSyncGadget", 0)
                    .put("TimeZone", 0)
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
        val root = JSONObject(
            normalizeResponseJson(response)
        )

        val timeL = root.optJSONObject("TimeL")

        if (timeL != null) {
            return parseTimeLObject(timeL)
        }

        val time = root.optJSONObject("Time")
            ?: throw IllegalStateException("Device time is missing")

        val timeCurrent = time.optString(
            "TimeCurrent",
            ""
        )

        if (timeCurrent.isBlank()) {
            throw IllegalStateException("Device time is missing")
        }

        return parseTimeCurrentString(timeCurrent)
    }

    private fun parseTimeLObject(
        time: JSONObject
    ): LightDeviceTimeState {
        val year = time.optInt("Y", 0)
        val month = time.optInt("Mn", 0)
        val day = time.optInt("D", 0)
        val weekDay = time.optInt("WD", 0)
        val hour = time.optInt("H", -1)
        val minute = time.optInt("M", -1)
        val second = time.optInt("S", 0)

        validateTime(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute
        )

        return LightDeviceTimeState(
            year = year,
            month = month,
            day = day,
            weekDay = normalizeWeekDay(weekDay),
            hour = hour,
            minute = minute,
            second = second.coerceIn(0, 59),
            source = LightDeviceTimeState.Source.DEVICE
        )
    }

    private fun parseTimeCurrentString(
        value: String
    ): LightDeviceTimeState {
        val pattern = Regex(
            """(\d{1,2}):(\d{2})(?::(\d{2}))?\s+(\d{1,2})\.(\d{1,2})\.(\d{4})(?:\s+W(\d+))?"""
        )

        val match = pattern.find(value.trim())
            ?: throw IllegalStateException("Device time format is invalid")

        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3]
            .ifBlank { "0" }
            .toInt()

        val day = match.groupValues[4].toInt()
        val month = match.groupValues[5].toInt()
        val year = match.groupValues[6].toInt()
        val weekDay = match.groupValues[7]
            .ifBlank { "0" }
            .toInt()

        validateTime(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute
        )

        return LightDeviceTimeState(
            year = year,
            month = month,
            day = day,
            weekDay = normalizeWeekDay(weekDay),
            hour = hour,
            minute = minute,
            second = second.coerceIn(0, 59),
            source = LightDeviceTimeState.Source.DEVICE
        )
    }

    private fun validateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ) {
        if (
            year <= 0 ||
            month !in 1..12 ||
            day !in 1..31 ||
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            throw IllegalStateException("Device time is invalid")
        }
    }

    private fun normalizeResponseJson(
        response: String
    ): String {
        val trimmed = response.trim()

        if (trimmed.startsWith("{")) {
            return trimmed
        }

        if (trimmed.startsWith("Json=")) {
            val jsonStart = "Json=".length
            val jsonEnd = trimmed.indexOf("&sRet=")

            val rawJson = if (jsonEnd >= 0) {
                trimmed.substring(jsonStart, jsonEnd)
            } else {
                trimmed.substring(jsonStart)
            }

            return URLDecoder.decode(
                rawJson,
                Charsets.UTF_8.name()
            )
        }

        return trimmed
    }

    private fun normalizeWeekDay(
        weekDay: Int
    ): Int {
        return when (weekDay) {
            in 1..7 -> weekDay
            else -> phoneFallback().weekDay
        }