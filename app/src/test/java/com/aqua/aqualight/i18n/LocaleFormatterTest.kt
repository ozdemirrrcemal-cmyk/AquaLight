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
    fun percentagesAndDatesAreLocaleAware() {
        assertEquals(
            "50%",
            LocaleFormatter.formatPercent(0.5, Locale.US)
        )

        val timestamp = 1_735_732_800_000L
        val english = LocaleFormatter.formatDateTime(timestamp, Locale.US)
        val german = LocaleFormatter.formatDateTime(timestamp, Locale.GERMANY)

        assertTrue(english.isNotBlank())
        assertTrue(german.isNotBlank())
        assertNotEquals(english, german)
    }
}
