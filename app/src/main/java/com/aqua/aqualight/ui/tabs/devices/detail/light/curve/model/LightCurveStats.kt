package com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model

data class LightCurveStats(
    val outputPercent: Int,
    val estimatedPowerWatts: Double,
    val durationHours: Double
)