package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.domain.model

data class LightProgramEditorDraft(
    val programId: String?,
    val programName: String,
    val mode: LightProgramEditorMode,
    val repeatRule: LightProgramRepeatRuleDraft,
    val rampSmoothing: LightProgramRampSmoothing,
    val simpleCurve: LightProgramCurveDraft?,
    val proCurves: List<LightProgramCurveDraft>,
    val channelBalance: LightProgramChannelBalanceDraft,
    val acclimation: LightProgramAcclimationDraft
) {
    companion object {
        fun emptyNewProgram(
            programName: String
        ): LightProgramEditorDraft {
            return LightProgramEditorDraft(
                programId = null,
                programName = programName,
                mode = LightProgramEditorMode.SIMPLE,
                repeatRule = LightProgramRepeatRuleDraft(),
                rampSmoothing = LightProgramRampSmoothing.LINEAR,
                simpleCurve = null,
                proCurves = emptyList(),
                channelBalance = LightProgramChannelBalanceDraft(),
                acclimation = LightProgramAcclimationDraft()
            )
        }
    }
}