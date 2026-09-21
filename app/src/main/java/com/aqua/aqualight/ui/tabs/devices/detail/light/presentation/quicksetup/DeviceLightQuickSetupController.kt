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
    @Suppress("ReturnCount")
    suspend fun load(deviceUid: String): DeviceLightQuickSetupLoadResult {
        val contextResult = contextOperations.readContext(deviceUid)
        if (contextResult !is DeviceLightQuickSetupContextResult.Available) {
            return DeviceLightQuickSetupLoadResult.Blocked(
                (contextResult as DeviceLightQuickSetupContextResult.Blocked).reason
            )
        }
        val context = contextResult.context
        val profile = DeviceLightQuickSetupPlantProfileResolver.resolve(context)
            ?: return DeviceLightQuickSetupLoadResult.Blocked(
                DeviceLightQuickSetupBlockReason.UNKNOWN_PLANT_CATALOG_ID
            )
        val managed = managedPlanOperations.read(deviceUid)
        val snapshot = (managed as? DeviceLightManagedPlanReadResult.Available)?.snapshot
        val livePlan = if (snapshot?.installed == true) readLivePlan(deviceUid) else null
        return DeviceLightQuickSetupLoadResult.Available(context, profile, snapshot, livePlan)
    }

    suspend fun apply(
        deviceUid: String,
        context: DeviceLightQuickSetupContext,
        recommendation: DeviceLightQuickSetupRecommendation
    ): DeviceLightQuickSetupApplyResult {
        val latestContext = contextOperations.readContext(deviceUid)
        if (latestContext !is DeviceLightQuickSetupContextResult.Available) {
            return DeviceLightQuickSetupApplyResult.Failed(
                (latestContext as DeviceLightQuickSetupContextResult.Blocked).reason
            )
        }
        if (latestContext.context.profileFingerprint != recommendation.contextFingerprint) {
            return DeviceLightQuickSetupApplyResult.ContextChanged(
                latestContext.context,
                DeviceLightQuickSetupPlantProfileResolver.resolve(latestContext.context)
            )
        }
        val planRead = managedPlanOperations.read(deviceUid)
        if (planRead !is DeviceLightManagedPlanReadResult.Available) {
            return DeviceLightQuickSetupApplyResult.Failed(
                (planRead as DeviceLightManagedPlanReadResult.Failed).reason
            )
        }
        val current = planRead.snapshot
        return when (
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
