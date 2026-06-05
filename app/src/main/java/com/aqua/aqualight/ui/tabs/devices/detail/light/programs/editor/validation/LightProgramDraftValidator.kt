package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath

object LightProgramDraftValidator {

    fun validate(
        draft: LightProgramDraft
    ): LightProgramValidationResult {
        val start = LightProgramTimeMath.startMinutes(draft.start)
        val peakStart = LightProgramTimeMath.normalMinutes(draft.peakStart)
        val peakEnd = LightProgramTimeMath.normalMinutes(draft.peakEnd)
        val end = LightProgramTimeMath.endMinutes(draft.end)

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