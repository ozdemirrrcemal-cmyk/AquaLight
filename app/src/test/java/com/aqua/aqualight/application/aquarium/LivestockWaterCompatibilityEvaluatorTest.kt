package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivestockWaterCompatibilityEvaluatorTest {

    @Test
    fun evaluatesOnlyMeasuredParametersAndReportsOutOfRangeValues() {
        val requirements = LivestockWaterRequirements(
            temperatureC = LivestockParameterRange(22.0, 26.0),
            ph = LivestockParameterRange(6.0, 7.5),
            nitratePpm = LivestockParameterRange(maximum = 20.0),
            warningMode = LivestockWarningMode.SOFT
        )
        val water = AquariumWaterSnapshot(
            temperatureC = 24.0,
            ph = 8.0
        )

        val result = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements = requirements,
            water = water
        )

        assertEquals(2, result.checkedParameterCount)
        assertFalse(result.isCompatible)
        assertEquals(1, result.issues.size)
        assertEquals(AquariumWaterParameter.PH, result.issues.single().parameter)
        assertEquals(8.0, result.issues.single().measuredValue, 0.0)
        assertEquals(LivestockWarningMode.SOFT, result.warningMode)
        assertEquals(listOf(AquariumWaterParameter.NITRATE_PPM), result.missingMeasurements)
    }

    @Test
    fun openEndedMaximumRangeIsAcceptedBelowLimitAndRejectedAboveIt() {
        val requirements = LivestockWaterRequirements(
            nitratePpm = LivestockParameterRange(maximum = 20.0)
        )

        val safe = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements,
            AquariumWaterSnapshot(nitratePpm = 15.0)
        )
        val unsafe = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements,
            AquariumWaterSnapshot(nitratePpm = 25.0)
        )

        assertTrue(safe.isCompatible)
        assertFalse(unsafe.isCompatible)
        assertEquals(AquariumWaterParameter.NITRATE_PPM, unsafe.issues.single().parameter)
    }

    @Test
    fun missingMeasurementsDoNotCreateFalseWarnings() {
        val requirements = LivestockWaterRequirements(
            temperatureC = LivestockParameterRange(24.0, 28.0),
            ph = LivestockParameterRange(6.5, 7.5)
        )

        val result = LivestockWaterCompatibilityEvaluator.evaluate(
            requirements,
            AquariumWaterSnapshot()
        )

        assertEquals(0, result.checkedParameterCount)
        assertTrue(result.issues.isEmpty())
        assertEquals(
            listOf(AquariumWaterParameter.TEMPERATURE_C, AquariumWaterParameter.PH),
            result.missingMeasurements
        )
        assertFalse(result.isCompatible)
    }

    @Test
    fun inRangePartialMeasurementDoesNotClaimCompleteCompatibility() {
        val result = LivestockWaterCompatibilityEvaluator.evaluate(
            LivestockWaterRequirements(
                temperatureC = LivestockParameterRange(20.0, 26.0),
                ph = LivestockParameterRange(6.0, 7.0)
            ),
            AquariumWaterSnapshot(temperatureC = 24.0)
        )

        assertEquals(1, result.checkedParameterCount)
        assertTrue(result.issues.isEmpty())
        assertEquals(listOf(AquariumWaterParameter.PH), result.missingMeasurements)
        assertFalse(result.isCompatible)
    }

    @Test
    fun exclusiveEndpointsAndApproximateValuesDoNotCreateFalseCompatibility() {
        val strict = LivestockWaterCompatibilityEvaluator.evaluate(
            LivestockWaterRequirements(
                nitratePpm = LivestockParameterRange(
                    maximum = 20.0,
                    maximumInclusive = false,
                    sourceText = "<20"
                ),
                phosphatePpm = LivestockParameterRange(
                    approximate = true,
                    nominalValue = 0.05,
                    sourceText = "~0.05"
                )
            ),
            AquariumWaterSnapshot(nitratePpm = 20.0, phosphatePpm = 0.05)
        )

        assertEquals(1, strict.checkedParameterCount)
        assertEquals(AquariumWaterParameter.NITRATE_PPM, strict.issues.single().parameter)
        assertEquals(
            LivestockRequirementUnavailableReason.APPROXIMATE_ONLY,
            strict.unavailableRequirements.single().reason
        )
        assertFalse(strict.isCompatible)
    }

    @Test
    fun informationalAndUnknownModesNeverClaimCompatibility() {
        for (mode in listOf(LivestockWarningMode.INFORMATIONAL, LivestockWarningMode.UNKNOWN)) {
            val result = LivestockWaterCompatibilityEvaluator.evaluate(
                LivestockWaterRequirements(
                    ph = LivestockParameterRange(6.0, 7.0),
                    warningMode = mode
                ),
                AquariumWaterSnapshot(ph = 6.5)
            )

            assertEquals(0, result.checkedParameterCount)
            assertTrue(result.issues.isEmpty())
            assertFalse(result.isCompatible)
        }
    }
}
