package com.aqua.aqualight.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleFormatterTest {

    @Test
    fun integersUseLocaleSpecificGrouping() {
        assertEquals(
            "1,234",
            LocaleFormatter.formatInteger(1_234, Locale.US)
        )
        assertEquals(
            "1.234",
            LocaleFormatter.formatInteger(1_234, Locale.GERMANY)
        )
    }

    @Test
    fun decimalsUseLocaleSpecificSeparators() {
        assertEquals(
            "12.5",
            LocaleFormatter.formatDecimal(12.5, Locale.US)
        )
        assertEquals(
            "12,5",
            LocaleFormatter.formatDecimal(12.5, Locale.GERMANY)
        )
    }

    @Test
    fun percentagesDatesAndTimesAreLocaleAware() {
        assertEquals(
            "50%",
            LocaleFormatter.formatPercent(0.5, Locale.US)
        )

        val timestamp = 1_735_732_800_000L
        val englishDate = LocaleFormatter.formatDate(timestamp, Locale.US)
        val germanDate = LocaleFormatter.formatDate(timestamp, Locale.GERMANY)
        val englishTime = LocaleFormatter.formatTime(timestamp, Locale.US)
        val germanTime = LocaleFormatter.formatTime(timestamp, Locale.GERMANY)
        val englishDateTime = LocaleFormatter.formatDateTime(timestamp, Locale.US)
        val germanDateTime = LocaleFormatter.formatDateTime(timestamp, Locale.GERMANY)

        assertTrue(englishDate.isNotBlank())
        assertTrue(germanDate.isNotBlank())
        assertTrue(englishTime.isNotBlank())
        assertTrue(germanTime.isNotBlank())
        assertNotEquals(englishDate, germanDate)
        assertNotEquals(englishTime, germanTime)
        assertNotEquals(englishDateTime, germanDateTime)
    }
}
