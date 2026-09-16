package com.aqua.aqualight.application.devices.light.automation

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene

data class DeviceLightManagedPlanPhaseDraft(
    val validFromEpochDay: Long,
    val validUntilEpochDayExclusive: Long?,
    val transitionDays: Int,
    val weekdaysMask: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val rampDurationMs: Long,
    val scene: DeviceLightAutomaticScene
)

data class DeviceLightManagedPlanDraft(
    val initialStartPercent: Int,
    val phases: List<DeviceLightManagedPlanPhaseDraft>
)

data class DeviceLightManagedPlanAuthority(
    val storageGeneration: Long,
    val revision: Long,
    val installedPlanId: String?
)

enum class DeviceLightManagedPlanRuntimeState {
    NOT_INSTALLED,
    NOT_SELECTED,
    RTC_BLOCKED,
    BEFORE_PLAN,
    ACTIVE
}

data class DeviceLightManagedPlanSnapshot(
    val deviceUid: String,
    val productDisplayName: String,
    val channels: List<DeviceLightAutomaticChannel>,
    val authority: DeviceLightManagedPlanAuthority,
    val installed: Boolean,
    val initialStartPercent: Int,
    val phases: List<DeviceLightManagedPlanPhaseDraft>,
    val runtimeState: DeviceLightManagedPlanRuntimeState,
    val activePhaseIndex: Int?
)

sealed interface DeviceLightManagedPlanReadResult {
    data class Available(val snapshot: DeviceLightManagedPlanSnapshot) :
        DeviceLightManagedPlanReadResult

    data class Failed(val failure: DeviceLightManagedPlanFailure) :
        DeviceLightManagedPlanReadResult
}

sealed interface DeviceLightManagedPlanMutationResult {
    data class Applied(val snapshot: DeviceLightManagedPlanSnapshot) :
        DeviceLightManagedPlanMutationResult

    data object Deleted : DeviceLightManagedPlanMutationResult

    data class Failed(val failure: DeviceLightManagedPlanFailure) :
        DeviceLightManagedPlanMutationResult
}

enum class DeviceLightManagedPlanFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    STALE_AUTHORITY,
    NOT_FOUND,
    SELECTED,
    REJECTED,
    INVALID_DATA
}

/** Firmware-backed application boundary for the single managed AUTO plan. */
interface DeviceLightManagedPlanOperations {
    suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult

    suspend fun apply(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority,
        draft: DeviceLightManagedPlanDraft
    ): DeviceLightManagedPlanMutationResult

    suspend fun delete(
        deviceUid: String,
        authority: DeviceLightManagedPlanAuthority
    ): DeviceLightManagedPlanMutationResult
}
