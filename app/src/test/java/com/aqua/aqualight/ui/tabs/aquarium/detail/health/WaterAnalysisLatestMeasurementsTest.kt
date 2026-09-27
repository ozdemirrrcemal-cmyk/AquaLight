package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementResultId
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WaterAnalysisLatestMeasurementsTest {
    @Test
    fun missingMetricInLatestEventDoesNotBorrowOlderValue() {
        val latest = event(2L, WaterParameter.PH)
        val older = event(1L, WaterParameter.NITRATE)

        val displayed = WaterAnalysisLatestMeasurements.from(listOf(latest, older))

        assertEquals(setOf(WaterParameter.PH), displayed.keys)
        assertFalse(displayed.containsKey(WaterParameter.NITRATE))
    }

    @Test
    fun latestEventIsIndependentOfListOrderAndUsesCommitTimeBeforeId() {
        val olderCommit = event(99L, WaterParameter.NITRATE)
            .copy(measuredAtMillis = 10_000L, createdAtMillis = 10_000L)
        val laterCommit = event(2L, WaterParameter.PH)
            .copy(measuredAtMillis = 10_000L, createdAtMillis = 11_000L)

        assertEquals(
            laterCommit,
            WaterAnalysisLatestMeasurements.latestEvent(listOf(olderCommit, laterCommit))
        )
        assertEquals(
            setOf(WaterParameter.PH),
            WaterAnalysisLatestMeasurements.from(listOf(olderCommit, laterCommit)).keys
        )
    }

    private fun event(id: Long, parameter: WaterParameter): WaterAnalysisSnapshot =
        WaterAnalysisSnapshot(
            id = id,
            tankId = 10L,
            measuredAtMillis = id * 1_000L,
            temperatureCelsius = null,
            temperatureSource = null,
            measurements = listOf(
                WaterMeasurementSnapshot(
                    resultId = WaterMeasurementResultId(id, parameter),
                    parameter = parameter,
                    value = 7.0,
                    method = WaterMeasurementMethod.MANUAL,
                    testKitId = null,
                    basis = if (parameter == WaterParameter.PH) {
                        WaterMeasurementBasis.PH
                    } else {
                        WaterMeasurementBasis.NO3
                    },
                    unit = if (parameter == WaterParameter.PH) {
                        WaterMeasurementUnit.NONE
                    } else {
                        WaterMeasurementUnit.MG_L
                    },
                    canonicalValue = 7.0,
                    canonicalBasis = if (parameter == WaterParameter.PH) {
                        WaterMeasurementBasis.PH
                    } else {
                        WaterMeasurementBasis.NO3
                    },
                    canonicalUnit = if (parameter == WaterParameter.PH) {
                        WaterMeasurementUnit.NONE
                    } else {
                        WaterMeasurementUnit.MG_L
                    }
                )
            ),
            createdAtMillis = id * 1_000L
        )
}
