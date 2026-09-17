package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestCustom(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CUSTOM_GET,
        parser = DeviceLightMutationParser.CustomAndAcclimation::parseCustom
    )
    return acceptCustomDocument(outcome)
}

suspend fun DeviceLightRuntimeRepository.installCustom(
    deviceUid: DeviceUid,
    payload: DeviceLightCustomInstallPayload
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CUSTOM_INSTALL,
        dataFactory = payload::toJson,
        parser = { data, product ->
            DeviceLightCommandValidation.requireProduct(payload.points.first().scene.product, product)
            DeviceLightMutationParser.CustomAndAcclimation.parseCustom(data, product)
        },
        refreshStatus = true
    )
    return acceptCustomDocument(outcome)
}

private suspend fun DeviceLightRuntimeRepository.acceptCustomDocument(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightCustomDocument>
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> {
    if (outcome !is DeviceRuntimeCommandOutcome.Success) return outcome
    if (stateOwner.customProjection.record(outcome.deviceUid, outcome.generation, outcome.value)) {
        return outcome
    }
    val refreshed = requestStatus(outcome.deviceUid)
    if (
        refreshed is DeviceRuntimeCommandOutcome.Success &&
        stateOwner.customProjection.record(outcome.deviceUid, outcome.generation, outcome.value)
    ) {
        return outcome
    }
    return DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = outcome.deviceUid,
        module = outcome.module,
        action = outcome.action,
        messageId = outcome.messageId,
        generation = outcome.generation,
        reason = "Custom document did not match the authoritative Light status generation."
    )
}
