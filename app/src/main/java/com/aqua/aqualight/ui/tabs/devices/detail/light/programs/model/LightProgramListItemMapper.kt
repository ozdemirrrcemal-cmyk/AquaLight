package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncStatus
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram

object LightProgramListItemMapper {

    fun map(
        program: SavedLightProgram
    ): LightProgramListItem {
        val draft = program.draft
        val schedule = LightProgramDevicePointExpander.expand(draft)
        val normalizedChannels = draft.channelValues.normalized()

        return LightProgramListItem(
            id = program.id,
            name = program.name,
            subtitle = buildSubtitle(program),
            isActive = program.isActive,
            startTime = draft.start.label,
            endTime = LightProgramTimeMath.endLabel(draft.end),
            rampText = transitionLabel(draft.transitionMode),
            pointText = "${schedule.totalPointCount} pts",
            peakText = "Peak ${draft.peakStart.label} – ${draft.peakEnd.label}",
            red = normalizedChannels.red,
            green = normalizedChannels.green,
            blue = normalizedChannels.blue,
            white = normalizedChannels.white
        )
    }

    private fun buildSubtitle(
        program: SavedLightProgram
    ): String {
        val repeatText = repeatLabel(program.draft.repeatMode)
        val syncText = syncLabel(program.syncStatus)
        return if (syncText.isBlank()) {
            repeatText
        } else {
            "$repeatText · $syncText"
        }
    }

    private fun repeatLabel(
        repeatMode: RepeatMode
    ): String {
        return when (repeatMode) {
            RepeatMode.EVERY -> "Every day"
            RepeatMode.WEEK -> "Weekdays"
            RepeatMode.WEEKEND -> "Weekend"
            RepeatMode.CUSTOM -> "Custom days"
        }
    }

    private fun syncLabel(
        status: LightProgramSyncStatus
    ): String {
        return when (status) {
            LightProgramSyncStatus.LOCAL_ONLY -> "Local"
            LightProgramSyncStatus.SYNCED_TO_DEVICE -> "Synced"
            LightProgramSyncStatus.SYNC_FAILED -> "Sync failed"
            LightProgramSyncStatus.READ_FROM_DEVICE -> "Imported"
        }
    }

    private fun transitionLabel(
        transitionMode: LightCurveTransitionMode
    ): String {
        return when (transitionMode) {
            LightCurveTransitionMode.LINEAR -> "Linear ramp"
            LightCurveTransitionMode.SMOOTH -> "Smooth ramp"
            LightCurveTransitionMode.NATURAL -> "Natural ramp"
        }
    }
}
