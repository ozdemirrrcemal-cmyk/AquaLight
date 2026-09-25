package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestManagedAutoPlan(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PLAN_GET,
        parser = DeviceLightManagedPlanParser::parsePlan
    )
    return acceptManagedAutoPlan(outcome)
}

suspend fun DeviceLightRuntimeRepository.applyManagedAutoPlan(
    deviceUid: DeviceUid,
    payload: DeviceLightManagedAutoPlanApplyPayload
): DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PLAN_APPLY,
        dataFactory = payload::toJson,
        parser = { data, product ->
            payload.phases.forEach { phase ->
                DeviceLightCommandValidation.requireProduct(phase.scene.product, product)
            }
            DeviceLightManagedPlanParser.parsePlan(data, product)
        },
        refreshStatus = true
    )
    return acceptManagedAutoPlan(outcome)
}

suspend fun DeviceLightRuntimeRepository.deleteManagedAutoPlan(
    deviceUid: DeviceUid,
    payload: DeviceLightManagedAutoPlanDeletePayload
): DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlanDeleteResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.AUTO_PLAN_DELETE,
    dataFactory = payload::toJson,
    parser = { data, _ -> DeviceLightManagedPlanParser.parseDelete(data) },
    refreshStatus = true
)

private suspend fun DeviceLightRuntimeRepository.acceptManagedAutoPlan(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan>
): DeviceRuntimeCommandOutcome<DeviceLightManagedAutoPlan> = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val accepted = stateOwner.managedPlanProjection.record(
            outcome.deviceUid,
            outcome.generation,
            outcome.value
        )
        if (accepted) {
            outcome
        } else {
            val refreshed = requestStatus(outcome.deviceUid)
            val acceptedAfterRefresh = refreshed is DeviceRuntimeCommandOutcome.Success &&
                stateOwner.managedPlanProjection.record(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.value
                )
            if (acceptedAfterRefresh) {
                outcome
            } else {
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid = outcome.deviceUid,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    generation = outcome.generation,
                    reason = "Managed AUTO plan did not match authoritative Light status."
                )
            }
        }
    }
    else -> outcome
}
