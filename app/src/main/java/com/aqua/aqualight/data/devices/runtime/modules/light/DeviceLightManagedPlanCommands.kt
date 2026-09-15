package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestManagedPlan(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightManagedPlanDocument> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PLAN_GET,
        parser = DeviceLightManagedPlanParser::parseDocument
    )
    if (outcome is DeviceRuntimeCommandOutcome.Success) {
        stateStore.recordManagedPlan(deviceUid, outcome.generation, outcome.value)
    }
    return outcome
}

suspend fun DeviceLightRuntimeRepository.applyManagedPlan(
    deviceUid: DeviceUid,
    payload: DeviceLightManagedPlanApplyPayload
): DeviceRuntimeCommandOutcome<DeviceLightManagedPlanDocument> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PLAN_APPLY,
        dataFactory = payload::toJson,
        parser = { data, product ->
            payload.phases.forEach { phase ->
                DeviceLightCommandValidation.requireProduct(phase.scene.product, product)
            }
            DeviceLightManagedPlanParser.parseDocument(data, product)
        }
    )
    outcome.invalidateManagedAuthoritySet(stateStore, deviceUid)
    return outcome
}

suspend fun DeviceLightRuntimeRepository.deleteManagedPlan(
    deviceUid: DeviceUid,
    payload: DeviceLightManagedPlanDeletePayload
): DeviceRuntimeCommandOutcome<DeviceLightManagedPlanDeleteResult> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PLAN_DELETE,
        dataFactory = payload::toJson,
        parser = { data, _ -> DeviceLightManagedPlanParser.parseDelete(data) }
    )
    outcome.invalidateManagedAuthoritySet(stateStore, deviceUid)
    return outcome
}

private fun DeviceRuntimeCommandOutcome<*>.invalidateManagedAuthoritySet(
    stateStore: DeviceLightRuntimeStateStore,
    deviceUid: DeviceUid
) {
    when (this) {
        is DeviceRuntimeCommandOutcome.Success ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.SendFailed ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.Timeout ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.ProtocolError ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.Cancelled ->
            stateStore.invalidateAuthoritySet(deviceUid, generation)
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> Unit
    }
}
