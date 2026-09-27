package com.aqua.aqualight.application.aquarium.health

import org.junit.Assert.assertEquals
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
    fun tanAndDirectFreeAmmoniaRemainIndependentResultsInOneEvent() {
        val total = WaterMeasurementInput(
            parameter = WaterParameter.TOTAL_AMMONIA_NITROGEN,
            value = 1.0,
            selection = WaterMeasurementCatalog.defaultSelection(
                WaterParameter.TOTAL_AMMONIA_NITROGEN
            )
        )
        val free = WaterMeasurementInput(
            parameter = WaterParameter.FREE_AMMONIA_NH3,
            value = 0.08,
            selection = WaterMeasurementCatalog.defaultSelection(WaterParameter.FREE_AMMONIA_NH3)
        )
        val input = WaterAnalysisInput(
            tankId = 1L,
            measuredAtMillis = VALID_TIME,
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(total, free)
        )

        assertEquals(input, WaterAnalysisPolicy.validate(input, nowMillis = VALID_TIME))
        assertEquals(WaterMeasurementBasis.TAN_N, total.selection.basis)
        assertEquals(WaterMeasurementBasis.FREE_NH3, free.selection.basis)
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(input.copy(measurements = listOf(total, total)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WaterAnalysisPolicy.validate(
                input.copy(measurements = listOf(
                    WaterMeasurementInput(
                        parameter = WaterParameter.AMMONIA_AMMONIUM,
                        value = 1.0,
                        selection = WaterMeasurementCatalog.defaultSelection(
                            WaterParameter.AMMONIA_AMMONIUM
                        )
                    )
                )),
                nowMillis = VALID_TIME
            )
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
