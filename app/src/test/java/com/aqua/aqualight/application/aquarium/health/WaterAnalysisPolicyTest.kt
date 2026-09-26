package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertThrows
import org.junit.Test

class WaterAnalysisPolicyTest {

    @Test
    fun requestIdentityMustBeCanonicalAndStable() {
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(
                WaterMeasurementInput(
                    parameter = WaterParameter.PH,
                    value = 7.0,
                    selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)
                )
            ),
            requestId = "not-a-uuid"
        )
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(input, nowMillis = VALID_TIME)
        }
    }

    @Test
    fun futureSamplesAllowOnlyOneMinuteOfClockTolerance() {
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME + 60_000L,
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(
                WaterMeasurementInput(
                    parameter = WaterParameter.PH,
                    value = 7.0,
                    selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)
                )
            )
        )
        WaterAnalysisPolicy.validate(input, nowMillis = VALID_TIME)
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(
                input.copy(measuredAtMillis = VALID_TIME + 60_001L),
                nowMillis = VALID_TIME
            )
        }
    }

    @Test
    fun sensorTemperatureCannotBeClaimedWithoutAProvenanceRecord() {
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = 25.0,
            temperatureSource = WaterTemperatureSource.SENSOR,
            measurements = listOf(
                WaterMeasurementInput(
                    parameter = WaterParameter.PH,
                    value = 7.0,
                    selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.PH)
                )
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(input, nowMillis = VALID_TIME)
        }
    }

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
