package com.aqua.aqualight.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocaleFormatterTest {

    @Test
    fun numberFormattingUsesLocaleDecimalSeparators() {
        assertEquals(
            "1,234.5",
            AppLocaleFormatter.formatNumber(Locale.US, 1234.5, maximumFractionDigits = 1)
        )
        assertEquals(
            "1.234,5",
            AppLocaleFormatter.formatNumber(Locale.GERMANY, 1234.5, maximumFractionDigits = 1)
        )
    }

    @Test
    fun percentFormattingUsesLocaleRules() {
        assertEquals("25%", AppLocaleFormatter.formatPercent(Locale.US, 0.25))
        assertTrue(
            AppLocaleFormatter.formatPercent(Locale.GERMANY, 0.25)
                .replace('\u00A0', ' ')
                .contains("25")
        )
    }

    @Test
    fun dateFormattingChangesWithLocale() {
        val epochMillis = 1_735_689_600_000L // 2025-01-01T00:00:00Z
        val english = AppLocaleFormatter.formatDate(Locale.US, epochMillis)
        val german = AppLocaleFormatter.formatDate(Locale.GERMANY, epochMillis)

        assertTrue(english.isNotBlank())
        assertTrue(german.isNotBlank())
        assertTrue(english != german)
    }
}
