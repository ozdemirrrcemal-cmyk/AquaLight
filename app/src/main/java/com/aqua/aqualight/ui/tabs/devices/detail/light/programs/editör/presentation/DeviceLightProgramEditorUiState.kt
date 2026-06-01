package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.presentation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramEditorMode
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model.LightProgramRampSmoothing
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChartData
import com.aqua.aqualight.ui.tabs.devices.detail.light.shared.curve.LightCurveChannel

data class DeviceLightProgramEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val programId: String? = null,
    val programName: String = "",
    val editorMode: LightProgramEditorMode = LightProgramEditorMode.SIMPLE,
    val selectedChannel: LightCurveChannel = LightCurveChannel.RED,
    val rampSmoothing: LightProgramRampSmoothing = LightProgramRampSmoothing.LINEAR,
    val repeatDays: Set<Int> = emptySet(),
    val chartData: LightCurveChartData? = null,
    val pointRows: List<ProgramCurvePointRowUi> = emptyList(),
    val channelBalance: ProgramChannelBalanceUi = ProgramChannelBalanceUi(),
    val acclimation: ProgramAcclimationUi = ProgramAcclimationUi(),
    val draft: LightProgramEditorDraft? = null
)

data class ProgramCurvePointRowUi(
    val id: String,
    val timeLabel: String,
    val title: String,
    val intensityLabel: String,
    val canRename: Boolean,
    val canDelete: Boolean
)

data class ProgramChannelBalanceUi(
    val redLabel: String = "",
    val greenLabel: String = "",
    val blueLabel: String = "",
    val whiteLabel: String = "",
    val redPercent: Int? = null,
    val greenPercent: Int? = null,
    val bluePercent: Int? = null,
    val whitePercent: Int? = null
)

data class ProgramAcclimationUi(
    val enabled: Boolean = false,
    val valueLabel: String = "",
    val durationDays: Int? = null,
    val startIntensityPercent: Int? = null
)