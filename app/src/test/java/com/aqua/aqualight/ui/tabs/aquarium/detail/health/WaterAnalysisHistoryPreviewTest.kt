package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementBasis
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementMethod
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementUnit
import com.aqua.aqualight.application.aquarium.health.WaterParameter
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class WaterAnalysisHistoryPreviewTest {

    @Test
    fun previewShowsActualNonDefaultMetricAndTemperature() {
        val preview = WaterAnalysisHistoryPreview.from(
            snapshot(listOf(measurement(WaterParameter.NITRITE)), temperature = 24.0)
        )

        assertEquals(
            listOf(WaterParameter.NITRITE, 24.0),
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
                temperature = 24.0
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
        id = 1L,
        tankId = 2L,
        measuredAtMillis = 1_800_000_000_000L,
        temperatureCelsius = temperature,
        temperatureSource = temperature?.let { WaterTemperatureSource.MANUAL },
        measurements = measurements,
        createdAtMillis = 1_800_000_000_000L
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
            parameter = parameter,
            value = 1.0,
            method = WaterMeasurementMethod.MANUAL,
            testKitId = null,
            basis = basis,
            unit = WaterMeasurementUnit.MG_L,
            canonicalValue = null,
            canonicalBasis = basis,
            canonicalUnit = WaterMeasurementUnit.MG_L
        )
    }
}
