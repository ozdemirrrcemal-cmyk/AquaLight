package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import kotlinx.coroutines.flow.Flow



interface DeviceLightQuickSetupOperations {
    fun observeRuntime(deviceUid: String): Flow<DeviceLightQuickSetupRuntimeSnapshot>

    suspend fun load(deviceUid: String): DeviceLightQuickSetupLoadResult

    fun recommend(
        context: DeviceLightQuickSetupContext,
        input: DeviceLightQuickSetupInput
    ): DeviceLightQuickSetupRecommendationResult

    suspend fun apply(
        deviceUid: String,
        context: DeviceLightQuickSetupContext,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult

    suspend fun disable(
        deviceUid: String,
        managedPlan: DeviceLightManagedPlanSnapshot
    ): DeviceLightQuickSetupDisableResult
}

data class DeviceLightQuickSetupRuntimeSnapshot(
    val managedPlan: DeviceLightManagedPlanSnapshot?,
    val livePlan: DeviceLightPlanSnapshot?
)

sealed interface DeviceLightQuickSetupLoadResult {
    data class Available(
        val context: DeviceLightQuickSetupContext,
        val plantProfile: DeviceLightQuickSetupPlantProfile,
        val managedPlan: DeviceLightManagedPlanSnapshot?,
        val livePlan: DeviceLightPlanSnapshot?
    ) : DeviceLightQuickSetupLoadResult

    data class Blocked(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightQuickSetupLoadResult
}

sealed interface DeviceLightQuickSetupApplyResult {
    data class Applied(
        val managedPlan: DeviceLightManagedPlanSnapshot,
        val livePlan: DeviceLightPlanSnapshot?
    ) : DeviceLightQuickSetupApplyResult

    data class ContextChanged(
        val context: DeviceLightQuickSetupContext,
        val plantProfile: DeviceLightQuickSetupPlantProfile?
    ) : DeviceLightQuickSetupApplyResult

    data class Stale(
        val latest: DeviceLightManagedPlanSnapshot?
    ) : DeviceLightQuickSetupApplyResult

    data class Failed(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightQuickSetupApplyResult
}

sealed interface DeviceLightQuickSetupDisableResult {
    data class Disabled(
        val managedPlan: DeviceLightManagedPlanSnapshot
    ) : DeviceLightQuickSetupDisableResult

    data class Stale(
        val latest: DeviceLightManagedPlanSnapshot?
    ) : DeviceLightQuickSetupDisableResult

    data class Failed(
        val reason: DeviceLightQuickSetupBlockReason
    ) : DeviceLightQuickSetupDisableResult

    data object NotInstalled : DeviceLightQuickSetupDisableResult
}

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
