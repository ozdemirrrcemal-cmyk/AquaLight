package com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.timeline

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode

data class LightProgramTimelinePhase(
    val type: LightProgramPhaseType,
    val label: String,
    val startMinute: Int,
    val endMinute: Int,
    val peakStartMinute: Int? = null,
    val peakEndMinute: Int? = null,
    val channelValues: LightCurveChannelValues,
    val transitionMode: LightCurveTransitionMode = LightCurveTransitionMode.LINEAR
) {

    val durationMinutes: Int
        get() = endMinute - startMinute

    val outputPercent: Int
        get() = maxOf(
            channelValues.red,
            channelValues.green,
            channelValues.blue,
            channelValues.white
        ).coerceIn(0, 100)

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}