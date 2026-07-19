package com.aqua.aqualight.i18n

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stable local-calendar grouping key for timestamp-backed UI timelines. */
object LocalDayKey {

    fun fromEpochMillis(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): LocalDate {
        return Instant.ofEpochMilli(timeMillis)
            .atZone(zoneId)
            .toLocalDate()
    }
}
