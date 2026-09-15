package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestGraph(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightGraph> {
    val outcome = productCommand(
        deviceUid = deviceUid,
        action = DeviceLightRuntimeContract.Action.GRAPH_GET,
        parser = DeviceLightMutationParser.Graph::parseGraph
    )
    if (outcome is DeviceRuntimeCommandOutcome.Success) {
        stateStore.recordGraph(deviceUid, outcome.generation, outcome.value)
    }
    return outcome
}

suspend fun DeviceLightRuntimeRepository.setPreview(
    deviceUid: DeviceUid,
    payload: DeviceLightPreviewSetPayload
): DeviceRuntimeCommandOutcome<DeviceLightPreviewResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.PREVIEW_SET,
    dataFactory = payload::toJson,
    parser = { data, product ->
        if (payload is DeviceLightPreviewSetPayload.Scene) {
            DeviceLightCommandValidation.requireProduct(payload.scene.product, product)
        }
        DeviceLightMutationParser.parsePreviewSet(data)
    },
    refreshStatus = true
)

suspend fun DeviceLightRuntimeRepository.clearPreview(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightPreviewResult> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.PREVIEW_CLEAR,
    parser = { data, _ -> DeviceLightMutationParser.parsePreviewClear(data) },
    refreshStatus = true
)
