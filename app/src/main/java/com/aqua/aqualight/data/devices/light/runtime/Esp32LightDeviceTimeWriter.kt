package com.aqua.aqualight.data.devices.light.runtime

import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.roundToInt

class Esp32LightDeviceTimeWriter(
    private val httpClient: Esp32HttpJsonClient = Esp32HttpJsonClient()
) {

    suspend fun syncClock(
        ip: String,
        timeState: LightDeviceTimeState,
        timeZoneOffsetMinutes: Int,
        protocol: LightDeviceTimeSyncProtocol
    ): LightCommandResult {
        return when (protocol) {
            LightDeviceTimeSyncProtocol.LEGACY_HOUR_TIME_ZONE -> {
                syncLegacyClock(
                    ip = ip,
                    timeState = timeState,
                    timeZoneOffsetMinutes = timeZoneOffsetMinutes
                )
            }

            LightDeviceTimeSyncProtocol.MODERN_MINUTE_TIME_ZONE -> {
                syncModernClock(
                    ip = ip,
                    timeState = timeState,
                    timeZoneOffsetMinutes = timeZoneOffsetMinutes
                )
            }
        }
    }

    private suspend fun syncLegacyClock(
        ip: String,
        timeState: LightDeviceTimeState,
        timeZoneOffsetMinutes: Int
    ): LightCommandResult {
        val safeOffsetMinutes =
            timeZoneOffsetMinutes.coerceIn(
                MIN_TIME_ZONE_OFFSET_MINUTES,
                MAX_TIME_ZONE_OFFSET_MINUTES
            )

        val timeZoneResult = if (safeOffsetMinutes % MINUTES_PER_HOUR == 0) {
            writeLegacyWholeHourTimeZone(
                ip = ip,
                timeZoneOffsetMinutes = safeOffsetMinutes
            )
        } else {
            disableLegacyNtpForFractionalTimeZone(
                ip = ip
            )
        }

        if (!timeZoneResult.isSuccess) {
            return timeZoneResult
        }

        delay(LEGACY_TIME_ZONE_TO_SET_TIME_DELAY_MS)

        return writeSetTimeOnly(
            ip = ip,
            timeState = timeState
        )
    }

    private suspend fun syncModernClock(
        ip: String,
        timeState: LightDeviceTimeState,
        timeZoneOffsetMinutes: Int
    ): LightCommandResult {
        val safeOffsetMinutes =
            timeZoneOffsetMinutes.coerceIn(
                MIN_TIME_ZONE_OFFSET_MINUTES,
                MAX_TIME_ZONE_OFFSET_MINUTES
            )

        val json = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put("TimeZoneMinutes", safeOffsetMinutes)
                    .put(
                        "SetTime",
                        buildTimeObject(
                            timeState = timeState
                        )
                    )
            )
            .put(
                "Main",
                JSONObject()
                    .put("SaveConfig", 1)
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_sync_modern"
        )
    }

    private suspend fun writeLegacyWholeHourTimeZone(
        ip: String,
        timeZoneOffsetMinutes: Int
    ): LightCommandResult {
        val timeZoneHours =
            (timeZoneOffsetMinutes.toDouble() / MINUTES_PER_HOUR.toDouble())
                .roundToInt()
                .coerceIn(
                    MIN_LEGACY_TIME_ZONE_HOURS,
                    MAX_LEGACY_TIME_ZONE_HOURS
                )

        val json = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put("TimeZone", timeZoneHours)
                    .put("EnabledAutoSyncNTP", 1)
            )
            .put(
                "Main",
                JSONObject()
                    .put("SaveConfig", 1)
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_zone_legacy"
        )
    }

    private suspend fun disableLegacyNtpForFractionalTimeZone(
        ip: String
    ): LightCommandResult {
        val json = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put("EnabledAutoSyncNTP", 0)
            )
            .put(
                "Main",
                JSONObject()
                    .put("SaveConfig", 1)
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_zone_fractional_legacy"
        )
    }

    private suspend fun writeSetTimeOnly(
        ip: String,
        timeState: LightDeviceTimeState
    ): LightCommandResult {
        val json = JSONObject()
            .put(
                "Time",
                JSONObject()
                    .put(
                        "SetTime",
                        buildTimeObject(
                            timeState = timeState
                        )
                    )
            )
            .toString()

        return httpClient.postSet(
            ip = ip,
            json = json,
            requestTag = "light_time_set"
        )
    }

    private fun buildTimeObject(
        timeState: LightDeviceTimeState
    ): JSONObject {
        return JSONObject()
            .put("Y", timeState.year.coerceIn(2000, 2099))
            .put("Mn", timeState.month.coerceIn(1, 12))
            .put("D", timeState.day.coerceIn(1, 31))
            .put("WD", toFirmwareWeekDay(timeState.weekDay))
            .put("H", timeState.hour.coerceIn(0, 23))
            .put("M", timeState.minute.coerceIn(0, 59))
            .put("S", timeState.second.coerceIn(0, 59))
    }

    private fun toFirmwareWeekDay(
        appWeekDay: Int
    ): Int {
        return when (appWeekDay) {
            7 -> 1
            in 1..6 -> appWeekDay + 1
            else -> 1
        }
    }

    companion object {
        private const val MINUTES_PER_HOUR = 60

        private const val MIN_TIME_ZONE_OFFSET_MINUTES = -12 * 60
        private const val MAX_TIME_ZONE_OFFSET_MINUTES = 14 * 60

        private const val MIN_LEGACY_TIME_ZONE_HOURS = -12
        private const val MAX_LEGACY_TIME_ZONE_HOURS = 14

        private const val LEGACY_TIME_ZONE_TO_SET_TIME_DELAY_MS = 150L
    }
}