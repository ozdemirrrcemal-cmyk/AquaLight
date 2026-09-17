package com.aqua.aqualight.data.devices.light.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data

internal fun DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus>.toReadResult(
    resolution: AdaptationRuntimeResolution.Ready
): DeviceLightAdaptationReadResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        resolution.runtime.isAuthoritative(resolution.deviceUid, generation)
    ) {
        value.toSnapshot(resolution.deviceUid, resolution.requirePolicy())
            ?.let(DeviceLightAdaptationReadResult::Available)
            ?: readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
    } else {
        readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    }
    else -> readFailure(toAdaptationFailure())
}

internal fun DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus>.toMutationResult(
    resolution: AdaptationRuntimeResolution.Ready
): DeviceLightAdaptationMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        resolution.runtime.isAuthoritative(resolution.deviceUid, generation)
    ) {
        value.toSnapshot(resolution.deviceUid, resolution.requirePolicy())
            ?.let(DeviceLightAdaptationMutationResult::Success)
            ?: mutationFailure(DeviceLightAdaptationFailure.INVALID_DATA)
    } else {
        mutationFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    }
    else -> mutationFailure(toAdaptationFailure())
}

private fun DeviceRuntimeCommandOutcome<*>.toAdaptationFailure(): DeviceLightAdaptationFailure =
    when (this) {
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            DeviceLightAdaptationFailure.NOT_CONNECTED
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
            DeviceLightAdaptationFailure.UNSUPPORTED
        is DeviceRuntimeCommandOutcome.FirmwareError -> toFirmwareFailure()
        is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightAdaptationFailure.INVALID_DATA
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightAdaptationFailure.UNAVAILABLE
        is DeviceRuntimeCommandOutcome.Success<*> -> error("Success has no failure mapping.")
    }

private fun DeviceRuntimeCommandOutcome.FirmwareError.toFirmwareFailure(): DeviceLightAdaptationFailure =
    runCatching { lightV1Data().reason }.getOrNull().let { reason ->
        when (reason) {
            DeviceLightErrorReason.Known.STALE_REVISION ->
                DeviceLightAdaptationFailure.STALE_REVISION
            DeviceLightErrorReason.Known.RTC_NOT_READY ->
                DeviceLightAdaptationFailure.CLOCK_NOT_READY
            DeviceLightErrorReason.Known.ACCLIMATION_START_PERCENT,
            DeviceLightErrorReason.Known.ACCLIMATION_DURATION ->
                DeviceLightAdaptationFailure.INVALID_REQUEST
            else -> DeviceLightAdaptationFailure.REJECTED
        }
    }

internal fun readFailure(failure: DeviceLightAdaptationFailure) =
    DeviceLightAdaptationReadResult.Failed(failure)

internal fun mutationFailure(failure: DeviceLightAdaptationFailure) =
    DeviceLightAdaptationMutationResult.Failed(failure)
