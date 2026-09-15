@file:Suppress("MatchingDeclarationName", "ReturnCount")

package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration

internal sealed interface DeviceLightAuthorityRefreshResult {
    data class Refreshed(
        val generation: DeviceRuntimeConnectionGeneration,
        val status: DeviceLightStatus,
        val plan: DeviceLightManagedPlanDocument,
        val graph: DeviceLightGraph
    ) : DeviceLightAuthorityRefreshResult

    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceLightAuthorityRefreshResult
}

/** Reads and cross-checks the complete Light authority set in a single connection generation. */
internal suspend fun DeviceLightRuntimeRepository.refreshAuthoritySet(
    deviceUid: DeviceUid
): DeviceLightAuthorityRefreshResult {
    // Readers must never observe an old authoritative graph beside a newly read status/plan.
    stateStore.invalidateAuthoritySet(deviceUid)
    val statusOutcome = requestStatus(deviceUid)
    val status = when (statusOutcome) {
        is DeviceRuntimeCommandOutcome.Success -> statusOutcome.value
        else -> return DeviceLightAuthorityRefreshResult.Failed(statusOutcome)
    }
    val planOutcome = requestManagedPlan(deviceUid)
    val plan = when (planOutcome) {
        is DeviceRuntimeCommandOutcome.Success -> planOutcome.value
        else -> {
            stateStore.invalidateAuthoritySet(deviceUid)
            return DeviceLightAuthorityRefreshResult.Failed(planOutcome)
        }
    }
    val graphOutcome = requestGraph(deviceUid)
    val graph = when (graphOutcome) {
        is DeviceRuntimeCommandOutcome.Success -> graphOutcome.value
        else -> {
            stateStore.invalidateAuthoritySet(deviceUid)
            return DeviceLightAuthorityRefreshResult.Failed(graphOutcome)
        }
    }
    if (statusOutcome.generation != planOutcome.generation ||
        statusOutcome.generation != graphOutcome.generation
    ) {
        stateStore.invalidateAuthoritySet(deviceUid)
        return DeviceLightAuthorityRefreshResult.Failed(
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = deviceUid,
                module = DeviceLightRuntimeContract.MODULE,
                action = DeviceLightRuntimeContract.Action.STATUS_GET,
                messageId = statusOutcome.messageId,
                generation = statusOutcome.generation,
                reason = "Light authority set crossed connection generations."
            )
        )
    }
    val valid = runCatching { requireConsistentLightAuthority(status, plan, graph) }
    if (valid.isFailure) {
        stateStore.invalidateAuthoritySet(deviceUid)
        return DeviceLightAuthorityRefreshResult.Failed(
            DeviceRuntimeCommandOutcome.ProtocolError(
                deviceUid = deviceUid,
                module = DeviceLightRuntimeContract.MODULE,
                action = DeviceLightRuntimeContract.Action.STATUS_GET,
                messageId = statusOutcome.messageId,
                generation = statusOutcome.generation,
                reason = valid.exceptionOrNull()?.message.orEmpty()
            )
        )
    }
    return DeviceLightAuthorityRefreshResult.Refreshed(
        generation = statusOutcome.generation,
        status = status,
        plan = plan,
        graph = graph
    )
}

internal fun DeviceLightRuntimeRepository.isAuthoritySetAuthoritative(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration
): Boolean =
    stateStore.isStatusAuthoritative(deviceUid, generation) &&
        stateStore.isManagedPlanAuthoritative(deviceUid, generation) &&
        stateStore.isGraphAuthoritative(deviceUid, generation)

private fun requireConsistentLightAuthority(
    status: DeviceLightStatus,
    plan: DeviceLightManagedPlanDocument,
    graph: DeviceLightGraph
) {
    require(status.storageGeneration == plan.storageGeneration)
    require(status.auto.planRevision == plan.revision)
    require(status.auto.planInstalled == plan.installed)
    require(status.auto.planId == plan.planId)
    require(status.scheduler.ready == plan.runtime.clockReady)
    require(status.auto.planRuntimeState == plan.runtime.state)
    require(status.auto.activePlanPhaseIndex == plan.runtime.activePhaseIndex)
    require(status.auto.planTransitionPermille == plan.runtime.transitionPermille)
    require(status.auto.nextPlanTransitionEpochDay == plan.runtime.nextTransitionEpochDay)
    require(graph.mode == status.mode)
    require(graph.schedulerGeneration == status.scheduler.generation)
    require(graph.localDate == status.scheduler.localDate)
    require(graph.currentWeekdayMask == status.scheduler.currentWeekdayMask)
    require(graph.nowTimeMs == status.scheduler.currentTimeMs)
    if (status.mode == DeviceLightMode.AUTO && plan.installed) {
        require(status.auto.scheduleSource == DeviceLightAutoScheduleSource.MANAGED_PLAN)
        require(graph.basis == DeviceLightGraphBasis.MANAGED_PLAN)
        require(graph.sourceRevision == plan.revision)
        require(graph.autoSpans.isEmpty())
        require(graph.planSpans.all { span -> span.planId == plan.planId })
    } else {
        require(graph.planSpans.isEmpty())
    }
}
