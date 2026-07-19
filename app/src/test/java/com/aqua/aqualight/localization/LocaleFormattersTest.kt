package com.aqua.aqualight.localization

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocaleFormattersTest {

    private val testMillis = LocalDateTime.of(2026, 7, 19, 14, 5)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `established English schedule pattern remains unchanged`() {
        assertEquals(
            "19 Jul 2026",
            LocaleFormatters.formatPattern(testMillis, "dd MMM yyyy", Locale.ENGLISH)
        )
        assertEquals(
            "14:05",
            LocaleFormatters.formatPattern(testMillis, "HH:mm", Locale.ENGLISH)
        )
    }

    @Test
    fun `numbers use locale decimal symbols without forced US formatting`() {
        assertEquals("12.5", LocaleFormatters.formatNumber(12.5, Locale.ENGLISH))
        assertEquals("12,5", LocaleFormatters.formatNumber(12.5, Locale.GERMAN))
        assertEquals("50%", LocaleFormatters.formatPercent(50, Locale.ENGLISH))
    }

    @Test
    fun `localized number parsing requires full valid input`() {
        assertEquals(12.5, LocaleFormatters.parseNumber("12,5", Locale.GERMAN)?.toDouble())
        assertNull(LocaleFormatters.parseNumber("12,5x", Locale.GERMAN))
        assertNull(LocaleFormatters.parseNumber("", Locale.ENGLISH))
    }
}
