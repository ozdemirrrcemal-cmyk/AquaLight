package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.CancellationException

/** Coherent status + graph reader used only inside the central Light refresh coordinator. */
internal class DeviceLightDashboardRefreshCoordinator(
    private val runtime: DeviceLightRuntimeRepository
) {

    suspend fun refresh(deviceUid: DeviceUid): DeviceLightDashboardRefreshResult =
        try {
            refreshOnce(deviceUid)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DeviceLightDashboardRefreshResult.Malformed
        }

    private suspend fun refreshOnce(
        deviceUid: DeviceUid
    ): DeviceLightDashboardRefreshResult = when (val status = runtime.requestStatus(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> refreshCustomIfNeeded(deviceUid, status)
        else -> DeviceLightDashboardRefreshResult.Failed(status)
    }

    private suspend fun refreshCustomIfNeeded(
        deviceUid: DeviceUid,
        status: DeviceRuntimeCommandOutcome.Success<DeviceLightStatus>
    ): DeviceLightDashboardRefreshResult {
        if (status.value.mode != DeviceLightMode.CUSTOM) {
            return refreshGraph(deviceUid, status)
        }
        return when (val custom = runtime.requestCustom(deviceUid)) {
            is DeviceRuntimeCommandOutcome.Success -> refreshGraph(deviceUid, status)
            else -> DeviceLightDashboardRefreshResult.Failed(custom)
        }
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
            DeviceLightDashboardRefreshResult.Success(
                generation = status.generation,
                dashboard = dashboard
            )
        } else {
            DeviceLightDashboardRefreshResult.Malformed
        }
    }
}

internal sealed interface DeviceLightDashboardRefreshResult {
    data class Success(
        val generation: DeviceRuntimeConnectionGeneration,
        val dashboard: DeviceLightDashboardRuntimeState
    ) : DeviceLightDashboardRefreshResult

    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceLightDashboardRefreshResult

    data object RejectedStale : DeviceLightDashboardRefreshResult
    data object Malformed : DeviceLightDashboardRefreshResult
}
