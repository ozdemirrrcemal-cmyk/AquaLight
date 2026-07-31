package com.aqua.aqualight.data.devices.runtime.modules.time

import java.util.GregorianCalendar
import org.json.JSONObject

data class DeviceTimeZoneSnapshot(
    val timezoneId: String,
    val posixTimeZone: String,
    val utcOffsetMinutes: Int
) {
    init {
        requireCanonicalText(timezoneId, "timezoneId", allowEmpty = false)
        requireCanonicalText(posixTimeZone, "posixTimeZone", allowEmpty = true)
        requireUtcOffset(utcOffsetMinutes)
    }
}

data class DeviceTimeConfigApplyPayload(
    val timezoneId: String? = null,
    val posixTimeZone: String? = null,
    val utcOffsetMinutes: Int? = null,
    val ntpEnabled: Boolean? = null,
    val gadgetSyncEnabled: Boolean? = null,
    val ntpServerPrimary: String? = null,
    val ntpServerSecondary: String? = null,
    val save: Boolean = true
) {
    init {
        require(
            timezoneId != null || posixTimeZone != null || utcOffsetMinutes != null ||
                ntpEnabled != null || gadgetSyncEnabled != null ||
                ntpServerPrimary != null || ntpServerSecondary != null
        ) { "time.config.apply requires at least one canonical config field." }
        timezoneId?.let { requireCanonicalText(it, "timezoneId", allowEmpty = false) }
        posixTimeZone?.let { requireCanonicalText(it, "posixTimeZone", allowEmpty = true) }
        utcOffsetMinutes?.let(::requireUtcOffset)
        ntpServerPrimary?.let {
            requireCanonicalText(it, "ntpServerPrimary", allowEmpty = true)
        }
        ntpServerSecondary?.let {
            requireCanonicalText(it, "ntpServerSecondary", allowEmpty = true)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        timezoneId?.let { put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, it) }
        posixTimeZone?.let { put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, it) }
        utcOffsetMinutes?.let { put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, it) }
        ntpEnabled?.let { put(DeviceTimeRuntimeContract.Field.NTP_ENABLED, it) }
        gadgetSyncEnabled?.let { put(DeviceTimeRuntimeContract.Field.GADGET_SYNC_ENABLED, it) }
        ntpServerPrimary?.let { put(DeviceTimeRuntimeContract.Field.NTP_SERVER_PRIMARY, it) }
        ntpServerSecondary?.let { put(DeviceTimeRuntimeContract.Field.NTP_SERVER_SECONDARY, it) }
        put(DeviceTimeRuntimeContract.Field.SAVE, save)
    }
}

data class DevicePhoneSyncPayload(
    val epochMillis: Long,
    val timezoneId: String? = null,
    val posixTimeZone: String? = null,
    val utcOffsetMinutes: Int? = null,
    val ntpEnabled: Boolean? = null,
    val gadgetSyncEnabled: Boolean? = null,
    val ntpServerPrimary: String? = null,
    val ntpServerSecondary: String? = null,
    val save: Boolean = true
) {
    init {
        requireValidEpoch(epochMillis)
        timezoneId?.let { requireCanonicalText(it, "timezoneId", allowEmpty = false) }
        posixTimeZone?.let { requireCanonicalText(it, "posixTimeZone", allowEmpty = true) }
        utcOffsetMinutes?.let(::requireUtcOffset)
        ntpServerPrimary?.let {
            requireCanonicalText(it, "ntpServerPrimary", allowEmpty = true)
        }
        ntpServerSecondary?.let {
            requireCanonicalText(it, "ntpServerSecondary", allowEmpty = true)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put(DeviceTimeRuntimeContract.Field.EPOCH_MILLIS, epochMillis)
        timezoneId?.let { put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, it) }
        posixTimeZone?.let { put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, it) }
        utcOffsetMinutes?.let { put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, it) }
        ntpEnabled?.let { put(DeviceTimeRuntimeContract.Field.NTP_ENABLED, it) }
        gadgetSyncEnabled?.let { put(DeviceTimeRuntimeContract.Field.GADGET_SYNC_ENABLED, it) }
        ntpServerPrimary?.let { put(DeviceTimeRuntimeContract.Field.NTP_SERVER_PRIMARY, it) }
        ntpServerSecondary?.let { put(DeviceTimeRuntimeContract.Field.NTP_SERVER_SECONDARY, it) }
        put(DeviceTimeRuntimeContract.Field.SAVE, save)
    }
}

sealed interface DeviceRtcSetPayload {
    val timezoneId: String?
    val posixTimeZone: String?
    val utcOffsetMinutes: Int?
    val save: Boolean
    fun toJson(): JSONObject
}

