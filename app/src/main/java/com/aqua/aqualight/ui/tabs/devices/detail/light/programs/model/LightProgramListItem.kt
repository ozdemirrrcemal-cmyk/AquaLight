package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData

data class LightProgramListItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val scheduleSummary: String = "",
    val startTimeLabel: String = "",
    val rampLabel: String = "",
    val endTimeLabel: String = "",
    val repeatLabel: String = "",
    val peakLabel: String = "",
    val redLabel: String = "",
    val greenLabel: String = "",
    val blueLabel: String = "",
    val whiteLabel: String = "",
    val photoperiodLabel: String = "",
    val isEnabled: Boolean = false,
    val curveData: LightCurveChartData? = null
)