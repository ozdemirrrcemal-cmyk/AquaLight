package com.aqua.aqualight.localization

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleFormattersTest {

    @Test
    fun numbersUseLocaleSpecificSeparators() {
        val english = LocaleFormatters.formatNumber(1234.5, Locale.US)
        val german = LocaleFormatters.formatNumber(1234.5, Locale.GERMANY)

        assertEquals("1,234.5", english)
        assertEquals("1.234,5", german)
        assertNotEquals(english, german)
    }

    @Test
    fun percentageAcceptsFractionAndUsesLocaleRules() {
        val formatted = LocaleFormatters.formatPercent(0.42, Locale.US)

        assertTrue(formatted.contains("42"))
        assertTrue(formatted.contains("%"))
    }

    @Test
    fun dateTimeFormattingProducesLocalizedReadableText() {
        val epochMillis = 1_735_732_800_000L
        val english = LocaleFormatters.formatDateTime(epochMillis, Locale.US)
        val german = LocaleFormatters.formatDateTime(epochMillis, Locale.GERMANY)

        assertTrue(english.isNotBlank())
        assertTrue(german.isNotBlank())
        assertNotEquals(english, german)
    }

    @Test
    fun localizedNumberParsingRequiresCompleteInput() {
        assertEquals(
            1234.5,
            LocaleFormatters.parseNumber("1.234,5", Locale.GERMANY)!!.toDouble(),
            0.0
        )
        assertEquals(
            1234.5,
            LocaleFormatters.parseNumber("1,234.5", Locale.US)!!.toDouble(),
            0.0
        )
        assertNull(LocaleFormatters.parseNumber("12 invalid", Locale.US))
    }

    @Test
    fun localDayKeyGroupsTimesFromTheSameCalendarDay() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 19, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morning = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 22)

        assertEquals(
            LocaleFormatters.localDayKey(morning),
            LocaleFormatters.localDayKey(calendar.timeInMillis)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun numberFormatterRejectsInvalidFractionRange() {
        LocaleFormatters.formatNumber(
            value = 1.0,
            locale = Locale.US,
            minimumFractionDigits = 3,
            maximumFractionDigits = 2
        )
    }
}
