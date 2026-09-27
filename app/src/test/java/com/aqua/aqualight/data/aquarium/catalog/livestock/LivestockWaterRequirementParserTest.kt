package com.aqua.aqualight.data.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.LivestockRequirementParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockWaterRequirementParserTest {

    @Test
    fun fullExpressionMustBeValidAndReversedBoundsAreNeverRepaired() {
        listOf("28–24", "20 mg/L", "200–400+", "<20 garbage", "20–30–40", "NaN", "Infinity", "1e309")
            .forEach { raw ->
                assertTrue(raw,
                    LivestockWaterRequirementParser.parseResult(raw) is LivestockRequirementParseResult.Unparseable)
                assertNull(LivestockWaterRequirementParser.parseRange(raw))
            }
        assertEquals(LivestockRequirementParseResult.Missing, LivestockWaterRequirementParser.parseResult(" "))
    }

    @Test
    fun hyphenIsASeparatorAndSignedBoundsArePreserved() {
        val range = requireNotNull(LivestockWaterRequirementParser.parseRange("24-28"))
        assertEquals(24.0, range.minimum)
        assertEquals(28.0, range.maximum)
        val signed = requireNotNull(LivestockWaterRequirementParser.parseRange("-5 - -1"))
        assertEquals(-5.0, signed.minimum)
        assertEquals(-1.0, signed.maximum)
        assertTrue(requireNotNull(LivestockWaterRequirementParser.parseRange("<=7,5")).contains(7.5))
        assertTrue(requireNotNull(LivestockWaterRequirementParser.parseRange(">=7,5")).contains(7.5))
    }

    @Test
    fun parsesClosedRangesUsedByTemperaturePhAndSpecificGravity() {
        val temperature = LivestockWaterRequirementParser.parseRange("24–28")
        val ph = LivestockWaterRequirementParser.parseRange("6.0–7.5")
        val sg = LivestockWaterRequirementParser.parseRange("1.023–1.026")

        assertEquals(24.0, temperature?.minimum)
        assertEquals(28.0, temperature?.maximum)
        assertEquals(6.0, ph?.minimum)
        assertEquals(7.5, ph?.maximum)
        assertEquals(1.023, sg?.minimum)
        assertEquals(1.026, sg?.maximum)
    }

    @Test
    fun parsesUpperBoundAndApproximateTargets() {
        val nitrate = LivestockWaterRequirementParser.parseRange("<20")
        val phosphate = LivestockWaterRequirementParser.parseRange("~0.05")

        assertNull(nitrate?.minimum)
        assertEquals(20.0, nitrate?.maximum)
        assertNull(phosphate?.minimum)
        assertNull(phosphate?.maximum)
        assertEquals(0.05, phosphate?.nominalTarget)
        assertTrue(phosphate?.approximate == true)
        assertFalse(requireNotNull(nitrate).contains(20.0))
        assertTrue(nitrate.contains(19.9))
    }

    @Test
    fun strictAndInclusiveEndpointsRemainDistinct() {
        val greater = requireNotNull(LivestockWaterRequirementParser.parseRange(">7"))
        val atLeast = requireNotNull(LivestockWaterRequirementParser.parseRange("≥7"))
        val less = requireNotNull(LivestockWaterRequirementParser.parseRange("<7"))
        val atMost = requireNotNull(LivestockWaterRequirementParser.parseRange("≤7"))
        assertFalse(greater.contains(7.0))
        assertTrue(atLeast.contains(7.0))
        assertFalse(less.contains(7.0))
        assertTrue(atMost.contains(7.0))
    }

    @Test
    fun rangeContainmentRejectsOutOfRangeValues() {
        val range = requireNotNull(LivestockWaterRequirementParser.parseRange("20–26"))

        assertTrue(range.contains(20.0))
        assertTrue(range.contains(23.5))
        assertTrue(range.contains(26.0))
        assertFalse(range.contains(19.9))
        assertFalse(range.contains(26.1))
    }
}
