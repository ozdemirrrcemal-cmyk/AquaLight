package com.aqua.aqualight.i18n

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Boundary for values that represent a calendar day, not a moment in time.
 *
 * Durable/application state stores epoch-day values. Epoch milliseconds are used only while
 * interoperating with Android picker APIs and are immediately converted at that boundary.
 */
object DateOnly {

    fun todayEpochDay(
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = LocalDate.now(zoneId).toEpochDay()

    fun fromPickerMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
            .toEpochDay()
    }

    fun toPickerMillis(
        epochDay: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        return LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
