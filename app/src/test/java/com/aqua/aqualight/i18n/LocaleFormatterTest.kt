package com.aqua.aqualight.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleFormatterTest {

    private val turkish = Locale.forLanguageTag("tr-TR")
    private val english = Locale.ENGLISH

    @Test
    fun integersFollowAppLocaleWithoutGrouping() {
        assertEquals("1234", LocaleFormatter.formatInteger(1_234, english))
        assertEquals("1234", LocaleFormatter.formatInteger(1_234, turkish))
    }

    @Test
    fun decimalsUseTurkishCommaAndEnglishPointWithoutGrouping() {
        assertEquals("12.5", LocaleFormatter.formatDecimal(12.5, english))
        assertEquals("12,5", LocaleFormatter.formatDecimal(12.5, turkish))
        assertEquals("1234.5", LocaleFormatter.formatDecimal(1_234.5, english))
        assertEquals("1234,5", LocaleFormatter.formatDecimal(1_234.5, turkish))
    }

    @Test
    fun decimalInputAcceptsPrimaryAndUnambiguousAlternateSeparators() {
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.5", english)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,5", english)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12,50", turkish)), 0.0)
        assertEquals(12.5, requireNotNull(LocaleFormatter.parseDecimal("12.50", english)), 0.0)
        assertEquals(0.5, requireNotNull(LocaleFormatter.parseDecimal(",5", turkish)), 0.0)
        assertEquals(0.5, requireNotNull(LocaleFormatter.parseDecimal(".5", english)), 0.0)
        assertEquals(1234.0, requireNotNull(LocaleFormatter.parseDecimal("1234", turkish)), 0.0)
    }

    @Test
    fun ambiguousGroupingLikeInputsAreRejectedInBothLanguages() {
        listOf("1.234", "1,234").forEach { ambiguous ->
            assertNull(LocaleFormatter.parseDecimal(ambiguous, turkish))
            assertNull(LocaleFormatter.parseDecimal(ambiguous, english))
        }
    }

    @Test
    fun malformedSignedAndNonFiniteDecimalInputsAreRejected() {
        listOf(
            "1,234.5",
            "1.234,5",
            "12,",
            "12.",
            "12,,5",
            "12..5",
            "+12.5",
            "-12.5",
            "NaN",
            "Infinity",
            "1 234",
            ""
        ).forEach { invalid ->
            assertNull(LocaleFormatter.parseDecimal(invalid, turkish))
            assertNull(LocaleFormatter.parseDecimal(invalid, english))
        }
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
    fun wallClockScheduleTimeUsesStable24HourFormatting() {
        assertEquals("00:00", LocaleFormatter.formatTimeOfDay24Hour(0, english))
        assertEquals("09:30", LocaleFormatter.formatTimeOfDay24Hour(570, turkish))
        assertEquals("23:59", LocaleFormatter.formatTimeOfDay24Hour(1_439, english))
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
            LocaleFormatter.resolveSupportedLocale(turkish).toLanguageTag()
        )
    }
}
