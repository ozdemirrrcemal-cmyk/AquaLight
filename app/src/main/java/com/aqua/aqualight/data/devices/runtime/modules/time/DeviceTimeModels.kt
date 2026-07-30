package com.aqua.aqualight.data.devices.runtime.modules.time

import org.json.JSONObject

data class DeviceTimeZoneSnapshot(
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int
)

data class DeviceTimeConfigApplyPayload(
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int,
    val ntpEnabled: Boolean = true,
    val gadgetSyncEnabled: Boolean = true,
    val ntpServerPrimary: String = DeviceTimeRuntimeContract.Default.PRIMARY_NTP_SERVER,
    val ntpServerSecondary: String = DeviceTimeRuntimeContract.Default.SECONDARY_NTP_SERVER,
    val save: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, timezoneId)
        .put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, posixTimeZone)
        .put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, utcOffsetMinutes)
        .put(DeviceTimeRuntimeContract.Field.NTP_ENABLED, ntpEnabled)
        .put(DeviceTimeRuntimeContract.Field.GADGET_SYNC_ENABLED, gadgetSyncEnabled)
        .put(DeviceTimeRuntimeContract.Field.NTP_SERVER_PRIMARY, ntpServerPrimary)
        .put(DeviceTimeRuntimeContract.Field.NTP_SERVER_SECONDARY, ntpServerSecondary)
        .put(DeviceTimeRuntimeContract.Field.SAVE, save)
}

data class DevicePhoneSyncPayload(
    val epochMillis: Long,
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int,
    val ntpEnabled: Boolean = true,
    val gadgetSyncEnabled: Boolean = true,
    val save: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimeRuntimeContract.Field.EPOCH_MILLIS, epochMillis)
        .put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, timezoneId)
        .put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, posixTimeZone)
        .put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, utcOffsetMinutes)
        .put(DeviceTimeRuntimeContract.Field.NTP_ENABLED, ntpEnabled)
        .put(DeviceTimeRuntimeContract.Field.GADGET_SYNC_ENABLED, gadgetSyncEnabled)
        .put(DeviceTimeRuntimeContract.Field.SAVE, save)
}

data class DeviceManualRtcPayload(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekday: Int = 1,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int,
    val save: Boolean = true
) {
    fun toJson(): JSONObject {
        val parts = JSONObject()
            .put(DeviceTimeRuntimeContract.Field.YEAR, year)
            .put(DeviceTimeRuntimeContract.Field.MONTH, month)
            .put(DeviceTimeRuntimeContract.Field.DAY, day)
            .put(DeviceTimeRuntimeContract.Field.WEEKDAY, weekday)
            .put(DeviceTimeRuntimeContract.Field.HOUR, hour)
            .put(DeviceTimeRuntimeContract.Field.MINUTE, minute)
            .put(DeviceTimeRuntimeContract.Field.SECOND, second)

        return JSONObject()
            .put(DeviceTimeRuntimeContract.Field.PARTS, parts)
            .put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, timezoneId)
            .put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, posixTimeZone)
            .put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, utcOffsetMinutes)
            .put(DeviceTimeRuntimeContract.Field.SAVE, save)
    }
}

data class DeviceTimeParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekday: Int,
    val hour: Int,
    val minute: Int,
    val second: Int
)

data class DeviceTimeRuntimeCapabilities(
    val module: String,
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsPhoneSync: Boolean,
    val supportsNtpSync: Boolean,
    val supportsRtcSet: Boolean
)

data class DeviceTimeStatus(
    val timeSet: Boolean,
    val timeString: String,
    val uptime: String,
    val uptimeMs: Long,
    val millisStartDay: Long,
    val timeZone: Int,
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int,
    val autoSyncNtpEnabled: Boolean,
    val autoSyncGadgetEnabled: Boolean,
    val ntpServerPrimary: String,
    val ntpServerSecondary: String,
    val lastSyncSource: String,
    val lastSyncEpochMillis: Long,
    val lastSyncUptimeMs: Long,
    val parts: DeviceTimeParts,
    val runtime: DeviceTimeRuntimeCapabilities
)

data class DeviceTimeCommandResult(
    val sent: Boolean,
    val skipped: Boolean = false,
    val module: String = DeviceTimeRuntimeContract.MODULE,
    val action: String,
    val messageId: String = "",
    val errorMessage: String = ""
) {
    val isSuccess: Boolean
        get() = sent && errorMessage.isBlank()
}
