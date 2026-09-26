package com.aqua.aqualight.data.aquarium.catalog.livestock

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockRequirementUnavailableReason
import com.aqua.aqualight.application.aquarium.LivestockWarningMode
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
        assertFalse(requireNotNull(nitrate).maximumInclusive)
        assertNull(phosphate?.minimum)
        assertNull(phosphate?.maximum)
        assertEquals(0.05, phosphate?.nominalValue)
        assertTrue(phosphate?.approximate == true)
        assertFalse(requireNotNull(phosphate).isComparable)
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

    @Test
    fun strictAndInclusiveBoundsRespectExactEndpoint() {
        val cases = listOf(
            Triple("<20", false, true),
            Triple("≤20", true, true),
            Triple("<=20", true, true),
            Triple(">20", false, false),
            Triple("≥20", true, false),
            Triple(">=20", true, false)
        )

        for ((source, includesEndpoint, upper) in cases) {
            val range = requireNotNull(LivestockWaterRequirementParser.parseRange(source))
            assertEquals(source, range.sourceText)
            assertEquals(source, includesEndpoint, range.contains(20.0))
            assertTrue(range.contains(if (upper) 19.9 else 20.1))
            assertFalse(range.contains(if (upper) 20.1 else 19.9))
        }
    }

    @Test
    fun approximateIntervalsAndBareSingleValuesRemainUncomparable() {
        val approximate = requireNotNull(LivestockWaterRequirementParser.parseRange("~20–26"))
        val nominal = requireNotNull(LivestockWaterRequirementParser.parseRange("20"))
        val about = requireNotNull(LivestockWaterRequirementParser.parseRange("about 20"))

        assertEquals(20.0, approximate.minimum)
        assertEquals(26.0, approximate.maximum)
        assertFalse(approximate.isComparable)
        assertEquals(20.0, nominal.nominalValue)
        assertEquals(20.0, about.nominalValue)
        assertFalse(nominal.contains(20.0))
    }

    @Test
    fun malformedAndReversedSourcesRetainTypedFailure() {
        for (source in listOf("26–20", "20–26 extra 30", "200–400+", "<20 ppm", "1e309")) {
            assertEquals(
                LivestockWaterRequirementParser.RangeParseResult.Unparseable(source),
                LivestockWaterRequirementParser.parseRangeResult(source)
            )
        }

        val parsed = LivestockWaterRequirementParser.parse(
            entry(ph = "26–20", warningMode = "not-a-mode")
        )
        assertNull(parsed.ph)
        assertEquals(LivestockWarningMode.UNKNOWN, parsed.warningMode)
        assertEquals("test-fish", parsed.catalogEntryId)
        assertEquals(2, parsed.unavailableRequirements.size)
        assertTrue(parsed.unavailableRequirements.any { failure ->
            failure.parameter == AquariumWaterParameter.PH &&
                failure.sourceText == "26–20" &&
                failure.reason == LivestockRequirementUnavailableReason.UNPARSEABLE_REQUIREMENT
        })
        assertTrue(parsed.unavailableRequirements.any { failure ->
            failure.reason == LivestockRequirementUnavailableReason.UNKNOWN_WARNING_MODE
        })
    }

    private fun entry(ph: String, warningMode: String) = LivestockCatalogEntry(
        id = "test-fish",
        category = "Fish",
        commonName = "Test fish",
        turkishName = null,
        scientificName = null,
        recordType = null,
        waterGroup = null,
        temperatureC = null,
        ph = ph,
        ghDgh = null,
        khDkh = null,
        tdsPpm = null,
        specificGravity = null,
        alkalinityDkh = null,
        calciumPpm = null,
        magnesiumPpm = null,
        nitratePpm = null,
        phosphatePpm = null,
        par = null,
        flow = null,
        note = null,
        confidence = null,
        warningMode = warningMode
    )
}
