package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** One owner-scoped status + graph reconciliation flight per Light device. */
internal class DeviceLightDashboardRefreshCoordinator(
    private val runtime: DeviceLightRuntimeRepository
) {
    private val inFlight = ConcurrentHashMap<
        DeviceUid,
        CompletableDeferred<DeviceLightDashboardRefreshResult>
    >()

    suspend fun reconcileCommitted(
        deviceUid: DeviceUid,
        expectedMode: DeviceLightMode
    ): DeviceLightDashboardRefreshResult = runtime.currentDashboard(
        deviceUid,
        DeviceLightDashboardReadAuthority.AUTHORITATIVE
    )
        ?.takeIf { dashboard -> dashboard.status.mode == expectedMode }
        ?.let(DeviceLightDashboardRefreshResult::Success)
        ?: refresh(deviceUid)

    suspend fun refresh(deviceUid: DeviceUid): DeviceLightDashboardRefreshResult {
        val pending = CompletableDeferred<DeviceLightDashboardRefreshResult>()
        val existing = inFlight.putIfAbsent(deviceUid, pending)
        if (existing != null) return existing.await()

        return try {
            val result = refreshOnce(deviceUid)
            pending.complete(result)
            result
        } catch (cancellation: CancellationException) {
            pending.complete(DeviceLightDashboardRefreshResult.RejectedStale)
            throw cancellation
        } catch (_: Exception) {
            val result = DeviceLightDashboardRefreshResult.Malformed
            pending.complete(result)
            result
        } finally {
            pending.complete(DeviceLightDashboardRefreshResult.Malformed)
            inFlight.remove(deviceUid, pending)
        }
    }

    private suspend fun refreshOnce(
        deviceUid: DeviceUid
    ): DeviceLightDashboardRefreshResult = when (val status = runtime.requestStatus(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> refreshGraph(deviceUid, status)
        else -> DeviceLightDashboardRefreshResult.Failed(status)
    }

    private suspend fun refreshGraph(
        deviceUid: DeviceUid,
        status: DeviceRuntimeCommandOutcome.Success<DeviceLightStatus>
    ): DeviceLightDashboardRefreshResult = when (val graph = runtime.requestGraph(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> reconcileDashboard(deviceUid, status, graph)
        else -> DeviceLightDashboardRefreshResult.Failed(graph)
    }

    private fun reconcileDashboard(
        deviceUid: DeviceUid,
        status: DeviceRuntimeCommandOutcome.Success<DeviceLightStatus>,
        graph: DeviceRuntimeCommandOutcome.Success<DeviceLightGraph>
    ): DeviceLightDashboardRefreshResult = when {
        status.generation != graph.generation ->
            DeviceLightDashboardRefreshResult.RejectedStale
        else -> acceptedDashboard(deviceUid, status, graph)
    }

    private fun acceptedDashboard(
        deviceUid: DeviceUid,
        status: DeviceRuntimeCommandOutcome.Success<DeviceLightStatus>,
        graph: DeviceRuntimeCommandOutcome.Success<DeviceLightGraph>
    ): DeviceLightDashboardRefreshResult {
        val dashboard = runtime.currentDashboard(
            deviceUid,
            DeviceLightDashboardReadAuthority.AUTHORITATIVE
        )
        return if (
            dashboard?.status == status.value &&
            dashboard.graph == graph.value
        ) {
            DeviceLightDashboardRefreshResult.Success(dashboard)
        } else {
            DeviceLightDashboardRefreshResult.Malformed
        }
    }
}

internal sealed interface DeviceLightDashboardRefreshResult {
    data class Success(
        val dashboard: DeviceLightDashboardRuntimeState
    ) : DeviceLightDashboardRefreshResult

    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceLightDashboardRefreshResult

    data object RejectedStale : DeviceLightDashboardRefreshResult
    data object Malformed : DeviceLightDashboardRefreshResult
}
