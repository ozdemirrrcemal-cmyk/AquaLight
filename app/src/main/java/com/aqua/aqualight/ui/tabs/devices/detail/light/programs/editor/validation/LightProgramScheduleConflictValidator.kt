package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.validation

import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.editor.model.LightProgramTimeMath
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.SavedLightProgram

object LightProgramScheduleConflictValidator {

    fun findConflict(
        candidate: SavedLightProgram,
        existingPrograms: List<SavedLightProgram>
    ): SavedLightProgram? {
        return existingPrograms.firstOrNull { existing ->
            existing.id != candidate.id &&
                existing.deviceId == candidate.deviceId &&
                existing.isActive &&
                hasCommonDays(candidate, existing) &&
                hasTimeOverlap(candidate, existing)
        }
    }

    private fun hasCommonDays(
        first: SavedLightProgram,
        second: SavedLightProgram
    ): Boolean {
        return first.draft.selectedDays
            .intersect(second.draft.selectedDays)
            .isNotEmpty()
    }

    private fun hasTimeOverlap(
        first: SavedLightProgram,
        second: SavedLightProgram
    ): Boolean {
        val firstStart = LightProgramTimeMath.startMinutes(first.draft.start)
        val firstEnd = LightProgramTimeMath.endMinutes(first.draft.end)

        val secondStart = LightProgramTimeMath.startMinutes(second.draft.start)
        val secondEnd = LightProgramTimeMath.endMinutes(second.draft.end)

        return firstStart < secondEnd && secondStart < firstEnd
    }
}