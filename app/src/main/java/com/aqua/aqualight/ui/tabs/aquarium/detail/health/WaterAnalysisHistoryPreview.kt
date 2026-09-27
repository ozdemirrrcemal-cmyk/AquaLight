package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot

internal sealed interface WaterAnalysisHistoryPreviewValue {
    data class Measurement(val source: WaterMeasurementSnapshot) :
        WaterAnalysisHistoryPreviewValue

    data class Temperature(val celsius: Double) : WaterAnalysisHistoryPreviewValue
}

internal object WaterAnalysisHistoryPreview {
    private const val MAX_METRICS = 3

    fun from(snapshot: WaterAnalysisSnapshot): List<WaterAnalysisHistoryPreviewValue> = buildList {
        snapshot.measurements.take(MAX_METRICS).forEach { measurement ->
            add(WaterAnalysisHistoryPreviewValue.Measurement(measurement))
        }
        if (size < MAX_METRICS) {
            snapshot.temperatureCelsius?.let { temperature ->
                add(WaterAnalysisHistoryPreviewValue.Temperature(temperature))
            }
        }
    }
}
