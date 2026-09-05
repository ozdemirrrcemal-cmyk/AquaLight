package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure

/** Customer-facing Cooling error copy is resolved only from stable application semantics. */
internal data class DeviceCoolingCommercialErrorMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

internal fun DeviceCoolingCommandFailure.toCommercialCoolingError():
    DeviceCoolingCommercialErrorMessage = when (this) {
    DeviceCoolingCommandFailure.CONFLICT -> commercialError(
        R.string.device_cooling_error_conflict_title,
        R.string.device_cooling_error_conflict_message
    )
    DeviceCoolingCommandFailure.INVALID_REQUEST -> commercialError(
        R.string.device_cooling_error_invalid_request_title,
        R.string.device_cooling_error_invalid_request_message
    )
    DeviceCoolingCommandFailure.INVALID_CONFIGURATION -> commercialError(
        R.string.device_cooling_error_invalid_configuration_title,
        R.string.device_cooling_error_invalid_configuration_message
    )
    DeviceCoolingCommandFailure.MANUAL_MODE_REQUIRED -> commercialError(
        R.string.device_cooling_error_manual_mode_required_title,
        R.string.device_cooling_error_manual_mode_required_message
    )
    DeviceCoolingCommandFailure.HARDWARE_UNAVAILABLE -> commercialError(
        R.string.device_cooling_error_hardware_unavailable_title,
        R.string.device_cooling_error_hardware_unavailable_message
    )
    DeviceCoolingCommandFailure.HARDWARE_FAILURE -> commercialError(
        R.string.device_cooling_error_hardware_failure_title,
        R.string.device_cooling_error_hardware_failure_message
    )
    DeviceCoolingCommandFailure.STORAGE_FAILURE -> commercialError(
        R.string.device_cooling_error_storage_failure_title,
        R.string.device_cooling_error_storage_failure_message
    )
    DeviceCoolingCommandFailure.CLOCK_UNSYNCED -> commercialError(
        R.string.device_cooling_error_clock_unsynced_title,
        R.string.device_cooling_error_clock_unsynced_message
    )
    DeviceCoolingCommandFailure.PROTOCOL_ERROR -> commercialError(
        R.string.device_cooling_error_protocol_title,
        R.string.device_cooling_error_protocol_message
    )
    DeviceCoolingCommandFailure.UNKNOWN_REJECTION -> commercialError(
        R.string.device_cooling_error_rejected_title,
        R.string.device_cooling_error_rejected_message
    )
}

internal fun DeviceCoolingControlFailure.toCommercialCoolingError():
    DeviceCoolingCommercialErrorMessage = when (this) {
    DeviceCoolingControlFailure.Unsupported -> unsupportedCoolingError()
    DeviceCoolingControlFailure.Unavailable -> unavailableCoolingError()
    DeviceCoolingControlFailure.NotConnected -> notConnectedCoolingError()
    is DeviceCoolingControlFailure.Rejected -> reason.toCommercialCoolingError()
    DeviceCoolingControlFailure.InvalidData ->
        DeviceCoolingCommandFailure.PROTOCOL_ERROR.toCommercialCoolingError()
}

internal fun DeviceCoolingAutomaticFailure.toCommercialCoolingError():
    DeviceCoolingCommercialErrorMessage = when (this) {
    DeviceCoolingAutomaticFailure.Unsupported -> unsupportedCoolingError()
    DeviceCoolingAutomaticFailure.Unavailable,
    DeviceCoolingAutomaticFailure.TemporaryFailure -> unavailableCoolingError()
    DeviceCoolingAutomaticFailure.NotConnected -> notConnectedCoolingError()
    DeviceCoolingAutomaticFailure.ReadOnly -> commercialError(
        R.string.device_cooling_error_read_only_title,
        R.string.device_cooling_error_read_only_message
    )
    DeviceCoolingAutomaticFailure.InvalidConfiguration ->
        DeviceCoolingCommandFailure.INVALID_CONFIGURATION.toCommercialCoolingError()
    is DeviceCoolingAutomaticFailure.Rejected -> reason.toCommercialCoolingError()
}

private fun unsupportedCoolingError() = commercialError(
    R.string.device_cooling_error_unsupported_title,
    R.string.device_cooling_error_unsupported_message
)

private fun unavailableCoolingError() = commercialError(
    R.string.device_cooling_error_unavailable_title,
    R.string.device_cooling_error_unavailable_message
)

private fun notConnectedCoolingError() = commercialError(
    R.string.device_cooling_error_not_connected_title,
    R.string.device_cooling_error_not_connected_message
)

private fun commercialError(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int
) = DeviceCoolingCommercialErrorMessage(titleRes = titleRes, messageRes = messageRes)
