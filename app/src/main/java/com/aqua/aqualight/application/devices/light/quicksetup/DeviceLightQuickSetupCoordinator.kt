package com.aqua.aqualight.application.devices.light.quicksetup

import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine

internal class DeviceLightQuickSetupCoordinator(
    private val contextOperations: DeviceLightQuickSetupContextOperations,
    private val managedPlanOperations: DeviceLightManagedAutoPlanOperations,
    private val controlOperations: DeviceLightControlOperations,
    calibration: DeviceLightFixtureCalibration
) : DeviceLightQuickSetupOperations {

    private val recommendationEngine = DeviceLightQuickSetupRecommendationEngine(calibration)

    override fun observeRuntime(
        deviceUid: String
    ): Flow<DeviceLightQuickSetupRuntimeSnapshot> =
        combine(
            managedPlanOperations.observe(deviceUid),
            controlOperations.observeControl(deviceUid)
        ) { managedPlan, control ->
            DeviceLightQuickSetupRuntimeSnapshot(
                managedPlan = managedPlan,
                livePlan = (control as? DeviceLightControlResult.Available)?.snapshot?.plan
            )
        }.distinctUntilChanged()

    override suspend fun load(deviceUid: String): DeviceLightQuickSetupLoadResult =
        when (val contextResult = contextOperations.readContext(deviceUid)) {
            is DeviceLightQuickSetupContextResult.Blocked ->
                DeviceLightQuickSetupLoadResult.Blocked(contextResult.reason)
            is DeviceLightQuickSetupContextResult.Available ->
                loadAvailable(deviceUid, contextResult.context)
        }

    override fun recommend(
        context: DeviceLightQuickSetupContext,
        input: DeviceLightQuickSetupInput
    ): DeviceLightQuickSetupRecommendationResult =
        recommendationEngine.recommend(context, input)

    override suspend fun apply(
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

    override suspend fun disable(
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
            applyAuthoritativePlan(deviceUid, originalContext, recommendation)
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
