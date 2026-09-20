package com.aqua.aqualight.application.devices.light.quicksetup

import kotlinx.coroutines.flow.Flow

interface DeviceLightQuickSetupContextOperations {
    suspend fun readContext(deviceUid: String): DeviceLightQuickSetupContextResult
}

enum class DeviceLightManagedPlanRuntimeState {
    NOT_INSTALLED,
    NOT_SELECTED,
    RTC_BLOCKED,
    BEFORE_PLAN,
    ACTIVE
}

data class DeviceLightManagedPlanSnapshot(
    val storageGeneration: Long,
    val revision: Long,
    val installed: Boolean,
    val planId: String?,
    val initialStartPercent: Int,
    val runtimeState: DeviceLightManagedPlanRuntimeState,
    val activePhaseIndex: Int?,
    val transitionPermille: Int?,
    val nextTransitionEpochDay: Int?
)

sealed interface DeviceLightManagedPlanReadResult {
    data class Available(
        val snapshot: DeviceLightManagedPlanSnapshot
    ) : DeviceLightManagedPlanReadResult

    data class Failed(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightManagedPlanReadResult
}

sealed interface DeviceLightManagedPlanApplyResult {
    data class Applied(
        val snapshot: DeviceLightManagedPlanSnapshot
    ) : DeviceLightManagedPlanApplyResult

    data class Stale(
        val latest: DeviceLightManagedPlanSnapshot?
    ) : DeviceLightManagedPlanApplyResult

    data class Failed(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightManagedPlanApplyResult
}

interface DeviceLightManagedAutoPlanOperations {
    fun observe(deviceUid: String): Flow<DeviceLightManagedPlanSnapshot?>

    fun current(deviceUid: String): DeviceLightManagedPlanSnapshot?

    suspend fun read(deviceUid: String): DeviceLightManagedPlanReadResult

    suspend fun apply(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        existingPlanId: String?,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightManagedPlanApplyResult

    suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        expectedStorageGeneration: Long,
        planId: String
    ): DeviceLightManagedPlanApplyResult
}
