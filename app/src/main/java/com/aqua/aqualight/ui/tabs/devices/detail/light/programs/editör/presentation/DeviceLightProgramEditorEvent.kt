package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.presentation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel

sealed interface DeviceLightProgramEditorEvent {

    data object LoadRequested : DeviceLightProgramEditorEvent

    data class ProgramNameChanged(
        val name: String
    ) : DeviceLightProgramEditorEvent

    data object SimpleModeSelected : DeviceLightProgramEditorEvent

    data object ProModeSelected : DeviceLightProgramEditorEvent

    data class ProChannelSelected(
        val channel: LightCurveChannel
    ) : DeviceLightProgramEditorEvent

    data class RepeatDaysChanged(
        val selectedDays: Set<Int>
    ) : DeviceLightProgramEditorEvent

    data class RampSmoothingChanged(
        val smoothing: LightProgramRampSmoothing
    ) : DeviceLightProgramEditorEvent

    data class ChannelBalanceChanged(
        val redPercent: Int?,
        val greenPercent: Int?,
        val bluePercent: Int?,
        val whitePercent: Int?
    ) : DeviceLightProgramEditorEvent

    data class AcclimationChanged(
        val enabled: Boolean,
        val durationDays: Int?,
        val startIntensityPercent: Int?
    ) : DeviceLightProgramEditorEvent

    data class CurvePointUpdated(
        val pointId: String,
        val label: String,
        val minuteOfDay: Int,
        val intensityPercent: Int
    ) : DeviceLightProgramEditorEvent

    data class CurvePointDeleted(
        val pointId: String
    ) : DeviceLightProgramEditorEvent

    data class CurvePointAdded(
        val label: String,
        val minuteOfDay: Int,
        val intensityPercent: Int
    ) : DeviceLightProgramEditorEvent

    data object PreviewDayRequested : DeviceLightProgramEditorEvent

    data object SaveRequested : DeviceLightProgramEditorEvent
}