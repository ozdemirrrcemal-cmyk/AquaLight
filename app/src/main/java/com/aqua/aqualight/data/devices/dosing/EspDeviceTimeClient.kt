package com.aqua.aqualight.data.devices.dosing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
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
        val safeDeviceIp =
            deviceIp.trim()

        if (safeDeviceIp.isBlank()) {
            return null
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val requestJson =
                    """{"Time":{"TimeCurrent":0}}"""

                val encodedJson =
                    URLEncoder.encode(
                        requestJson,
                        "UTF-8"
                    )

                val encodedRet =
                    URLEncoder.encode(
                        "TimeCurrent",
                        "UTF-8"
                    )

                val url =
                    URL(
                        "http://$safeDeviceIp/get?Json=$encodedJson&sRet=$encodedRet"
                    )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    2500

                connection.readTimeout =
                    2500

                connection.useCaches =
                    false

                val responseCode =
                    connection.responseCode

                if (responseCode !in 200..299) {
                    connection.disconnect()
                    return@runCatching null
                }

                val responseBody =
                    connection.inputStream
                        .bufferedReader()
                        .use { reader ->
                            reader.readText()
                        }

                connection.disconnect()

                val root =
                    JSONObject(responseBody)

                val rawTime =
                    root.optJSONObject("Time")
                        ?.optString("TimeCurrent")
                        .orEmpty()
                        .trim()

                val millis =
                    parseEspTimeCurrent(
                        rawTime = rawTime
                    ) ?: return@runCatching null

                EspDeviceTimeResult(
                    millis = millis,
                    rawTimeText = rawTime
                )
            }.getOrNull()
        }
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