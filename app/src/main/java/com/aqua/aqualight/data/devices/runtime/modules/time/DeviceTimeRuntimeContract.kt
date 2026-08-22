package com.aqua.aqualight.data.devices.runtime.modules.time

/**
 * Firmware-verified Android mirror.
 *
 * Firmware:
 * AquaLight-Firmware / agent/rtc-commercial-v1-20260822
 * commit 751cdc2e497531b8f754b59b4a2ae3828aaf9b52
 *
 * The mandatory-RTC implementation preserves the original aql.ws.v1 module, actions, request
 * fields and exact response shapes. Android derives readiness from the existing `timeSet` field;
 * there is no status-version selector, RTC extension object, or parallel time contract.
 *
 * module: time
 * actions:
 * - status.get
 * - config.apply
 * - phone.sync
 * - ntp.sync
 * - rtc.set
 */
object DeviceTimeRuntimeContract {

    const val MODULE = "time"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val PHONE_SYNC = "phone.sync"
        const val NTP_SYNC = "ntp.sync"
        const val RTC_SET = "rtc.set"
    }

    object Field {
        const val EPOCH_MILLIS = "epochMillis"
        const val TIMEZONE_ID = "timezoneId"
        const val POSIX_TIME_ZONE = "posixTimeZone"
        const val UTC_OFFSET_MINUTES = "utcOffsetMinutes"
        const val TIME_ZONE = "timeZone"
        const val NTP_ENABLED = "ntpEnabled"
        const val GADGET_SYNC_ENABLED = "gadgetSyncEnabled"
        const val NTP_SERVER_PRIMARY = "ntpServerPrimary"
        const val NTP_SERVER_SECONDARY = "ntpServerSecondary"
        const val SAVE = "save"
        const val PARTS = "parts"
        const val YEAR = "year"
        const val MONTH = "month"
        const val DAY = "day"
        const val WEEKDAY = "weekday"
        const val HOUR = "hour"
        const val MINUTE = "minute"
        const val SECOND = "second"
    }

    object Default {
        const val PRIMARY_NTP_SERVER = "pool.ntp.org"
        const val SECONDARY_NTP_SERVER = "time.nist.gov"
    }
}
