package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class WaterAnalysisObservationTimeTest {
    private val zone = TimeZone.getTimeZone("Europe/Istanbul")

    @Test
    fun datePickerDoesNotReplaceSelectedTime() {
        val current = instant(2026, Calendar.SEPTEMBER, 26, 18, 47)
        val picked = instant(2026, Calendar.OCTOBER, 4, 9, 12)

        WaterAnalysisObservationTime.withDate(current, picked.timeInMillis)

        assertEquals(2026, current.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, current.get(Calendar.MONTH))
        assertEquals(4, current.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, current.get(Calendar.HOUR_OF_DAY))
        assertEquals(47, current.get(Calendar.MINUTE))
    }

    @Test
    fun timePickerDoesNotReplaceSelectedDate() {
        val current = instant(2026, Calendar.SEPTEMBER, 26, 18, 47)
        val picked = instant(2026, Calendar.OCTOBER, 4, 9, 12)

        WaterAnalysisObservationTime.withTime(current, picked.timeInMillis)

        assertEquals(2026, current.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, current.get(Calendar.MONTH))
        assertEquals(26, current.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, current.get(Calendar.HOUR_OF_DAY))
        assertEquals(12, current.get(Calendar.MINUTE))
        assertEquals(0, current.get(Calendar.SECOND))
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, day, hour, minute)
        }
}
