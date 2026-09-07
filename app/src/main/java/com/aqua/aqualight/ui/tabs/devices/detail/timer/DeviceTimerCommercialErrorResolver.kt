package com.aqua.aqualight.ui.tabs.devices.detail.timer

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.timer.DeviceTimerCommandFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure

/** Customer-facing Timer copy resolved exclusively from stable application semantics. */
internal object DeviceTimerCommercialErrorResolver {

    fun resolve(failure: DeviceTimerCommandFailure): DeviceTimerCommercialMessage =
        when (failure) {
            DeviceTimerCommandFailure.CONFLICT -> commercialMessage(
                R.string.device_timer_error_conflict_title,
                R.string.device_timer_error_conflict_message
            )
            DeviceTimerCommandFailure.INVALID_REQUEST -> commercialMessage(
                R.string.device_timer_error_invalid_request_title,
                R.string.device_timer_error_invalid_request_message
            )
            DeviceTimerCommandFailure.INVALID_CONFIGURATION -> commercialMessage(
                R.string.device_timer_error_invalid_configuration_title,
                R.string.device_timer_error_invalid_configuration_message
            )
            DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE -> commercialMessage(
                R.string.device_timer_error_channel_unavailable_title,
                R.string.device_timer_error_channel_unavailable_message
            )
            DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE -> commercialMessage(
                R.string.device_timer_error_resource_unavailable_title,
                R.string.device_timer_error_resource_unavailable_message
            )
            DeviceTimerCommandFailure.HARDWARE_FAILURE -> commercialMessage(
                R.string.device_timer_error_hardware_failure_title,
                R.string.device_timer_error_hardware_failure_message
            )
            DeviceTimerCommandFailure.STORAGE_FAILURE -> commercialMessage(
                R.string.device_timer_error_storage_failure_title,
                R.string.device_timer_error_storage_failure_message
            )
            DeviceTimerCommandFailure.RUNTIME_LOCKED -> commercialMessage(
                R.string.device_timer_error_runtime_locked_title,
                R.string.device_timer_error_runtime_locked_message
            )
            DeviceTimerCommandFailure.PROTOCOL_ERROR -> commercialMessage(
                R.string.device_timer_error_protocol_title,
                R.string.device_timer_error_protocol_message
            )
            DeviceTimerCommandFailure.UNKNOWN_REJECTION -> commercialMessage(
                R.string.device_timer_error_rejected_title,
                R.string.device_timer_error_rejected_message
            )
        }

    fun resolve(failure: DeviceTimerControlFailure): DeviceTimerCommercialMessage =
        when (failure) {
            DeviceTimerControlFailure.Unsupported -> commercialMessage(
                R.string.device_timer_error_unsupported_title,
                R.string.device_timer_error_unsupported_message
            )
            DeviceTimerControlFailure.Unavailable -> commercialMessage(
                R.string.device_timer_error_unavailable_title,
                R.string.device_timer_error_unavailable_message
            )
            DeviceTimerControlFailure.NotConnected -> commercialMessage(
                R.string.device_timer_error_not_connected_title,
                R.string.device_timer_error_not_connected_message
            )
            is DeviceTimerControlFailure.Rejected -> resolve(failure.reason)
            DeviceTimerControlFailure.InvalidData ->
                resolve(DeviceTimerCommandFailure.PROTOCOL_ERROR)
        }

    fun resolveStatus(notice: DeviceTimerChannelStatusNotice): DeviceTimerCommercialMessage =
        when (notice) {
            DeviceTimerChannelStatusNotice.CLOCK_UNAVAILABLE -> commercialMessage(
                R.string.device_timer_status_clock_unavailable_title,
                R.string.device_timer_status_clock_unavailable_message
            )
        }

    private fun commercialMessage(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int
    ) = DeviceTimerCommercialMessage(titleRes = titleRes, messageRes = messageRes)
}

internal data class DeviceTimerCommercialMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int
)

internal fun DeviceTimerCommandFailure.toCommercialTimerError(): DeviceTimerCommercialMessage =
    DeviceTimerCommercialErrorResolver.resolve(this)

internal fun DeviceTimerControlFailure.toCommercialTimerError(): DeviceTimerCommercialMessage =
    DeviceTimerCommercialErrorResolver.resolve(this)

internal fun DeviceTimerChannelStatusNotice.toCommercialTimerStatus():
    DeviceTimerCommercialMessage = DeviceTimerCommercialErrorResolver.resolveStatus(this)
