package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestCustom(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.CUSTOM_GET,
    parser = DeviceLightMutationParser.CustomAndAcclimation::parseCustom
)

suspend fun DeviceLightRuntimeRepository.installCustom(
    deviceUid: DeviceUid,
    payload: DeviceLightCustomInstallPayload
): DeviceRuntimeCommandOutcome<DeviceLightCustomDocument> = productCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.CUSTOM_INSTALL,
    dataFactory = payload::toJson,
    parser = { data, product ->
        DeviceLightCommandValidation.requireProduct(payload.points.first().scene.product, product)
        DeviceLightMutationParser.CustomAndAcclimation.parseCustom(data, product)
    },
    refreshStatus = true
)
