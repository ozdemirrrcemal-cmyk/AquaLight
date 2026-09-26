package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysisPolicyTest {

    @Test
    fun duplicateParametersAreRejected() {
        val measurement = WaterMeasurementInput(
            parameter = WaterParameter.PH,
            value = 7.0,
            selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)
        )
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(measurement, measurement)
        )

        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(input)
        }
    }

    @Test
    fun temperatureSourceAndValueMustBeConsistent() {
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = null,
            temperatureSource = WaterTemperatureSource.MANUAL,
            measurements = listOf(
                WaterMeasurementInput(
                    parameter = WaterParameter.PH,
                    value = 7.0,
                    selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(input)
        }
    }

    private companion object {
        const val VALID_TIME = 1_790_000_000_000L
    }
}
