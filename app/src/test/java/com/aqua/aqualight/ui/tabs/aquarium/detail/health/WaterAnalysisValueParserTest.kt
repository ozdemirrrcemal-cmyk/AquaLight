package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WaterAnalysisValueParserTest {

    @Test
    fun acceptsDotAndCommaDecimalsWithoutLocaleGuessing() {
        assertEquals(12.3456, WaterAnalysisValueParser.parse("12.3456") ?: error("parse"), 0.0)
        assertEquals(12.3456, WaterAnalysisValueParser.parse("12,3456") ?: error("parse"), 0.0)
    }

    @Test
    fun rejectsGroupingSignsExponentsAndPartialValues() {
        assertNull(WaterAnalysisValueParser.parse("1,234.5"))
        assertNull(WaterAnalysisValueParser.parse("-1"))
        assertNull(WaterAnalysisValueParser.parse("1e3"))
        assertNull(WaterAnalysisValueParser.parse("12."))
        assertNull(WaterAnalysisValueParser.parse(""))
    }

    @Test
    fun tinyPositiveInputCannotBecomeMeasuredZero() {
        assertNull(WaterAnalysisValueParser.parse("0.${"0".repeat(UNDERFLOW_ZERO_COUNT)}1"))
        assertEquals(0.0, WaterAnalysisValueParser.parse("0,000") ?: error("parse"), 0.0)
    }

    private companion object {
        const val UNDERFLOW_ZERO_COUNT = 400
    }
}
