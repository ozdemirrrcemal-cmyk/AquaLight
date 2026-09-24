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
                lightDurationMinutes = 8 * 60,
                lightIntensityPercent = 70,
                hasCo2 = true,
                co2ScheduleKnown = true,
                co2LeadMinutesBeforeLight = 30,
                daysSinceWaterChange = 12,
                daysSinceFilterMaintenance = 35,
                waterQuality = AlgaeWaterQualityContext(
                    nitrateState = WaterParameterState.NORMAL,
                    phosphateState = WaterParameterState.NORMAL,
                    nitriteState = WaterParameterState.NORMAL,
                    ammoniaState = WaterParameterState.NORMAL
                )
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
                lightDurationMinutes = 8 * 60,
                lightIntensityPercent = 70,
                waterQuality = AlgaeWaterQualityContext(
                    nitrateState = WaterParameterState.NORMAL,
                    phosphateState = WaterParameterState.LOW,
                    nitriteState = WaterParameterState.NORMAL,
                    ammoniaState = WaterParameterState.NORMAL
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
}
