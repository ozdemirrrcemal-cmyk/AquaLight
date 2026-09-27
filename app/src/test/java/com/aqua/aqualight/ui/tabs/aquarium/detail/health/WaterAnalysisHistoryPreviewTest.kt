package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementResultId
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class WaterAnalysisHistoryPreviewTest {

    @Test
    fun previewShowsActualNonDefaultMetricAndTemperature() {
        val preview = WaterAnalysisHistoryPreview.from(
            snapshot(listOf(measurement(WaterParameter.NITRITE)), temperature = SAMPLE_TEMP_C)
        )

        assertEquals(
            listOf(WaterParameter.NITRITE, SAMPLE_TEMP_C),
            preview.map { item ->
                when (item) {
                    is WaterAnalysisHistoryPreviewValue.Measurement -> item.source.parameter
                    is WaterAnalysisHistoryPreviewValue.Temperature -> item.celsius
                }
            }
        )
    }

    @Test
    fun previewLimitsCardsWithoutInventingMissingMeasurements() {
        val preview = WaterAnalysisHistoryPreview.from(
            snapshot(
                listOf(
                    measurement(WaterParameter.NITRITE),
                    measurement(WaterParameter.CALCIUM),
                    measurement(WaterParameter.MAGNESIUM),
                    measurement(WaterParameter.COPPER)
                ),
                temperature = SAMPLE_TEMP_C
            )
        )

        assertEquals(
            listOf(WaterParameter.NITRITE, WaterParameter.CALCIUM, WaterParameter.MAGNESIUM),
            preview.map { (it as WaterAnalysisHistoryPreviewValue.Measurement).source.parameter }
        )
        assertEquals(emptyList<WaterAnalysisHistoryPreviewValue>(),
            WaterAnalysisHistoryPreview.from(snapshot(emptyList(), temperature = null)))
    }

    private fun snapshot(
        measurements: List<WaterMeasurementSnapshot>,
        temperature: Double?
    ) = WaterAnalysisSnapshot(
        id = SAMPLE_ANALYSIS_ID,
        tankId = SAMPLE_TANK_ID,
        measuredAtMillis = SAMPLE_TIME_MILLIS,
        temperatureCelsius = temperature,
        temperatureSource = temperature?.let { WaterTemperatureSource.MANUAL },
        measurements = measurements,
        createdAtMillis = SAMPLE_TIME_MILLIS
    )

    private fun measurement(parameter: WaterParameter): WaterMeasurementSnapshot {
        val basis = when (parameter) {
            WaterParameter.NITRITE -> WaterMeasurementBasis.NO2
            WaterParameter.CALCIUM -> WaterMeasurementBasis.CA
            WaterParameter.MAGNESIUM -> WaterMeasurementBasis.MG
            WaterParameter.COPPER -> WaterMeasurementBasis.CU
            else -> error("Unexpected test parameter: $parameter")
        }
        return WaterMeasurementSnapshot(
            resultId = WaterMeasurementResultId(SAMPLE_ANALYSIS_ID, parameter),
            parameter = parameter,
            value = SAMPLE_MEASUREMENT_VALUE,
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = basis,
            unit = WaterMeasurementUnit.MG_L,
            canonicalValue = null,
            canonicalBasis = basis,
            canonicalUnit = WaterMeasurementUnit.MG_L
        )
    }

    private companion object {
        const val SAMPLE_ANALYSIS_ID = 1L
        const val SAMPLE_TANK_ID = 2L
        const val SAMPLE_TIME_MILLIS = 1_800_000_000_000L
        const val SAMPLE_TEMP_C = 24.0
        const val SAMPLE_MEASUREMENT_VALUE = 1.0
    }
}
