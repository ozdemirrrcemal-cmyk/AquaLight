package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaterAnalysisInputBuilderTest {
    @Test
    fun hiddenValuesRemainInDraftButCannotEnterCurrentTankEvent() {
        val state = WaterAnalysisParameterState().apply {
            parameterValues[WaterTestParameterId.PH] = "7,4"
            parameterValues[WaterTestParameterId.NITRATE] = "12"
        }

        val result = WaterAnalysisInputBuilder.build(
            WaterAnalysisInputBuildRequest(
                tankId = 1L,
                measuredAtMillis = 1_780_000_000_000L,
                temperatureText = "",
                temperatureSource = WaterTemperatureSource.MANUAL,
                visibleParameterIds = setOf(WaterTestParameterId.PH),
                parameterState = state
            )
        )

        assertTrue(result is WaterAnalysisInputBuildResult.Success)
        val input = (result as WaterAnalysisInputBuildResult.Success).input
        assertEquals(1, input.measurements.size)
        assertEquals(7.4, input.measurements.single().value, 0.0)
        assertEquals("12", state.parameterValues[WaterTestParameterId.NITRATE])
    }
}
