package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestAutoPrograms(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightAutoPrograms> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.AUTO_PROGRAMS_GET,
        parser = DeviceLightMutationParser::parseAutoPrograms
    )
    return acceptAutoPrograms(outcome)
}

private suspend fun DeviceLightRuntimeRepository.acceptAutoPrograms(
    outcome: DeviceRuntimeCommandOutcome<DeviceLightAutoPrograms>
): DeviceRuntimeCommandOutcome<DeviceLightAutoPrograms> = when (outcome) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val accepted = stateOwner.automaticProjection.record(
            outcome.deviceUid,
            outcome.generation,
            outcome.value
        )
        if (accepted) {
            outcome
        } else {
            val refreshed = requestStatus(outcome.deviceUid)
            val acceptedAfterRefresh = refreshed is DeviceRuntimeCommandOutcome.Success &&
                stateOwner.automaticProjection.record(
                    outcome.deviceUid,
                    outcome.generation,
                    outcome.value
                )
            if (refreshed is DeviceRuntimeCommandOutcome.Success) requestGraph(outcome.deviceUid)
            if (acceptedAfterRefresh) {
                outcome
            } else {
                DeviceRuntimeCommandOutcome.ProtocolError(
                    deviceUid = outcome.deviceUid,
                    module = outcome.module,
                    action = outcome.action,
                    messageId = outcome.messageId,
                    generation = outcome.generation,
                    reason = "Automatic programs did not match the authoritative Light status generation."
                )
            }
        }
    }
    else -> outcome
}

suspend fun DeviceLightRuntimeRepository.createAutoProgram(
    deviceUid: DeviceUid,
    payload: DeviceLightAutoProgramCreatePayload
): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_CREATE,
    dataFactory = payload::toJson,
    parser = { data, product ->
        DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
        DeviceLightMutationParser.parseAutoProgramMutation(data, product)
    },
    refreshStatus = true
)

suspend fun DeviceLightRuntimeRepository.updateAutoProgram(
    deviceUid: DeviceUid,
    payload: DeviceLightAutoProgramUpdatePayload
): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_UPDATE,
    dataFactory = payload::toJson,
    parser = { data, product ->
        DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
        DeviceLightMutationParser.parseAutoProgramMutation(data, product)
    },
    refreshStatus = true
)

suspend fun DeviceLightRuntimeRepository.setAutoProgramEnabled(
    deviceUid: DeviceUid,
    payload: DeviceLightAutoProgramEnabledSetPayload
): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramMutationResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_ENABLED_SET,
    dataFactory = payload::toJson,
    parser = DeviceLightMutationParser::parseAutoProgramMutation,
    refreshStatus = true
)

suspend fun DeviceLightRuntimeRepository.deleteAutoProgram(
    deviceUid: DeviceUid,
    payload: DeviceLightAutoProgramDeletePayload
): DeviceRuntimeCommandOutcome<DeviceLightAutoProgramDeleteResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.AUTO_PROGRAM_DELETE,
    dataFactory = payload::toJson,
    parser = { data, _ -> DeviceLightMutationParser.parseAutoProgramDelete(data) },
    refreshStatus = true
)
