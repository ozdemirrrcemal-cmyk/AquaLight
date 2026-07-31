package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.runtime.parsing.requireExactKeys
import com.aqua.aqualight.data.devices.runtime.parsing.requiredBoolean
import com.aqua.aqualight.data.devices.runtime.parsing.requiredInt
import com.aqua.aqualight.data.devices.runtime.parsing.requiredNonNegativeLong
import com.aqua.aqualight.data.devices.runtime.parsing.requiredObject
import com.aqua.aqualight.data.devices.runtime.parsing.requiredString
import com.aqua.aqualight.data.devices.runtime.parsing.requiredStringAllowEmpty
import org.json.JSONObject

object DeviceTimeStatusParser {

    fun parse(data: JSONObject): DeviceTimeStatus {
        val status = data.optJSONObject("status") ?: data
        status.requireExactKeys(STATUS_KEYS, "time status")
        val parts = status.requiredObject("parts")
        val runtime = status.requiredObject("runtime")
        parts.requireExactKeys(PART_KEYS, "time status parts")
        runtime.requireExactKeys(RUNTIME_KEYS, "time status runtime")

        return DeviceTimeStatus(
            timeSet = status.requiredBoolean("timeSet"),
            timeString = status.requiredStringAllowEmpty("timeString"),
            uptime = status.requiredString("uptime"),
            uptimeMs = status.requiredNonNegativeLong("uptimeMs"),
            millisStartDay = status.requiredNonNegativeLong("millisStartDay"),
            timeZone = status.requiredInt("timeZone"),
            timezoneId = status.requiredStringAllowEmpty("timezoneId"),
            posixTimeZone = status.requiredStringAllowEmpty("posixTimeZone"),
            utcOffsetMinutes = status.requiredInt("utcOffsetMinutes").also {
                require(it in MIN_UTC_OFFSET_MINUTES..MAX_UTC_OFFSET_MINUTES)
            },
            autoSyncNtpEnabled = status.requiredBoolean("autoSyncNtpEnabled"),
            autoSyncGadgetEnabled = status.requiredBoolean("autoSyncGadgetEnabled"),
            ntpServerPrimary = status.requiredString("ntpServerPrimary"),
            ntpServerSecondary = status.requiredString("ntpServerSecondary"),
            lastSyncSource = status.requiredStringAllowEmpty("lastSyncSource"),
            lastSyncEpochMillis = status.requiredNonNegativeLong("lastSyncEpochMillis"),
            lastSyncUptimeMs = status.requiredNonNegativeLong("lastSyncUptimeMs"),
            parts = DeviceTimeParts(
                year = parts.requiredInt("year").also {
                    require(it in MIN_YEAR..MAX_YEAR)
                },
                month = parts.requiredInt("month").also {
                    require(it in MIN_MONTH..MAX_MONTH)
                },
                day = parts.requiredInt("day").also {
                    require(it in MIN_DAY..MAX_DAY)
                },
                weekday = parts.requiredInt("weekday").also {
                    require(it in MIN_WEEKDAY..MAX_WEEKDAY)
                },
                hour = parts.requiredInt("hour").also {
                    require(it in MIN_HOUR..MAX_HOUR)
                },
                minute = parts.requiredInt("minute").also {
                    require(it in MIN_MINUTE..MAX_MINUTE)
                },
                second = parts.requiredInt("second").also {
                    require(it in MIN_SECOND..MAX_SECOND)
                }
            ),
            runtime = DeviceTimeRuntimeCapabilities(
                module = runtime.requiredString("module").also {
                    require(it == DeviceTimeRuntimeContract.MODULE)
                },
                readOnly = runtime.requiredBoolean("readOnly"),
                supportsConfigApply = runtime.requiredBoolean("supportsConfigApply"),
                supportsPhoneSync = runtime.requiredBoolean("supportsPhoneSync"),
                supportsNtpSync = runtime.requiredBoolean("supportsNtpSync"),
                supportsRtcSet = runtime.requiredBoolean("supportsRtcSet")
            )
        )
    }

    private val STATUS_KEYS = setOf(
        "timeSet", "timeString", "uptime", "uptimeMs", "millisStartDay", "timeZone",
        "utcOffsetMinutes", "timezoneId", "posixTimeZone", "autoSyncNtpEnabled",
        "autoSyncGadgetEnabled", "ntpServerPrimary", "ntpServerSecondary", "lastSyncSource",
        "lastSyncEpochMillis", "lastSyncUptimeMs", "parts", "runtime"
    )
    private val PART_KEYS = setOf(
        "year", "month", "day", "weekday", "hour", "minute", "second"
    )
    private val RUNTIME_KEYS = setOf(
        "module", "readOnly", "supportsConfigApply", "supportsPhoneSync", "supportsNtpSync",
        "supportsRtcSet"
    )

    private const val MINUTES_PER_HOUR = 60
    private const val MIN_UTC_OFFSET_MINUTES = -18 * MINUTES_PER_HOUR
    private const val MAX_UTC_OFFSET_MINUTES = 18 * MINUTES_PER_HOUR
    private const val MIN_YEAR = 0
    private const val MAX_YEAR = 9_999
    private const val MIN_MONTH = 0
    private const val MAX_MONTH = 12
    private const val MIN_DAY = 0
    private const val MAX_DAY = 31
    private const val MIN_WEEKDAY = 0
    private const val MAX_WEEKDAY = 7
    private const val MIN_HOUR = 0
    private const val MAX_HOUR = 23
    private const val MIN_MINUTE = 0
    private const val MAX_MINUTE = 59
    private const val MIN_SECOND = 0
    private const val MAX_SECOND = 60
}
