package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Central Light refresh coordinator.
 *
 * Lifecycle, event and screen refresh callers share one device-scoped firmware readback flight.
 * The runtime state owner remains the only mutable authority; this class only coordinates
 * coherent current-generation hydration.
 */
internal class DeviceLightRuntimeRefreshCoordinator(
    private val runtime: DeviceLightRuntimeRepository,
    private val thermal: DeviceLightThermalRuntimeRepository,
    private val protection: DeviceLightTemperatureProtectionRuntimeRepository,
    private val dashboardRefresh: DeviceLightDashboardRefreshCoordinator =
        DeviceLightDashboardRefreshCoordinator(runtime)
) {
    private val inFlight = ConcurrentHashMap<
        DeviceUid,
        CompletableDeferred<DeviceLightRuntimeRefreshResult>
        >()

    suspend fun refreshAll(deviceUid: DeviceUid): DeviceLightRuntimeRefreshResult =
        refresh(deviceUid) { refreshAllWithinFlight(deviceUid) }

    suspend fun refreshGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceLightRuntimeRefreshResult = refresh(deviceUid) {
        refreshGenerationWithinFlight(deviceUid, generation)
    }.forGeneration(generation)

    suspend fun reconcileCommitted(
        deviceUid: DeviceUid,
        expectedMode: DeviceLightMode,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceLightRuntimeRefreshResult = refresh(deviceUid) {
        val current = runtime.currentDashboard(
            deviceUid,
            DeviceLightDashboardReadAuthority.AUTHORITATIVE
        )
        if (current?.status?.mode == expectedMode) {
            refreshDependents(
                deviceUid = deviceUid,
                dashboard = current,
                generation = generation,
                refreshSystem = true
            )
        } else {
            when (val dashboard = dashboardRefresh.refresh(deviceUid)) {
                is DeviceLightDashboardRefreshResult.Success -> {
                    if (dashboard.generation == generation) {
                        refreshDependents(
                            deviceUid = deviceUid,
                            dashboard = dashboard.dashboard,
                            generation = generation,
                            refreshSystem = true
                        )
                    } else {
                        DeviceLightRuntimeRefreshResult.RejectedStale
                    }
                }
                is DeviceLightDashboardRefreshResult.Failed ->
                    DeviceLightRuntimeRefreshResult.Failed(dashboard.outcome)
                DeviceLightDashboardRefreshResult.RejectedStale ->
                    DeviceLightRuntimeRefreshResult.RejectedStale
                DeviceLightDashboardRefreshResult.Malformed ->
                    DeviceLightRuntimeRefreshResult.Malformed
            }
        }
    }

    private suspend fun refresh(
        deviceUid: DeviceUid,
        producer: suspend () -> DeviceLightRuntimeRefreshResult
    ): DeviceLightRuntimeRefreshResult {
        val pending = CompletableDeferred<DeviceLightRuntimeRefreshResult>()
        val existing = inFlight.putIfAbsent(deviceUid, pending)
        if (existing != null) return existing.await()

        return try {
            val result = producer()
            pending.complete(result)
            result
        } catch (cancellation: CancellationException) {
            pending.complete(DeviceLightRuntimeRefreshResult.RejectedStale)
            throw cancellation
        } finally {
            pending.complete(DeviceLightRuntimeRefreshResult.Malformed)
            inFlight.remove(deviceUid, pending)
        }
    }

    private suspend fun refreshAllWithinFlight(
        deviceUid: DeviceUid
    ): DeviceLightRuntimeRefreshResult = when (val dashboard = dashboardRefresh.refresh(deviceUid)) {
        is DeviceLightDashboardRefreshResult.Success -> refreshDependents(
            deviceUid = deviceUid,
            dashboard = dashboard.dashboard,
            generation = dashboard.generation,
            refreshSystem = true
        )
        is DeviceLightDashboardRefreshResult.Failed ->
            DeviceLightRuntimeRefreshResult.Failed(dashboard.outcome)
        DeviceLightDashboardRefreshResult.RejectedStale ->
            DeviceLightRuntimeRefreshResult.RejectedStale
        DeviceLightDashboardRefreshResult.Malformed ->
            DeviceLightRuntimeRefreshResult.Malformed
    }

    private suspend fun refreshGenerationWithinFlight(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceLightRuntimeRefreshResult = when (val status = runtime.requestStatus(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> if (status.generation == generation) {
            refreshDependentsFromStatus(
                deviceUid = deviceUid,
                status = status.value,
                generation = generation,
                refreshGraph = true,
                refreshSystem = false
            )
        } else {
            DeviceLightRuntimeRefreshResult.RejectedStale
        }
        else -> DeviceLightRuntimeRefreshResult.Failed(status)
    }

    private suspend fun refreshDependents(
        deviceUid: DeviceUid,
        dashboard: DeviceLightDashboardRuntimeState,
        generation: DeviceRuntimeConnectionGeneration,
        refreshSystem: Boolean
    ): DeviceLightRuntimeRefreshResult {
        val dashboardAuthoritative =
            runtime.isAuthoritative(deviceUid, generation) &&
                runtime.isGraphAuthoritative(deviceUid, generation)
        return if (dashboardAuthoritative) {
            refreshDependentsFromStatus(
                deviceUid = deviceUid,
                status = dashboard.status,
                generation = generation,
                refreshGraph = false,
                refreshSystem = refreshSystem
            )
        } else {
            DeviceLightRuntimeRefreshResult.RejectedStale
        }
    }

    private suspend fun refreshDependentsFromStatus(
        deviceUid: DeviceUid,
        status: DeviceLightStatus,
        generation: DeviceRuntimeConnectionGeneration,
        refreshGraph: Boolean,
        refreshSystem: Boolean
    ): DeviceLightRuntimeRefreshResult {
        val custom = if (runtime.requiresLibraryCustomRefresh(deviceUid)) {
            validateGeneration(runtime.requestCustom(deviceUid), generation)
        } else {
            null
        }
        val automatic = custom ?: if (runtime.requiresAutomaticProgramsRefresh(deviceUid)) {
            validateGeneration(runtime.requestAutoPrograms(deviceUid), generation)
        } else {
            null
        }
        val managedPlan = automatic ?: if (runtime.requiresManagedAutoPlanRefresh(deviceUid)) {
            validateGeneration(runtime.requestManagedAutoPlan(deviceUid), generation)
        } else {
            null
        }
        val graph = managedPlan ?: if (refreshGraph) {
            validateGeneration(runtime.requestGraph(deviceUid), generation)
        } else {
            null
        }
        val system = graph ?: if (refreshSystem) {
            refreshSystemIfRequired(deviceUid, status, generation)
        } else {
            null
        }
        return system ?: verifyAuthoritativeSurface(
            deviceUid = deviceUid,
            status = status,
            generation = generation,
            requireSystem = refreshSystem
        )
    }

    private suspend fun refreshSystemIfRequired(
        deviceUid: DeviceUid,
        status: DeviceLightStatus,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceLightRuntimeRefreshResult? {
        val systemAlreadyAuthoritative = runtime.currentSystem(
            deviceUid,
            DeviceLightSystemReadAuthority.AUTHORITATIVE
        ) != null
        return if (!status.requiresSystemRuntimeRefresh() || systemAlreadyAuthoritative) {
            null
        } else {
            val thermalResult = validateGeneration(thermal.requestStatus(deviceUid), generation)
            thermalResult ?: validateGeneration(protection.requestStatus(deviceUid), generation)
        }
    }

    private fun verifyAuthoritativeSurface(
        deviceUid: DeviceUid,
        status: DeviceLightStatus,
        generation: DeviceRuntimeConnectionGeneration,
        requireSystem: Boolean
    ): DeviceLightRuntimeRefreshResult {
        val generationReady =
            runtime.isAuthoritative(deviceUid, generation) &&
                runtime.isGraphAuthoritative(deviceUid, generation)
        val dashboard = runtime.currentDashboard(
            deviceUid,
            DeviceLightDashboardReadAuthority.AUTHORITATIVE
        )
        val libraryReady = runtime.currentLibrary(
            deviceUid,
            DeviceLightLibraryReadAuthority.AUTHORITATIVE
        ) != null
        val automaticReady = runtime.currentAutomatic(
            deviceUid,
            DeviceLightAutomaticReadAuthority.AUTHORITATIVE
        ) != null
        val managedPlanReady = !status.auto.planInstalled ||
            runtime.currentManagedAutoPlan(
                deviceUid,
                DeviceLightManagedPlanReadAuthority.AUTHORITATIVE
            ) != null
        val systemReady = !requireSystem ||
            !status.requiresSystemRuntimeRefresh() ||
            runtime.currentSystem(deviceUid, DeviceLightSystemReadAuthority.AUTHORITATIVE) != null
        val surfaceReady = listOf(
            generationReady,
            dashboard?.status == status,
            libraryReady,
            automaticReady,
            managedPlanReady,
            systemReady
        ).all { ready -> ready }
        return if (surfaceReady) {
            DeviceLightRuntimeRefreshResult.Success(generation, checkNotNull(dashboard))
        } else {
            DeviceLightRuntimeRefreshResult.RejectedStale
        }
    }
}

internal sealed interface DeviceLightRuntimeRefreshResult {
    data class Success(
        val generation: DeviceRuntimeConnectionGeneration,
        val dashboard: DeviceLightDashboardRuntimeState
    ) : DeviceLightRuntimeRefreshResult

    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceLightRuntimeRefreshResult

    data object RejectedStale : DeviceLightRuntimeRefreshResult
    data object Malformed : DeviceLightRuntimeRefreshResult
}

private fun validateGeneration(
    outcome: DeviceRuntimeCommandOutcome<*>,
    expected: DeviceRuntimeConnectionGeneration
): DeviceLightRuntimeRefreshResult? = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success<*> -> if (outcome.generation == expected) {
        null
    } else {
        DeviceLightRuntimeRefreshResult.RejectedStale
    }
    else -> DeviceLightRuntimeRefreshResult.Failed(outcome)
}

private fun DeviceLightRuntimeRefreshResult.forGeneration(
    generation: DeviceRuntimeConnectionGeneration
): DeviceLightRuntimeRefreshResult = when (this) {
    is DeviceLightRuntimeRefreshResult.Success ->
        if (this.generation == generation) this else DeviceLightRuntimeRefreshResult.RejectedStale
    is DeviceLightRuntimeRefreshResult.Failed,
    DeviceLightRuntimeRefreshResult.RejectedStale,
    DeviceLightRuntimeRefreshResult.Malformed -> this
}

private fun DeviceLightStatus.requiresSystemRuntimeRefresh(): Boolean = listOf(
    product == DeviceLightProduct.WRGB_PRO_ELITE,
    features.fanControl,
    features.temperatureSensor,
    features.thermal
).all { supported -> supported }
