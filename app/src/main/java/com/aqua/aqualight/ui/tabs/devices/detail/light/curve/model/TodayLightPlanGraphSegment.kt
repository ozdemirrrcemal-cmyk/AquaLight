package com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model

data class TodayLightPlanGraphSegment(
    val id: String,
    val name: String,
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val outputPercent: Int,
    val transitionMode: LightCurveTransitionMode = LightCurveTransitionMode.LINEAR,
    val isCurrent: Boolean = false,
    val isNext: Boolean = false
)