package com.aqua.aqualight.data.devices.dosing

import java.util.Calendar
import java.util.Locale

data class EspDeviceTimeResult(
    val millis: Long,
    val rawTimeText: String
)

object EspDeviceTimeClient {

    suspend fun readCurrentTimeMillis(
        deviceIp: String
    ): EspDeviceTimeResult? {
        val root =
            EspDosingHttpClient.getJson(
                deviceIp = deviceIp,
                requestJson = """{"Time":{"TimeCurrent":0}}"""
            ) ?: return null

        val rawTime =
            root.optJSONObject("Time")
                ?.optString("TimeCurrent")
                .orEmpty()
                .trim()

        val millis =
            parseEspTimeCurrent(
                rawTime = rawTime
            ) ?: return null

        return EspDeviceTimeResult(
            millis = millis,
            rawTimeText = rawTime
        )
    }

    private fun parseEspTimeCurrent(
        rawTime: String
    ): Long? {
        val regex =
            Regex(
                pattern = """(\d{1,2}):(\d{2}):(\d{2})\s+(\d{1,2})\.(\d{1,2})\.(\d{4})"""
            )

        val match =
            regex.find(
                input = rawTime
            ) ?: return null

        val hour =
            match.groupValues[1].toIntOrNull() ?: return null

        val minute =
            match.groupValues[2].toIntOrNull() ?: return null

        val second =
            match.groupValues[3].toIntOrNull() ?: return null

        val day =
            match.groupValues[4].toIntOrNull() ?: return null

        val month =
            match.groupValues[5].toIntOrNull() ?: return null

        val year =
            match.groupValues[6].toIntOrNull() ?: return null

        if (
            year < 2024 ||
            month !in 1..12 ||
            day !in 1..31 ||
            hour !in 0..23 ||
            minute !in 0..59 ||
            second !in 0..59
        ) {
            return null
        }

        val calendar =
            Calendar.getInstance(
                Locale.getDefault()
            ).apply {
                set(
                    Calendar.YEAR,
                    year
                )

                set(
                    Calendar.MONTH,
                    month - 1
                )

                set(
                    Calendar.DAY_OF_MONTH,
                    day
                )

                set(
                    Calendar.HOUR_OF_DAY,
                    hour
                )

                set(
                    Calendar.MINUTE,
                    minute
                )

                set(
                    Calendar.SECOND,
                    second
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }

        return calendar.timeInMillis
    }
}