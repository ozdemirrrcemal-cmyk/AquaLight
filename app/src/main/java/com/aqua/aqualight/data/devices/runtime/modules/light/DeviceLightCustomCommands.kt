package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

internal fun DeviceLightRuntimeRepository.currentCustom(
    deviceUid: DeviceUid
): DeviceLightCustomDocument? = stateOwner.customProjection.currentAuthoritative(deviceUid)

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

suspend fun DeviceLightRuntimeRepository.clearCustom(
    deviceUid: DeviceUid,
    payload: DeviceLightCustomClearPayload
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.CUSTOM_CLEAR,
        dataFactory = payload::toJson,
        parser = DeviceLightMutationParser.CustomAndAcclimation::parseCustom,
        refreshStatus = true
    )
    return acceptCustomDocument(outcome)
}

private suspend fun DeviceLightRuntimeRepository.acceptCustomDocument(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightCustomDocument>
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success -> acceptSuccessfulCustomDocument(outcome)
    else -> outcome
}

private suspend fun DeviceLightRuntimeRepository.acceptSuccessfulCustomDocument(
    outcome: DeviceRuntimeCommandOutcome.Success<DeviceLightCustomDocument>
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> {
    val accepted = stateOwner.customProjection.record(
        outcome.deviceUid,
        outcome.generation,
        outcome.value
    )
    if (accepted) return outcome
    val refreshed = requestStatus(outcome.deviceUid)
    val acceptedAfterRefresh = if (refreshed is DeviceRuntimeCommandOutcome.Success) {
        val acceptedDocument = stateOwner.customProjection.record(
            outcome.deviceUid,
            outcome.generation,
            outcome.value
        )
        requestGraph(outcome.deviceUid)
        acceptedDocument
    } else {
        false
    }
    return if (acceptedAfterRefresh) outcome else DeviceRuntimeCommandOutcome.ProtocolError(
        deviceUid = outcome.deviceUid,
        module = outcome.module,
        action = outcome.action,
        messageId = outcome.messageId,
        generation = outcome.generation,
        reason = "Custom document did not match the authoritative Light status generation."
    )
}