data class DeviceManualRtcPayload(
    val year: Int,
    val month: Int,
    val day: Int,
    val weekday: Int = 1,
    val hour: Int,
    val minute: Int,
    val second: Int = 0,
    override val timezoneId: String? = null,
    override val posixTimeZone: String? = null,
    override val utcOffsetMinutes: Int? = null,
    override val save: Boolean = true
) : DeviceRtcSetPayload {
    init {
        require(year in DeviceTimeRuntimeContract.Limit.MIN_MANUAL_YEAR..
            DeviceTimeRuntimeContract.Limit.MAX_MANUAL_YEAR)
        requireValidCalendarDate(year, month, day)
        require(weekday in 1..7)
        require(hour in 0..23)
        require(minute in 0..59)
        require(second in 0..59)
        validateRtcConfig(timezoneId, posixTimeZone, utcOffsetMinutes)
    }

    override fun toJson(): JSONObject = JSONObject()
        .put(
            DeviceTimeRuntimeContract.Field.PARTS,
            JSONObject()
                .put(DeviceTimeRuntimeContract.Field.YEAR, year)
                .put(DeviceTimeRuntimeContract.Field.MONTH, month)
                .put(DeviceTimeRuntimeContract.Field.DAY, day)
                .put(DeviceTimeRuntimeContract.Field.WEEKDAY, weekday)
                .put(DeviceTimeRuntimeContract.Field.HOUR, hour)
                .put(DeviceTimeRuntimeContract.Field.MINUTE, minute)
                .put(DeviceTimeRuntimeContract.Field.SECOND, second)
        )
        .applyRtcConfig(timezoneId, posixTimeZone, utcOffsetMinutes, save)
}

data class DeviceEpochRtcPayload(
    val epochMillis: Long,
    override val timezoneId: String? = null,
    override val posixTimeZone: String? = null,
    override val utcOffsetMinutes: Int? = null,
    override val save: Boolean = true
) : DeviceRtcSetPayload {
    init {
        requireValidEpoch(epochMillis)
        validateRtcConfig(timezoneId, posixTimeZone, utcOffsetMinutes)
    }

    override fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimeRuntimeContract.Field.EPOCH_MILLIS, epochMillis)
        .applyRtcConfig(timezoneId, posixTimeZone, utcOffsetMinutes, save)
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
    val timeZoneHours: Int,
    val utcOffsetMinutes: Int,
    val timezoneId: String,
    val posixTimeZone: String,
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

data class DeviceTimeConfigApplyResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val event: String,
    val status: DeviceTimeStatus
)

data class DeviceTimeSyncResult(
    val operation: String,
    val synced: Boolean,
    val saved: Boolean?,
    val saveRequested: Boolean?,
    val event: String,
    val status: DeviceTimeStatus
)

private fun JSONObject.applyRtcConfig(
    timezoneId: String?,
    posixTimeZone: String?,
    utcOffsetMinutes: Int?,
    save: Boolean
): JSONObject = apply {
    timezoneId?.let { put(DeviceTimeRuntimeContract.Field.TIMEZONE_ID, it) }
    posixTimeZone?.let { put(DeviceTimeRuntimeContract.Field.POSIX_TIME_ZONE, it) }
    utcOffsetMinutes?.let { put(DeviceTimeRuntimeContract.Field.UTC_OFFSET_MINUTES, it) }
    put(DeviceTimeRuntimeContract.Field.SAVE, save)
}

private fun validateRtcConfig(
    timezoneId: String?,
    posixTimeZone: String?,
    utcOffsetMinutes: Int?
) {
    timezoneId?.let { requireCanonicalText(it, "timezoneId", allowEmpty = false) }
    posixTimeZone?.let { requireCanonicalText(it, "posixTimeZone", allowEmpty = true) }
    utcOffsetMinutes?.let(::requireUtcOffset)
}

private fun requireValidEpoch(epochMillis: Long) {
    require(epochMillis >= DeviceTimeRuntimeContract.Limit.MIN_EPOCH_MILLIS) {
        "epochMillis predates the firmware's supported epoch."
    }
}

private fun requireUtcOffset(value: Int) {
    require(value in DeviceTimeRuntimeContract.Limit.MIN_UTC_OFFSET_MINUTES..
        DeviceTimeRuntimeContract.Limit.MAX_UTC_OFFSET_MINUTES)
}

private fun requireCanonicalText(value: String, field: String, allowEmpty: Boolean) {
    require(allowEmpty || value.isNotEmpty()) { "$field must not be empty." }
    require(value == value.trim()) { "$field must not contain surrounding whitespace." }
    require(value.none(Char::isISOControl)) { "$field must not contain control characters." }
}

private fun requireValidCalendarDate(year: Int, month: Int, day: Int) {
    val calendar = GregorianCalendar().apply {
        isLenient = false
        clear()
        set(year, month - 1, day)
    }
    calendar.time
}
