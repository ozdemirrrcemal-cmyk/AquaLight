package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveChannelValues
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveGraphState
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurvePoint
import com.aqua.aqualight.ui.tabs.devices.detail.light.curve.model.LightCurveTransitionMode

data class DeviceLightProgramEditorUiState(
    val start: LightCurvePoint,
    val peakStart: LightCurvePoint,
    val peakEnd: LightCurvePoint,
    val end: LightCurvePoint,
    val channelValues: LightCurveChannelValues,
    val repeatMode: RepeatMode,
    val selectedDays: Set<Int>,
    val moonlightSettings: MoonlightSettings,
    val cloudSimulationSettings: CloudSimulationSettings,
    val transitionMode: LightCurveTransitionMode,
    val previewSpeed: PreviewSpeed,
    val currentDeviceTime: LightCurvePoint,
    val previewSimulationTime: LightCurvePoint? = null,
    val isPreviewRunning: Boolean = false
) {
    val graphState: LightCurveGraphState
        get() = LightCurveGraphState(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            currentTime = previewSimulationTime ?: currentDeviceTime,
            transitionMode = transitionMode
        )

    val draft: LightProgramDraft
        get() = LightProgramDraft(
            start = start,
            peakStart = peakStart,
            peakEnd = peakEnd,
            end = end,
            channelValues = channelValues,
            repeatMode = repeatMode,
            selectedDays = selectedDays,
            moonlightSettings = moonlightSettings,
            cloudSimulationSettings = cloudSimulationSettings,
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
                moonlightSettings = draft.moonlightSettings,
                cloudSimulationSettings = draft.cloudSimulationSettings,
                transitionMode = draft.transitionMode,
                previewSpeed = PreviewSpeed.ONE_MINUTE,
                currentDeviceTime = LightCurvePoint.of(0, 0)
            )
        }
    }
}