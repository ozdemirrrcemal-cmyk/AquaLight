package com.aqua.aqualight.data.devices.light.programs.validation

import com.aqua.aqualight.data.devices.light.programs.model.LightProgramDraft
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.timeline.LightProgramPhaseType
import com.aqua.aqualight.data.devices.light.programs.timeline.LightProgramTimelineBuilder

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

        if (draft.cloudSimulationSettings.enabled) {
            return LightProgramValidationResult.Invalid(
                "Cloud simulation is not available yet."
            )
        }

        val moonlight = draft.moonlightSettings

        if (moonlight.enabled) {
            if (moonlight.intensityPercent !in 1..15) {
                return LightProgramValidationResult.Invalid(
                    "Moonlight intensity must be between 1% and 15%."
                )
            }

            val timeline = LightProgramTimelineBuilder.build(
                draft = draft
            )

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