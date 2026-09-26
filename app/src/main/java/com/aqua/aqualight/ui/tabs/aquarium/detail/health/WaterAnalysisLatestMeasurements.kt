package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterParameter

internal object WaterAnalysisLatestMeasurements {
    /** The store sorts events newest first. Never borrow a value from an older event. */
    fun from(analyses: List<WaterAnalysisSnapshot>): Map<WaterParameter, WaterMeasurementSnapshot> =
        analyses.firstOrNull()
            ?.measurements
            ?.associateBy(WaterMeasurementSnapshot::parameter)
            .orEmpty()
}
