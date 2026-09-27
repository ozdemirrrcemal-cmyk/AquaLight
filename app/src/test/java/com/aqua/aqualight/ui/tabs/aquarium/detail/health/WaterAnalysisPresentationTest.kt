package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisPresentationTest {
    @Test
    fun smallPositiveReadingCannotBeDisplayedAsMeasuredZero() {
        listOf(Locale.ENGLISH, Locale.forLanguageTag("tr-TR")).forEach { locale ->
            assertEquals("0", WaterAnalysisPresentation.formatMeasuredNumber(0.0, locale))
            val displayed = WaterAnalysisPresentation.formatMeasuredNumber(SMALL_POSITIVE, locale)
            assertTrue(displayed, displayed.contains("E-"))
            assertTrue(displayed, displayed != "0")
            val negative = WaterAnalysisPresentation.formatMeasuredNumber(-SMALL_POSITIVE, locale)
            assertTrue(negative, negative.startsWith("-"))
            assertTrue(negative, negative.contains("E-"))
        }
    }

    private companion object {
        const val SMALL_POSITIVE = 0.00000123
    }
}
