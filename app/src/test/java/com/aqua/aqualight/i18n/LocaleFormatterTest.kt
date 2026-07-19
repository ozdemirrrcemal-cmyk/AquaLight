package com.aqua.aqualight.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun decimalInputAcceptsAppAndImeSeparatorsButRejectsGroupingAndMixedValues() {
        val turkish = Locale("tr", "TR")

        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", Locale.US)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", Locale.US)), 0.0)
        assertNull(LocaleFormatter.parseDecimal("1,234.5", Locale.US))
        assertNull(LocaleFormatter.parseDecimal("1.234,5", turkish))
        assertNull(LocaleFormatter.parseDecimal("12,", turkish))
        assertNull(LocaleFormatter.parseDecimal("NaN", Locale.US))
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

    @Test
    fun unsupportedConfiguredLocaleUsesSupportedDeviceDefault() {
        val resolved = LocaleFormatter.resolveSupportedLocale(Locale.SIMPLIFIED_CHINESE)

        assertEquals(
            SupportedLocaleRegistry.deviceDefault(),
            resolved.toLanguageTag()
        )
        assertTrue(resolved.toLanguageTag() in SupportedLocaleRegistry.all)
    }

    @Test
    fun supportedConfiguredLocalesRemainSelected() {
        assertEquals(
            "en",
            LocaleFormatter.resolveSupportedLocale(Locale.ENGLISH).toLanguageTag()
        )
        assertEquals(
            "tr",
            LocaleFormatter.resolveSupportedLocale(Locale("tr", "TR")).toLanguageTag()
        )
    }
}
