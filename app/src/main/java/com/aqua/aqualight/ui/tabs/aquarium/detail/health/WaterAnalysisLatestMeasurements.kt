package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterParameter

internal object WaterAnalysisLatestMeasurements {
    fun latestEvent(analyses: List<WaterAnalysisSnapshot>): WaterAnalysisSnapshot? =
        analyses.maxWithOrNull(
            compareBy<WaterAnalysisSnapshot>(WaterAnalysisSnapshot::measuredAtMillis)
                .thenBy(WaterAnalysisSnapshot::createdAtMillis)
                .thenBy(WaterAnalysisSnapshot::id)
        )

    /** Never borrow a value from an older event. */
    fun from(analyses: List<WaterAnalysisSnapshot>): Map<WaterParameter, WaterMeasurementSnapshot> =
        latestEvent(analyses)
            ?.measurements
            ?.associateBy(WaterMeasurementSnapshot::parameter)
            .orEmpty()
}
