package com.aqua.aqualight.ui.tabs.devices.detail.light.programs

import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramCompileResult
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramScheduleCompiler
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncState
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTransitionMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model.LightProgramListItem

object LightProgramListItemMapper {

    fun map(
        programs: List<SavedLightProgram>
    ): List<LightProgramListItem> {
        return programs.map { program ->
            map(program)
        }
    }

    fun map(
        program: SavedLightProgram
    ): LightProgramListItem {
        val compiledPointCount = when (val result = LightProgramScheduleCompiler.compile(program)) {
            is LightProgramCompileResult.Success -> result.schedule.points.size
            is LightProgramCompileResult.Invalid -> 0
        }

        return LightProgramListItem(
            id = program.id,
            name = program.name,
            subtitle = program.subtitleText(),
            isActive = program.isActive,
            syncState = program.syncState,
            stateText = program.stateText(),
            startTime = formatMinute(program.startMinute),
            endTime = formatMinute(program.endMinute),
            rampText = "Rise ${formatMinute(program.startMinute)}–${formatMinute(program.peakStartMinute)}",
            pointText = "$compiledPointCount pts",
            peakText = "Peak ${program.peakPercent}%",
            red = program.red.coerceIn(0, 100),
            green = program.green.coerceIn(0, 100),
            blue = program.blue.coerceIn(0, 100),
            white = program.white.coerceIn(0, 100)
        )
    }

    private fun SavedLightProgram.subtitleText(): String {
        val transition = when (transitionMode) {
            LightProgramTransitionMode.LINEAR -> "Linear"
            LightProgramTransitionMode.SMOOTH -> "Smooth"
            LightProgramTransitionMode.NATURAL -> "Natural"
        }

        val repeat = "Every day"
        return "$repeat · $transition transition"
    }

    private fun SavedLightProgram.stateText(): String {
        return when (syncState) {
            LightProgramSyncState.ACTIVE_SYNCED -> "Active"
            LightProgramSyncState.ACTIVE_DIRTY -> "Needs Load"
            LightProgramSyncState.SYNC_FAILED -> "Sync Failed"
            LightProgramSyncState.LOCAL_ONLY -> if (isActive) {
                "Needs Load"
            } else {
                "Saved"
            }
        }
    }

    private fun formatMinute(
        minute: Int
    ): String {
        if (minute >= 24 * 60) {
            return "24:00"
        }

        val safeMinute = minute.coerceIn(0, 24 * 60 - 1)
        val hour = safeMinute / 60
        val min = safeMinute % 60
        return "%02d:%02d".format(hour, min)
    }
}
