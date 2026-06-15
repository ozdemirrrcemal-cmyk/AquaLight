package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.core.programs.model.RepeatMode

data class DeviceLightProgramEditorUiState(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val transitionMode: LightCurveTransitionMode,
    val previewSpeed: PreviewSpeed,
    val currentDeviceTime: LightCurvePoint,
    val previewSimulationTime: LightCurvePoint? = null,
    val isPreviewRunning: Boolean = false,
    val previewProgressPercent: Int = 0
) {
    val draft: LightProgramDraft
        get() = LightProgramDraft(start, peakStart, peakEnd, end, channelValues.normalized(), repeatMode, selectedDays, LightCurveTransitionMode.NATURAL)

    val graphState: LightCurveGraphState
        get() = LightCurveGraphState(start, peakStart, peakEnd, end, channelValues.normalized(), previewSimulationTime ?: currentDeviceTime, LightCurveTransitionMode.NATURAL)

    companion object {
        fun default(): DeviceLightProgramEditorUiState {
            val draft = LightProgramDraft.default()
            return DeviceLightProgramEditorUiState(
                start = draft.start,
                peakStart = draft.peakStart,
                peakEnd = draft.peakEnd,
                end = draft.end,
                channelValues = draft.channelValues,
                repeatMode = draft.repeatMode,
                selectedDays = draft.selectedDays,
                transitionMode = LightCurveTransitionMode.NATURAL,
                previewSpeed = PreviewSpeed.ONE_MINUTE,
                currentDeviceTime = LightCurvePoint.of(0, 0)
            )
        }
    }
}
