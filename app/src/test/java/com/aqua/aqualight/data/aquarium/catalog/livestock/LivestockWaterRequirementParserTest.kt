package com.aqua.aqualight.data.aquarium.catalog.livestock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockWaterRequirementParserTest {

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
        assertEquals(0.05, phosphate?.minimum)
        assertEquals(0.05, phosphate?.maximum)
        assertTrue(phosphate?.approximate == true)
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
