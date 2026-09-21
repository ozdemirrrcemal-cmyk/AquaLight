package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedAutoPlanOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanApplyResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanReadResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightManagedPlanSnapshot
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupBlockReason
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContext
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupContextResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlantProfile
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlantProfileResolver
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecommendation

internal class DeviceLightQuickSetupController(
    private val contextOperations: DeviceLightQuickSetupContextOperations,
    private val managedPlanOperations: DeviceLightManagedAutoPlanOperations,
    private val controlOperations: DeviceLightControlOperations
) {
    suspend fun load(deviceUid: String): DeviceLightQuickSetupLoadResult =
        when (val contextResult = contextOperations.readContext(deviceUid)) {
            is DeviceLightQuickSetupContextResult.Blocked ->
                DeviceLightQuickSetupLoadResult.Blocked(contextResult.reason)
            is DeviceLightQuickSetupContextResult.Available ->
                loadAvailable(deviceUid, contextResult.context)
        }

    suspend fun apply(
        deviceUid: String,
        context: DeviceLightQuickSetupContext,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult =
        when (val latestContext = contextOperations.readContext(deviceUid)) {
            is DeviceLightQuickSetupContextResult.Blocked ->
                DeviceLightQuickSetupApplyResult.Failed(latestContext.reason)
            is DeviceLightQuickSetupContextResult.Available ->
                applyLatestContext(
                    deviceUid = deviceUid,
                    originalContext = context,
                    latestContext = latestContext.context,
                    recommendation = recommendation
                )
        }

    suspend fun disable(
        deviceUid: String,
        managedPlan: DeviceLightManagedPlanSnapshot
    ): DeviceLightQuickSetupDisableResult {
        val planId = managedPlan.planId ?: return DeviceLightQuickSetupDisableResult.NotInstalled
        return when (
            val result = managedPlanOperations.delete(
                deviceUid = deviceUid,
                expectedRevision = managedPlan.revision,
                expectedStorageGeneration = managedPlan.storageGeneration,
                planId = planId
            )
        ) {
            is DeviceLightManagedPlanApplyResult.Applied ->
                DeviceLightQuickSetupDisableResult.Disabled(result.snapshot)
            is DeviceLightManagedPlanApplyResult.Stale ->
                DeviceLightQuickSetupDisableResult.Stale(result.latest)
            is DeviceLightManagedPlanApplyResult.Failed ->
                DeviceLightQuickSetupDisableResult.Failed(result.reason)
        }
    }

    private suspend fun loadAvailable(
        deviceUid: String,
        context: DeviceLightQuickSetupContext
    ): DeviceLightQuickSetupLoadResult {
        val profile = DeviceLightQuickSetupPlantProfileResolver.resolve(context)
        val managed = managedPlanOperations.read(deviceUid)
        val snapshot = (managed as? DeviceLightManagedPlanReadResult.Available)?.snapshot
        val livePlan = if (snapshot?.installed == true) readLivePlan(deviceUid) else null
        return if (profile == null) {
            DeviceLightQuickSetupLoadResult.Blocked(
                DeviceLightQuickSetupBlockReason.UNKNOWN_PLANT_CATALOG_ID
            )
        } else {
            DeviceLightQuickSetupLoadResult.Available(context, profile, snapshot, livePlan)
        }
    }

    private suspend fun applyLatestContext(
        deviceUid: String,
        originalContext: DeviceLightQuickSetupContext,
        latestContext: DeviceLightQuickSetupContext,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult =
        if (latestContext.profileFingerprint != recommendation.contextFingerprint) {
            DeviceLightQuickSetupApplyResult.ContextChanged(
                latestContext,
                DeviceLightQuickSetupPlantProfileResolver.resolve(latestContext)
            )
        } else {
            applyAuthoritativePlan(
                deviceUid = deviceUid,
                originalContext = originalContext,
                recommendation = recommendation
            )
        }

    private suspend fun applyAuthoritativePlan(
        deviceUid: String,
        originalContext: DeviceLightQuickSetupContext,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult =
        when (val planRead = managedPlanOperations.read(deviceUid)) {
            is DeviceLightManagedPlanReadResult.Failed ->
                DeviceLightQuickSetupApplyResult.Failed(planRead.reason)
            is DeviceLightManagedPlanReadResult.Available -> {
                check(originalContext.profileFingerprint == recommendation.contextFingerprint)
                applyCurrentPlan(deviceUid, planRead.snapshot, recommendation)
            }
        }

    private suspend fun applyCurrentPlan(
        deviceUid: String,
        current: DeviceLightManagedPlanSnapshot,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult =
        when (
            val applied = managedPlanOperations.apply(
                deviceUid = deviceUid,
                expectedRevision = current.revision,
                expectedStorageGeneration = current.storageGeneration,
                existingPlanId = current.planId.takeIf { current.installed },
                recommendation = recommendation
            )
        ) {
            is DeviceLightManagedPlanApplyResult.Applied ->
                DeviceLightQuickSetupApplyResult.Applied(applied.snapshot, readLivePlan(deviceUid))
            is DeviceLightManagedPlanApplyResult.Stale ->
                DeviceLightQuickSetupApplyResult.Stale(applied.latest)
            is DeviceLightManagedPlanApplyResult.Failed ->
                DeviceLightQuickSetupApplyResult.Failed(applied.reason)
        }

    private suspend fun readLivePlan(deviceUid: String): DeviceLightPlanSnapshot? =
        when (val control = controlOperations.refreshControl(deviceUid)) {
            is DeviceLightControlResult.Available -> control.snapshot.plan
            is DeviceLightControlResult.Failed -> null
        }
}

internal sealed interface DeviceLightQuickSetupLoadResult {
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

internal sealed interface DeviceLightQuickSetupApplyResult {
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

internal sealed interface DeviceLightQuickSetupDisableResult {
    data class Disabled(val managedPlan: DeviceLightManagedPlanSnapshot) :
        DeviceLightQuickSetupDisableResult
    data class Stale(val latest: DeviceLightManagedPlanSnapshot?) :
        DeviceLightQuickSetupDisableResult
    data class Failed(val reason: DeviceLightQuickSetupBlockReason) :
        DeviceLightQuickSetupDisableResult
    data object NotInstalled : DeviceLightQuickSetupDisableResult
}
