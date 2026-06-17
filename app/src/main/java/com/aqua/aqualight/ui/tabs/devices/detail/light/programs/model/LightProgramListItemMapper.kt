package com.aqua.aqualight.ui.tabs.devices.detail.light.programs.model

import com.aqua.aqualight.data.devices.light.curve.model.LightCurveTransitionMode
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramTimeMath
import com.aqua.aqualight.data.devices.light.programs.model.LightProgramSyncStatus
import com.aqua.aqualight.data.devices.light.programs.model.RepeatMode
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncState
import com.aqua.aqualight.data.devices.light.programs.sync.LightProgramDeviceSyncStatus

object LightProgramListItemMapper {

    fun map(
        program: SavedLightProgram,
        deviceSyncState: LightProgramDeviceSyncState? = null
    ): LightProgramListItem {
        val draft = program.draft
        val schedule = LightProgramDevicePointExpander.expand(draft)
        val normalizedChannels = draft.channelValues.normalized()
        val isOnDevice = deviceSyncState?.matchedProgramId == program.id
        val hasSyncWarning = program.isActive && deviceSyncState?.status ==
            LightProgramDeviceSyncStatus.LOCAL_ACTIVE_OUT_OF_SYNC

        return LightProgramListItem(
            id = program.id,
            name = program.name,
            subtitle = buildSubtitle(
                program = program,
                deviceSyncState = deviceSyncState,
                isOnDevice = isOnDevice,
                hasSyncWarning = hasSyncWarning
            ),
            stateText = stateLabel(
                program = program,
                isOnDevice = isOnDevice,
                hasSyncWarning = hasSyncWarning
            ),
            isActive = program.isActive,
            isOnDevice = isOnDevice,
            hasSyncWarning = hasSyncWarning,
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
        program: SavedLightProgram,
        deviceSyncState: LightProgramDeviceSyncState?,
        isOnDevice: Boolean,
        hasSyncWarning: Boolean
    ): String {
        val repeatText = repeatLabel(program.draft.repeatMode)
        val syncText = when {
            isOnDevice && program.isActive -> "On device"
            isOnDevice -> "Saved on device"
            hasSyncWarning -> "Device differs"
            program.isActive && deviceSyncState?.status == LightProgramDeviceSyncStatus.NO_RUNTIME -> "Waiting for device sync"
            else -> syncLabel(program.syncStatus)
        }

        return if (syncText.isBlank()) {
            repeatText
        } else {
            "$repeatText · $syncText"
        }
    }

    private fun stateLabel(
        program: SavedLightProgram,
        isOnDevice: Boolean,
        hasSyncWarning: Boolean
    ): String {
        return when {
            hasSyncWarning -> "OUT OF SYNC"
            isOnDevice -> "ON DEVICE"
            program.isActive -> "ACTIVE"
            else -> "DISABLED"
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
            LightProgramSyncStatus.READ_FROM_DEVICE -> "Recovered"
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
