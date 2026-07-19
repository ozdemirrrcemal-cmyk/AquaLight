package com.aqua.aqualight.i18n

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DateOnlyTest {

    @Test
    fun pickerRoundTripPreservesCalendarDayAcrossSupportedTravelZones() {
        val date = LocalDate.of(2026, 7, 19)
        val epochDay = date.toEpochDay()
        val zones = listOf(
            ZoneId.of("Europe/Istanbul"),
            ZoneId.of("America/New_York"),
            ZoneId.of("Asia/Tokyo")
        )

        zones.forEach { zoneId ->
            val pickerMillis = DateOnly.toPickerMillis(epochDay, zoneId)
            assertEquals(
                "Picker conversion shifted the calendar day in $zoneId.",
                epochDay,
                DateOnly.fromPickerMillis(pickerMillis, zoneId)
            )
        }

        assertNotEquals(
            DateOnly.toPickerMillis(epochDay, zones.first()),
            DateOnly.toPickerMillis(epochDay, zones.last())
        )
    }

    @Test
    fun localizedDateFormattingUsesEpochDayWithoutTimezoneConversion() {
        val epochDay = LocalDate.of(2026, 7, 19).toEpochDay()

        val turkish = LocaleFormatter.formatDateEpochDay(epochDay, Locale.forLanguageTag("tr"))
        val english = LocaleFormatter.formatDateEpochDay(epochDay, Locale.ENGLISH)

        assertEquals("19 Tem 2026", turkish)
        assertEquals("Jul 19, 2026", english)
    }
}
