package com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve

data class LightCurvePoint(
    val minuteOfDay: Int,
    val intensityPercent: Int,
    val isMajor: Boolean = true
)