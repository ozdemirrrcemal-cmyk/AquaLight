package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import kotlin.math.abs
import kotlinx.coroutines.delay

internal fun DeviceRuntimeCommandOutcome<DeviceCoolingStatus>.toAutomaticRefreshResult():
    DeviceCoolingAutomaticCommandResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        !value.supported || !value.fanSupported || !value.temperatureSupported
    ) {
        automaticFailure(DeviceCoolingAutomaticFailure.Unsupported)
    } else {
        DeviceCoolingAutomaticCommandResult.Success
    }
    else -> automaticFailure(toAutomaticFailure())
}

internal fun DeviceRuntimeCommandOutcome<DeviceCoolingConfigApplyResult>.toAutomaticSaveResult(
    requestedStartC: Double,
    requestedMaximumC: Double
): DeviceCoolingAutomaticCommandResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        value.saveRequested &&
        value.saved &&
        abs(value.config.minTemperatureC - requestedStartC) <= AUTOMATIC_RESULT_EPSILON &&
        abs(value.config.maxTemperatureC - requestedMaximumC) <= AUTOMATIC_RESULT_EPSILON
    ) {
        DeviceCoolingAutomaticCommandResult.Success
    } else {
        automaticFailure(DeviceCoolingAutomaticFailure.Rejected)
    }
    else -> automaticFailure(toAutomaticFailure())
}

internal fun DeviceRuntimeCommandOutcome<*>.toAutomaticFailure(): DeviceCoolingAutomaticFailure =
    when (this) {
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceCoolingAutomaticFailure.Unsupported
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceCoolingAutomaticFailure.NotConnected
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.ProtocolError,
        is DeviceRuntimeCommandOutcome.Cancelled -> DeviceCoolingAutomaticFailure.TemporaryFailure
        is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceCoolingAutomaticFailure.Rejected
        is DeviceRuntimeCommandOutcome.Success -> DeviceCoolingAutomaticFailure.TemporaryFailure
    }

internal fun automaticFailure(
    failure: DeviceCoolingAutomaticFailure
): DeviceCoolingAutomaticCommandResult = DeviceCoolingAutomaticCommandResult.Failed(failure)

internal suspend fun requestAutomaticStatusWithRetry(
    request: suspend () -> DeviceRuntimeCommandOutcome<DeviceCoolingStatus>
): DeviceRuntimeCommandOutcome<DeviceCoolingStatus> {
    var outcome = request()
    repeat(AUTOMATIC_STATUS_REQUEST_MAX_ATTEMPTS - 1) {
        if (!outcome.isAutomaticTransientReadFailure()) return outcome
        delay(AUTOMATIC_STATUS_REQUEST_RETRY_DELAY_MILLIS)
        outcome = request()
    }
    return outcome
}

private fun DeviceRuntimeCommandOutcome<*>.isAutomaticTransientReadFailure(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> true
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

private const val AUTOMATIC_STATUS_REQUEST_MAX_ATTEMPTS = 6
private const val AUTOMATIC_STATUS_REQUEST_RETRY_DELAY_MILLIS = 250L
private const val AUTOMATIC_RESULT_EPSILON = 0.000_001
