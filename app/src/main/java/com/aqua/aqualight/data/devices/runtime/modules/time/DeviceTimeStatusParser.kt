package com.aqua.aqualight.data.devices.runtime.modules.time

import org.json.JSONObject

object DeviceTimeStatusParser {

    fun parse(
        data: JSONObject
    ): DeviceTimeStatus {
        val status = data.optJSONObject("status") ?: data

        return DeviceTimeStatus(
            timeSet = status.optBoolean("timeSet", false),
            timeString = status.optString("timeString", ""),
            timezoneId = status.optString("timezoneId", ""),
            posixTimeZone = status.optString("posixTimeZone", ""),
            utcOffsetMinutes = status.optInt("utcOffsetMinutes", status.optInt("timeZone", 0) * 60),
            autoSyncNtpEnabled = status.optBoolean("autoSyncNtpEnabled", false),
            autoSyncGadgetEnabled = status.optBoolean("autoSyncGadgetEnabled", false),
            ntpServerPrimary = status.optString(
                "ntpServerPrimary",
                DeviceTimeRuntimeContract.Default.PRIMARY_NTP_SERVER
            ),
            ntpServerSecondary = status.optString(
                "ntpServerSecondary",
                DeviceTimeRuntimeContract.Default.SECONDARY_NTP_SERVER
            ),
            lastSyncSource = status.optString("lastSyncSource", ""),
            lastSyncEpochMillis = status.optLong("lastSyncEpochMillis", 0L),
            lastSyncUptimeMs = status.optLong("lastSyncUptimeMs", 0L)
        )
    }
}
