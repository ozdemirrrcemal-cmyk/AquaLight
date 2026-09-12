package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

suspend fun DeviceLightRuntimeRepository.requestAcclimationStatus(
    deviceUid: DeviceUid
): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.ACCLIMATION_STATUS_GET,
    parser = DeviceLightMutationParser.CustomAndAcclimation::parseAcclimation
)

suspend fun DeviceLightRuntimeRepository.startAcclimation(
    deviceUid: DeviceUid,
    payload: DeviceLightAcclimationStartPayload
): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.ACCLIMATION_START,
    dataFactory = payload::toJson,
    parser = DeviceLightMutationParser.CustomAndAcclimation::parseAcclimation,
    refreshStatus = true
)

suspend fun DeviceLightRuntimeRepository.stopAcclimation(
    deviceUid: DeviceUid,
    payload: DeviceLightAcclimationStopPayload
): DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus> = acclimationCommand(
    deviceUid = deviceUid,
    action = DeviceLightRuntimeContract.Action.ACCLIMATION_STOP,
    dataFactory = payload::toJson,
    parser = DeviceLightMutationParser.CustomAndAcclimation::parseAcclimation,
    refreshStatus = true
)
