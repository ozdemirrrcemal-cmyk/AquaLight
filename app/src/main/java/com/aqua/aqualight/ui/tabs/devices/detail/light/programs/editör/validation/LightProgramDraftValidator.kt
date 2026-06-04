package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft

object LightProgramDraftValidator {

    fun validate(
        draft: LightProgramDraft
    ): LightProgramValidationResult {
        val start = draft.start.totalMinutes
        val peakStart = draft.peakStart.totalMinutes
        val peakEnd = draft.peakEnd.totalMinutes
        val end = draft.end.totalMinutes

        if (!(start < peakStart && peakStart < peakEnd && peakEnd < end)) {
            return LightProgramValidationResult.Invalid(
                "Program times must be in order."
            )
        }

        val channels = draft.channelValues

        if (
            channels.red == 0 &&
            channels.green == 0 &&
            channels.blue == 0 &&
            channels.white == 0
        ) {
            return LightProgramValidationResult.Invalid(
                "At least one channel must be above 0%."
            )
        }

        if (draft.selectedDays.isEmpty()) {
            return LightProgramValidationResult.Invalid(
                "Select at least one repeat day."
            )
        }

        return LightProgramValidationResult.Valid
    }
}