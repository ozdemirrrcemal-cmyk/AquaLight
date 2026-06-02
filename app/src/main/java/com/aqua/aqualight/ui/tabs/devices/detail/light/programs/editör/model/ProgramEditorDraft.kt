package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model

data class ProgramEditorDraft(
    val id: String?,
    val deviceId: Long,
    val title: String,
    val mode: ProgramEditorMode,
    val repeatDays: Set<LightRepeatDay>,
    val rampSmoothing: ProgramRampSmoothing,
    val balance: ProgramEditorChannelBalance,
    val curvePoints: List<ProgramEditorCurvePoint>,
    val rampMinutes: Int,
    val peakIntensityPercent: Int
)

data class ProgramEditorChannelBalance(
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

data class ProgramEditorCurvePoint(
    val kind: ProgramEditorCurvePointKind,
    val minuteOfDay: Int,
    val masterPercent: Int,
    val red: Int,
    val green: Int,
    val blue: Int,
    val white: Int
)

enum class ProgramEditorCurvePointKind {
    START,
    PEAK_START,
    PEAK_END,
    END,
    CUSTOM
}