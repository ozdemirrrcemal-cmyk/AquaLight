package com.aqua.aqualight.application.aquarium.health.algae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgaeAnalysisEngineTest {

    @Test
    fun blackBeardUsesKnownCo2AndMaintenanceSignals() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.BLACK_BEARD,
                locations = setOf(
                    AlgaeObservationLocation.PLANTS,
                    AlgaeObservationLocation.EQUIPMENT
                ),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.INCREASING
            ),
            context = AlgaeTankContext(
                lightDurationState = AlgaeSignalState.NORMAL,
                lightIntensityState = AlgaeSignalState.NORMAL,
                hasCo2 = true,
                co2ScheduleKnown = true,
                co2TimingState = AlgaeSignalState.ELEVATED,
                waterChangeOverdue = true,
                filterMaintenanceOverdue = true,
                organicLoadState = AlgaeSignalState.ELEVATED,
                waterQuality = normalWater()
            )
        )

        assertEquals(AlgaeAnalysisPriority.ACTION_RECOMMENDED, result.priority)
        assertTrue(result.factors.any { factor ->
            factor.factor == AlgaeFactorId.CO2_STABILITY
        })
        assertTrue(result.factors.any { factor ->
            factor.factor == AlgaeFactorId.FILTER_MAINTENANCE
        })
        assertTrue(result.actions.any { action ->
            action.action == AlgaeActionId.VERIFY_CO2_STABILITY
        })
    }

    @Test
    fun missingCo2ScheduleIsMissingDataNotAnInventedCause() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.BLACK_BEARD,
                locations = setOf(AlgaeObservationLocation.PLANTS),
                density = AlgaeDensity.LOW,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                hasCo2 = true,
                co2ScheduleKnown = false
            )
        )

        assertTrue(AlgaeMissingData.CO2_SCHEDULE in result.missingData)
        assertTrue(result.factors.none { factor ->
            factor.factor == AlgaeFactorId.CO2_STABILITY
        })
        assertTrue(result.actions.any { action ->
            action.action == AlgaeActionId.ADD_CO2_SCHEDULE_DATA
        })
    }

    @Test
    fun greenSpotUsesLowPhosphateOnlyWhenWaterAnalysisSaysItIsLow() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.GREEN_SPOT,
                locations = setOf(AlgaeObservationLocation.FRONT_GLASS),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                lightDurationState = AlgaeSignalState.NORMAL,
                lightIntensityState = AlgaeSignalState.NORMAL,
                waterQuality = AlgaeWaterQualityContext(
                    nitrateState = AlgaeSignalState.NORMAL,
                    phosphateState = AlgaeSignalState.LOW,
                    nitriteState = AlgaeSignalState.NORMAL,
                    ammoniaState = AlgaeSignalState.NORMAL
                )
            )
        )

        assertTrue(result.factors.any { factor ->
            factor.factor == AlgaeFactorId.LOW_PHOSPHATE_CONTEXT
        })
        assertTrue(result.actions.any { action ->
            action.action == AlgaeActionId.REVIEW_NO3_PO4_BALANCE
        })
    }

    @Test
    fun unknownLightProfileIsMissingDataNotHighLight() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.GREEN_WATER,
                locations = setOf(AlgaeObservationLocation.WATER_COLUMN),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                waterQuality = normalWater()
            )
        )

        assertTrue(AlgaeMissingData.LIGHT_PROFILE in result.missingData)
        assertTrue(result.factors.none { factor ->
            factor.factor == AlgaeFactorId.LIGHT_DURATION ||
                factor.factor == AlgaeFactorId.LIGHT_INTENSITY
        })
    }

    private fun normalWater() = AlgaeWaterQualityContext(
        nitrateState = AlgaeSignalState.NORMAL,
        phosphateState = AlgaeSignalState.NORMAL,
        nitriteState = AlgaeSignalState.NORMAL,
        ammoniaState = AlgaeSignalState.NORMAL
    )
}
