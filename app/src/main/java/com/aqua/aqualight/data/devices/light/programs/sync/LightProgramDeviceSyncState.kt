package com.aqua.aqualight.data.devices.light.programs.sync

import com.aqua.aqualight.data.devices.light.programs.model.SavedLightProgram

/**
 * Non-mutating interpretation of the relationship between local saved programs
 * and the concrete LP schedule currently read from the controller.
 */
data class LightProgramDeviceSyncState(
    val status: LightProgramDeviceSyncStatus = LightProgramDeviceSyncStatus.NO_RUNTIME,
    val deviceChecksum: String = "",
    val matchedProgramId: String? = null,
    val matchedProgramName: String? = null,
    val localActiveProgramId: String? = null,
    val localActiveProgramName: String? = null,
    val hasDeviceSchedule: Boolean = false,
    val isLocalActiveOnDevice: Boolean = false,
    val totalPointCount: Int = 0,
    val runtimeErrorMessage: String? = null
) {
    val hasMatchedSavedProgram: Boolean
        get() = matchedProgramId != null
}

enum class LightProgramDeviceSyncStatus {
    /** Runtime has not been read yet or the device is currently unavailable. */
    NO_RUNTIME,

    /** Runtime is available but the controller has no LP points. */
    NO_DEVICE_PROGRAM,

    /** Controller LP schedule matches the local active saved program. */
    LOCAL_ACTIVE_MATCHED,

    /** Controller LP schedule matches a saved program that is not locally active. */
    SAVED_PROGRAM_MATCHED,

    /** Local active program exists, but the controller is running a different LP schedule. */
    LOCAL_ACTIVE_OUT_OF_SYNC,

    /** Controller has an LP schedule, but it does not match any saved local program. */
    DEVICE_PROGRAM_UNKNOWN
}

data class LightProgramDeviceProgramsSnapshot(
    val programs: List<SavedLightProgram>,
    val syncState: LightProgramDeviceSyncState
)
