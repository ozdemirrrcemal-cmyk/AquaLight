package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.timeline.LightProgramTimelineBuilder

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

        val moonlight = draft.moonlightSettings

        if (moonlight.enabled) {
            if (moonlight.intensityPercent !in 1..15) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight intensity must be between 1% and 15%."
                )
            }

            val timeline = LightProgramTimelineBuilder.build(draft)

            val moonlightPhase = timeline.phases.firstOrNull { phase ->
                phase.type == LightProgramPhaseType.MOONLIGHT
            }

            if (moonlightPhase == null) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight schedule could not be prepared."
                )
            }

            if (moonlightPhase.durationMinutes < 15) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight duration must be at least 15 minutes."
                )
            }

            if (moonlightPhase.durationMinutes > 12 * 60) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight duration cannot be longer than 12 hours."
                )
            }

            if (moonlightPhase.startMinute < end) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight must start after the main program ends."
                )
            }
        }

        return LightProgramValidationResult.Valid
    }
}