package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve

data class LightCurveSeries(
    val channel: LightCurveChannel,
    val points: List<LightCurvePoint>,
    val isActive: Boolean = true
)