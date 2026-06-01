package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve

data class LightCurveChartData(
    val displayMode: LightCurveDisplayMode = LightCurveDisplayMode.SIMPLE,
    val currentTimeMinutes: Int? = null,
    val series: List<LightCurveSeries> = emptyList()
) {
    val hasData: Boolean
        get() = series.any { item ->
            item.points.isNotEmpty()
        }
}