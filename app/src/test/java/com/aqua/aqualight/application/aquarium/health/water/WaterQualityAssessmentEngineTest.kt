package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.health.context.HealthPlantContext
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementInput
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSelection
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.context.HealthLivestockContext
import com.aqua.aqualight.application.aquarium.health.context.HealthTankFacts
import com.aqua.aqualight.application.aquarium.health.context.HealthCatalogRevisions
import com.aqua.aqualight.application.aquarium.health.context.HealthContextCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterQualityAssessmentEngineTest {
    @Test
    fun `no entities or unresolved measurements cannot become normal`() {
        val empty = WaterQualityAssessmentEngine.assess(input(), context(emptyList()))
        assertNull(empty.hazardSeverity)
        assertEquals(WaterAssessmentCoverage.NONE, empty.coverage)
        val unresolved = WaterQualityAssessmentEngine.assess(input(WaterMeasurementMethod.DIGITAL), context())
        assertNull(unresolved.hazardSeverity)
        assertEquals(WaterAssessmentCoverage.NONE, unresolved.coverage)
        assertEquals(WaterAssessmentGap.SOURCE_UNRESOLVED,
            unresolved.findings.single { it.parameter == AquariumWaterParameter.PH }.gap)
        assertEquals(WaterAssessmentGap.RULE_MISSING, unresolved.chemistryGap)
    }

    @Test
    fun `strict source bounds remain advisory and missing values cannot dilute them`() {
        val result = WaterQualityAssessmentEngine.assess(input(), context())
        val ph = result.findings.single { it.parameter == AquariumWaterParameter.PH }
        assertEquals(WaterAssessmentDirection.ABOVE, ph.direction)
        assertEquals(WaterHazardSeverity.ADVISORY, result.hazardSeverity)
        assertEquals(WaterAssessmentCoverage.PARTIAL, result.coverage)
        assertEquals("VERIFY_TEST_RESULT", result.recommendations.single().code)
        assertTrue(result.findings.any { it.gap != null })
    }

    @Test
    fun `conflicting strict bounds are retained without measurements or quantity multiplication`() {
        val first = animal(1, LivestockParameterRange(maximum = 7.0, maximumInclusive = false))
        val second = animal(2, LivestockParameterRange(minimum = 7.0))
        val sample = input().copy(measurements = emptyList())
        val result = WaterQualityAssessmentEngine.assess(sample, context(listOf(first, second)))
        assertNull(result.hazardSeverity)
        assertEquals(1, result.conflicts.size)
        assertFalse(result.conflicts.single().authoritative)
        val reversed = WaterQualityAssessmentEngine.assess(sample, context(listOf(second.copy(quantity = 30), first)))
        assertEquals(result, reversed)
    }

    @Test
    fun `inclusive touch is compatible requirement overlap but incomplete coverage stays visible`() {
        val animals = listOf(animal(1, LivestockParameterRange(maximum = 7.0)),
            animal(2, LivestockParameterRange(minimum = 7.0)))
        val result = WaterQualityAssessmentEngine.assess(input(), context(animals))
        assertTrue(result.conflicts.isEmpty())
        assertEquals(WaterHazardSeverity.NONE, result.hazardSeverity)
        assertEquals(WaterAssessmentCoverage.PARTIAL, result.conflictCoverage)
    }

    @Test
    fun `unknown tank custom identity and approximate ranges remain explicit gaps`() {
        val custom = animal(1).copy(resolution = HealthEntityResolution.CUSTOM_UNVERIFIED)
        val approximate = animal(2, LivestockParameterRange(6.0, 7.0, approximate = true))
        val result = WaterQualityAssessmentEngine.assess(input(), context(listOf(custom, approximate)))
        assertNull(result.hazardSeverity)
        assertTrue(result.findings.any { it.gap == WaterAssessmentGap.CUSTOM_UNVERIFIED })
        assertTrue(result.findings.any { it.gap == WaterAssessmentGap.INFORMATIONAL })
        assertTrue(result.conflicts.isEmpty())
        val unknown = WaterQualityAssessmentEngine.assess(input(), context(environment = null))
        assertTrue(unknown.findings.all { it.gap == WaterAssessmentGap.UNKNOWN_TANK_TYPE })
    }

    @Test
    fun `catalog ppm columns do not acquire nitrate or phosphate chemical meaning`() {
        val animal = animal(1).copy(requirements = LivestockWaterRequirements(
            nitratePpm = LivestockParameterRange(maximum = 1.0),
            phosphatePpm = LivestockParameterRange(maximum = 1.0)))
        val input = input().copy(measurements = listOf(
            WaterMeasurementInput(WaterParameter.NITRATE, 30.0, WaterMeasurementSelection(
                WaterMeasurementMethod.MANUAL, null, WaterMeasurementBasis.NO3, WaterMeasurementUnit.MG_L))))
        val result = WaterQualityAssessmentEngine.assess(input, context(listOf(animal)))
        assertNull(result.hazardSeverity)
        assertEquals(WaterAssessmentGap.UNKNOWN_REQUIREMENT_SEMANTIC,
            result.findings.single { it.parameter == AquariumWaterParameter.NITRATE_PPM }.gap)
    }

    @Test
    fun `plant readiness and verified fields control each comparison`() {
        val plants = listOf(plant(1), plant(2, HealthEntityResolution.PARTIAL),
            plant(3).let { it.copy(care = it.care?.copy(verifiedCareFields = emptySet())) })
        val result = WaterQualityAssessmentEngine.assess(input(), plantContext(plants))
        val ph = result.findings.filter { it.parameter == AquariumWaterParameter.PH }.associateBy { it.entity.localId }
        assertEquals(WaterAssessmentDirection.ABOVE, ph.getValue(1).direction)
        assertEquals(WaterAssessmentGap.PARTIAL_PLANT, ph.getValue(2).gap)
        assertEquals(WaterAssessmentGap.REQUIREMENT_MISSING, ph.getValue(3).gap)
        assertEquals(WaterHazardSeverity.ADVISORY, result.hazardSeverity)
    }

    @Test
    fun `habitat mismatch remains separate and never numerically compares incompatible habitats`() {
        val animal = animal(1).copy(waterGroup = "Marine")
        val result = WaterQualityAssessmentEngine.assess(input(), context(listOf(animal)))
        assertEquals(1, result.habitatConflicts.size)
        assertTrue(result.findings.all { it.gap == WaterAssessmentGap.INCOMPATIBLE_HABITAT })
        assertNull(result.hazardSeverity)
        val marinePlant = WaterQualityAssessmentEngine.assess(input(), plantContext(listOf(plant(1)), "Marine"))
        assertTrue(marinePlant.findings.all { it.gap == WaterAssessmentGap.UNKNOWN_HABITAT })
    }

    @Test
    fun `GH source endpoint survives canonical round trip without rounding into an advisory`() {
        val exact = 7.1
        val animal = animal(1).copy(requirements = LivestockWaterRequirements(
            ghDgh = LivestockParameterRange(exact, exact)))
        val input = input().copy(measurements = listOf(WaterMeasurementInput(WaterParameter.GH, exact,
            WaterMeasurementSelection(WaterMeasurementMethod.MANUAL, null,
                WaterMeasurementBasis.GH, WaterMeasurementUnit.DGH))))
        val result = WaterQualityAssessmentEngine.assess(input, context(listOf(animal)))
        val finding = result.findings.single { it.parameter == AquariumWaterParameter.GH_DGH }
        assertEquals(exact, finding.measuredValue!!, 0.0)
        assertEquals(WaterAssessmentDirection.WITHIN, finding.direction)
    }

    private fun plant(id: Long, resolution: HealthEntityResolution = HealthEntityResolution.RESOLVED) =
        HealthPlantContext(id, "plant-$id", "Plant", resolution,
            AquariumPlantCare("", "", "", "", null, null, 5.0, 6.0, null, null, null, null,
                "", "", null, null, "VERIFIED", true, setOf("pHMin", "pHMax")))

    private fun plantContext(plants: List<HealthPlantContext>, environment: String = "Freshwater") =
        AquariumHealthContext(HealthContextCapture(1_800_000_000_000, "revision"),
            HealthTankFacts(7, "Freshwater Planted", environment, null, null),
            HealthCatalogRevisions("plants-v1", "animals-v1"), plants, emptyList(), emptyList())

    private fun input(method: WaterMeasurementMethod = WaterMeasurementMethod.MANUAL) = WaterAnalysisInput(
        7, 1_800_000_000_000, null, null,
        listOf(WaterMeasurementInput(WaterParameter.PH, 7.0,
            WaterMeasurementSelection(method, null, WaterMeasurementBasis.PH, WaterMeasurementUnit.NONE)))
    )

    private fun animal(id: Long, range: LivestockParameterRange = LivestockParameterRange(maximum = 7.0,
        maximumInclusive = false)) = HealthLivestockContext(id, "catalog-$id", "Animal", 1,
        HealthEntityResolution.RESOLVED, "Freshwater", LivestockWaterRequirements(ph = range))

    private fun context(
        animals: List<HealthLivestockContext> = listOf(animal(1)),
        environment: String? = "Freshwater"
    ) = AquariumHealthContext(HealthContextCapture(1_800_000_000_000, "revision"),
        HealthTankFacts(7, "Freshwater Fish", environment, null, null),
        HealthCatalogRevisions("plants-v1", "animals-v1"), emptyList(), animals, emptyList())
}
