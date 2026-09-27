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
                parameterState = state,
                requestId = REQUEST_ID
            )
        )

        assertTrue(result is WaterAnalysisInputBuildResult.Success)
        val input = (result as WaterAnalysisInputBuildResult.Success).input
        assertEquals(1, input.measurements.size)
        assertEquals(7.4, input.measurements.single().value, 0.0)
        assertEquals("12", state.parameterValues[WaterTestParameterId.NITRATE])
        assertEquals(REQUEST_ID, input.requestId)
    }

    @Test
    fun invalidPhAndTemperatureAreFieldFailuresAndTheDraftIsUntouched() {
        val state = WaterAnalysisParameterState().apply { parameterValues[WaterTestParameterId.PH] = "14,01" }
        val request = WaterAnalysisInputBuildRequest(1, 1_780_000_000_000, "", WaterTemperatureSource.MANUAL,
            setOf(WaterTestParameterId.PH), state, REQUEST_ID)
        assertEquals(WaterAnalysisInputBuildResult.Failure.InvalidParameterValue(WaterTestParameterId.PH),
            WaterAnalysisInputBuilder.build(request))
        assertEquals("14,01", state.parameterValues[WaterTestParameterId.PH])
        state.parameterValues[WaterTestParameterId.PH] = "0"
        assertEquals(WaterAnalysisInputBuildResult.Failure.InvalidTemperature,
            WaterAnalysisInputBuilder.build(request.copy(temperatureText = "101")))
        val cold = WaterAnalysisInputBuilder.build(request.copy(temperatureText = "-2,5"))
            as WaterAnalysisInputBuildResult.Success
        assertEquals(-2.5, cold.input.temperatureCelsius!!, 0.0)
        assertEquals(0.0, cold.input.measurements.single().value, 0.0)
        assertEquals(null, WaterAnalysisValueParser.parse("9".repeat(65)))
        assertEquals(null, WaterAnalysisValueParser.parse("<0.1"))
    }

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000"
    }
}
