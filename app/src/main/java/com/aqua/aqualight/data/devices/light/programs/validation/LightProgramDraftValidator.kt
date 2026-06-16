package com.aqua.aqualight.data.devices.light.programs.validation

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath

object LightProgramDraftValidator {

    fun validateName(
        name: String
    ): LightProgramValidationResult {
        return if (name.trim().isBlank()) {
            LightProgramValidationResult.Invalid(
                "Program name is required."
            )
        } else {
            LightProgramValidationResult.Valid
        }
    }

    fun validate(
        draft: LightProgramDraft
    ): LightProgramValidationResult {
        val start = LightProgramTimeMath.startMinutes(draft.start)
        val peakStart = LightProgramTimeMath.normalMinutes(draft.peakStart)
        val peakEnd = LightProgramTimeMath.normalMinutes(draft.peakEnd)
        val end = LightProgramTimeMath.endMinutes(draft.end)

        if (!(start < peakStart && peakStart < peakEnd && peakEnd < end)) {
            return LightProgramValidationResult.Invalid(
                "Times must follow Start → Peak Start → Peak End → End."
            )
        }

        val channels = draft.channelValues

        val channelValues = listOf(
            channels.red,
            channels.green,
            channels.blue,
            channels.white
        )

        if (channelValues.any { value -> value !in 0..100 }) {
            return LightProgramValidationResult.Invalid(
                "Channel values must be between 0% and 100%."
            )
        }

        if (channelValues.all { value -> value == 0 }) {
            return LightProgramValidationResult.Invalid(
                "At least one channel must be above 0%."
            )
        }

        if (draft.selectedDays.isEmpty()) {
            return LightProgramValidationResult.Invalid(
                "Select at least one repeat day."
            )
        }

        if (draft.selectedDays.any { day -> day !in 1..7 }) {
            return LightProgramValidationResult.Invalid(
                "Repeat days are invalid."
            )
        }

        return LightProgramValidationResult.Valid
    }
}