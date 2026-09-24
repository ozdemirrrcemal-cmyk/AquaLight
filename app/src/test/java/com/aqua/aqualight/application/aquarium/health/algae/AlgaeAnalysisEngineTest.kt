package com.aqua.aqualight.application.aquarium.health.algae

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgaeAnalysisEngineTest {

    @Test
    fun blackBeardUsesExplicitCo2AndMaintenanceSignals() {
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
                hasCo2 = true,
                co2ScheduleKnown = true,
                co2Stability = AlgaeCo2Stability.UNSTABLE,
                waterChangeOverdue = true,
                filterMaintenanceOverdue = true,
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
                co2ScheduleKnown = false,
                co2Stability = AlgaeCo2Stability.UNKNOWN
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
    fun greenSpotUsesPhosphateImbalanceOnlyWhenWaterAnalysisSupportsIt() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.GREEN_SPOT,
                locations = setOf(AlgaeObservationLocation.FRONT_GLASS),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                lightDurationMinutes = 8 * 60,
                lightExposureState = AlgaeLightExposureState.WITHIN_RANGE,
                waterQuality = AlgaeWaterQualityContext(
                    nitrateState = WaterParameterState.NORMAL,
                    phosphateState = WaterParameterState.LOW,
                    nitriteState = WaterParameterState.NORMAL,
                    ammoniaState = WaterParameterState.NORMAL
                )
            )
        )

        assertTrue(result.factors.any { factor ->
            factor.factor == AlgaeFactorId.PHOSPHATE_IMBALANCE_CONTEXT
        })
        assertTrue(result.actions.any { action ->
            action.action == AlgaeActionId.REVIEW_NO3_PO4_BALANCE
        })
    }

    @Test
    fun unknownLightScheduleIsMissingDataNotHighLight() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.GREEN_WATER,
                locations = setOf(AlgaeObservationLocation.WATER_COLUMN),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                lightExposureState = AlgaeLightExposureState.UNKNOWN,
                waterQuality = normalWater()
            )
        )

        assertTrue(AlgaeMissingData.LIGHT_SCHEDULE in result.missingData)
        assertTrue(result.factors.none { factor ->
            factor.factor == AlgaeFactorId.LIGHT_DURATION ||
                factor.factor == AlgaeFactorId.LIGHT_INTENSITY
        })
    }

    @Test
    fun cyanobacteriaUsesLowFlowOnlyWhenFlowWasObserved() {
        val result = AlgaeAnalysisEngine.analyze(
            observation = AlgaeObservationInput(
                algaeType = AlgaeTypeId.CYANOBACTERIA,
                locations = setOf(AlgaeObservationLocation.SUBSTRATE),
                density = AlgaeDensity.MEDIUM,
                trend = AlgaeTrend.STABLE
            ),
            context = AlgaeTankContext(
                flowState = AlgaeFlowState.LOW,
                waterQuality = normalWater()
            )
        )

        assertTrue(result.factors.any { factor ->
            factor.factor == AlgaeFactorId.FLOW_OR_OXYGENATION
        })
        assertTrue(result.actions.any { action ->
            action.action == AlgaeActionId.IMPROVE_FLOW_OR_OXYGENATION
        })
    }

    private fun normalWater() = AlgaeWaterQualityContext(
        nitrateState = WaterParameterState.NORMAL,
        phosphateState = WaterParameterState.NORMAL,
        nitriteState = WaterParameterState.NORMAL,
        ammoniaState = WaterParameterState.NORMAL
    )
}
