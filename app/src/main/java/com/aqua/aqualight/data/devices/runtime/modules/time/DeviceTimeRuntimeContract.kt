package com.aqua.aqualight.data.devices.runtime.modules.time

object DeviceTimeRuntimeContract {
    const val MODULE = "time"

    object Action {
        const val STATUS_GET = "status.get"
        const val CONFIG_APPLY = "config.apply"
        const val PHONE_SYNC = "phone.sync"
        const val NTP_SYNC = "ntp.sync"
        const val RTC_SET = "rtc.set"
    }

    object Event {
        const val STATUS_CHANGED = "status.changed"
    }

    object Field {
        const val EPOCH_MILLIS = "epochMillis"
        const val TIMEZONE_ID = "timezoneId"
        const val POSIX_TIME_ZONE = "posixTimeZone"
        const val UTC_OFFSET_MINUTES = "utcOffsetMinutes"
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

    object Limit {
        const val MIN_UTC_OFFSET_MINUTES = -14 * 60
        const val MAX_UTC_OFFSET_MINUTES = 14 * 60
        const val MIN_EPOCH_MILLIS = 946_684_800_000L
        const val MIN_MANUAL_YEAR = 1970
        const val MAX_MANUAL_YEAR = 2225
    }
}
