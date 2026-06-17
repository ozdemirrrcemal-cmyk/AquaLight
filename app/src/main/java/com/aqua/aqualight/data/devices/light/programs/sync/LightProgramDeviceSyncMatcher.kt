package com.aqua.aqualight.data.devices.light.programs.sync

import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDevicePointExpander
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramDeviceTransitionStrategy
import com.aqua.aqualight.data.devices.light.programs.compiler.LightProgramPointExpansionOptions
import com.aqua.aqualight.data.devices.light.programs.device.LightProgramDevicePayloadMapper
import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeSnapshot
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeState

/**
 * Matches the controller's actual runtime LP points against saved local program
 * intent. This matcher is pure and never mutates local records; auto recovery is
 * handled by a separate use-case after this state proves the device schedule is
 * valid but unknown locally.
 */
object LightProgramDeviceSyncMatcher {

    fun match(
        programs: List<SavedLightProgram>,
        runtimeState: LightRuntimeState
    ): LightProgramDeviceSyncState {
        return match(
            programs = programs,
            snapshot = runtimeState.snapshot,
            runtimeErrorMessage = runtimeState.errorMessage
        )
    }

    fun match(
        programs: List<SavedLightProgram>,
        snapshot: LightRuntimeSnapshot?,
        runtimeErrorMessage: String? = null
    ): LightProgramDeviceSyncState {
        val localActiveProgram = programs.firstOrNull { program ->
            program.isActive
        }

        if (snapshot == null) {
            return LightProgramDeviceSyncState(
                status = LightProgramDeviceSyncStatus.NO_RUNTIME,
                localActiveProgramId = localActiveProgram?.id,
                localActiveProgramName = localActiveProgram?.name,
                runtimeErrorMessage = runtimeErrorMessage
            )
        }

        val deviceChecksum = LightProgramRuntimeScheduleMapper.checksum(
            scheduleChannels = snapshot.scheduleChannels
        )
        val pointCount = LightProgramRuntimeScheduleMapper.totalPointCount(
            scheduleChannels = snapshot.scheduleChannels
        )

        if (deviceChecksum.isNullOrBlank()) {
            return LightProgramDeviceSyncState(
                status = LightProgramDeviceSyncStatus.NO_DEVICE_PROGRAM,
                localActiveProgramId = localActiveProgram?.id,
                localActiveProgramName = localActiveProgram?.name,
                hasDeviceSchedule = false,
                totalPointCount = pointCount,
                runtimeErrorMessage = runtimeErrorMessage
            )
        }

        val matchedProgram = findMatchingProgram(
            programs = programs,
            deviceChecksum = deviceChecksum
        )
        val localActiveMatches = matchedProgram != null &&
            localActiveProgram?.id == matchedProgram.id

        val status = when {
            localActiveMatches -> LightProgramDeviceSyncStatus.LOCAL_ACTIVE_MATCHED
            matchedProgram != null -> LightProgramDeviceSyncStatus.SAVED_PROGRAM_MATCHED
            localActiveProgram != null -> LightProgramDeviceSyncStatus.LOCAL_ACTIVE_OUT_OF_SYNC
            else -> LightProgramDeviceSyncStatus.DEVICE_PROGRAM_UNKNOWN
        }

        return LightProgramDeviceSyncState(
            status = status,
            deviceChecksum = deviceChecksum,
            matchedProgramId = matchedProgram?.id,
            matchedProgramName = matchedProgram?.name,
            localActiveProgramId = localActiveProgram?.id,
            localActiveProgramName = localActiveProgram?.name,
            hasDeviceSchedule = true,
            isLocalActiveOnDevice = localActiveMatches,
            totalPointCount = pointCount,
            runtimeErrorMessage = runtimeErrorMessage
        )
    }

    private fun findMatchingProgram(
        programs: List<SavedLightProgram>,
        deviceChecksum: String
    ): SavedLightProgram? {
        return programs.firstOrNull { program ->
            program.compiledChecksum.isNotBlank() && program.compiledChecksum == deviceChecksum
        } ?: programs.firstOrNull { program ->
            compiledChecksum(program) == deviceChecksum
        }
    }

    private fun compiledChecksum(
        program: SavedLightProgram
    ): String {
        val schedule = LightProgramDevicePointExpander.expand(
            draft = program.draft,
            options = LightProgramPointExpansionOptions(
                strategy = if (program.firmwareProfile.supportsNativeTransition) {
                    LightProgramDeviceTransitionStrategy.NATIVE_TRANSITION
                } else {
                    LightProgramDeviceTransitionStrategy.EXPANDED_POINTS
                }
            )
        )

        return LightProgramDevicePayloadMapper.toPayload(schedule).checksum
    }
}
