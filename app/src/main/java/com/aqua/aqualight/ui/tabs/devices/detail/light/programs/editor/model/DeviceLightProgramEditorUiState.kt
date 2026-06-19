package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.data.devices.light.curve.model.LightCurvePoint
import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode

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
        get() = LightProgramDraft(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues.normalized(),
            repeatMode = repeatMode,
            selectedDays = selectedDays,
            transitionMode = transitionMode
        )

    val graphState: LightCurveGraphState
        get() = LightCurveGraphState(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues.normalized(),
            currentTime = previewSimulationTime ?: currentDeviceTime,
            transitionMode = transitionMode
        )

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
