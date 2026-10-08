package com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.base.BaseActivity

/** Customer-facing Dosing error copy resolved from stable application semantics and operation context. */
internal object DeviceDosingCommercialErrorResolver {

    fun resolve(
        failure: DeviceDosingChannelRejection,
        context: DeviceDosingErrorContext
    ): DeviceDosingCommercialErrorMessage = commercialError(
        context = context,
        messageRes = when (context) {
            DeviceDosingErrorContext.PLAN_SAVE -> failure.planSaveMessageRes()
            DeviceDosingErrorContext.CHANNEL_DETAIL,
            DeviceDosingErrorContext.MANUAL_DOSE,
            DeviceDosingErrorContext.RESERVOIR_SAVE -> failure.channelMessageRes()
            DeviceDosingErrorContext.REFILL,
            DeviceDosingErrorContext.CALIBRATION ->
                R.string.device_dosing_detail_operation_failed
            DeviceDosingErrorContext.CHANNEL_OPEN ->
                R.string.device_dosing_channel_open_failed
        },
        severity = if (context == DeviceDosingErrorContext.PLAN_SAVE) {
            DeviceDosingCommercialErrorSeverity.WARNING
        } else {
            DeviceDosingCommercialErrorSeverity.ERROR
        }
    )

    fun resolve(
        failure: DeviceDosingCalibrationFailure
    ): DeviceDosingCommercialErrorMessage = commercialError(
        context = DeviceDosingErrorContext.CALIBRATION,
        messageRes = when (failure) {
            DeviceDosingCalibrationFailure.CONNECTION ->
                R.string.device_dosing_calibration_connection_error
            DeviceDosingCalibrationFailure.STORAGE ->
                R.string.device_dosing_calibration_storage_error
            DeviceDosingCalibrationFailure.HARDWARE ->
                R.string.device_dosing_calibration_hardware_error
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED ->
                R.string.device_dosing_error_output_stop_unconfirmed
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS ->
                R.string.device_dosing_calibration_operation_in_progress
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY ->
                R.string.device_dosing_calibration_device_time_not_ready
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH ->
                R.string.device_dosing_calibration_state_mismatch
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT ->
                R.string.device_dosing_calibration_invalid_measurement
            DeviceDosingCalibrationFailure.INTERNAL ->
                R.string.device_dosing_calibration_operation_failed
        },
        severity = when (failure) {
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT,
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS,
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY,
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH ->
                DeviceDosingCommercialErrorSeverity.WARNING
            DeviceDosingCalibrationFailure.CONNECTION,
            DeviceDosingCalibrationFailure.STORAGE,
            DeviceDosingCalibrationFailure.HARDWARE,
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED,
            DeviceDosingCalibrationFailure.INTERNAL ->
                DeviceDosingCommercialErrorSeverity.ERROR
        }
    )

    fun resolve(
        failure: DeviceDosingOperationFailure,
        context: DeviceDosingErrorContext
    ): DeviceDosingCommercialErrorMessage = commercialError(
        context = context,
        messageRes = when (context) {
            DeviceDosingErrorContext.CHANNEL_OPEN ->
                R.string.device_dosing_channel_open_failed
            DeviceDosingErrorContext.PLAN_SAVE -> if (
                failure == DeviceDosingOperationFailure.UNAVAILABLE
            ) {
                R.string.device_dosing_plan_unavailable
            } else {
                R.string.device_dosing_detail_operation_failed
            }
            DeviceDosingErrorContext.CHANNEL_DETAIL,
            DeviceDosingErrorContext.MANUAL_DOSE -> if (
                failure == DeviceDosingOperationFailure.UNAVAILABLE
            ) {
                R.string.device_dosing_detail_error_unavailable
            } else {
                R.string.device_dosing_detail_operation_failed
            }
            DeviceDosingErrorContext.RESERVOIR_SAVE,
            DeviceDosingErrorContext.REFILL ->
                R.string.device_dosing_detail_operation_failed
            DeviceDosingErrorContext.CALIBRATION -> if (
                failure == DeviceDosingOperationFailure.CONNECTION
            ) {
                R.string.device_dosing_calibration_connection_error
            } else {
                R.string.device_dosing_calibration_operation_failed
            }
        },
        severity = DeviceDosingCommercialErrorSeverity.ERROR
    )

    private fun DeviceDosingChannelRejection.channelMessageRes(): Int = when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT ->
            R.string.device_dosing_detail_error_invalid_input
        DeviceDosingChannelRejection.NOT_EDITABLE ->
            R.string.device_dosing_detail_error_not_editable
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            R.string.device_dosing_detail_error_calibration_required
        DeviceDosingChannelRejection.BUSY -> R.string.device_dosing_detail_error_busy
        DeviceDosingChannelRejection.CONFLICT ->
            R.string.device_dosing_detail_error_state_changed
        DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED ->
            R.string.device_dosing_error_output_stop_unconfirmed
        DeviceDosingChannelRejection.UNSAFE ->
            R.string.device_dosing_detail_error_safety_blocked
        DeviceDosingChannelRejection.UNKNOWN ->
            R.string.device_dosing_detail_operation_failed
    }

    private fun DeviceDosingChannelRejection.planSaveMessageRes(): Int = when (this) {
        DeviceDosingChannelRejection.INVALID_DRAFT ->
            R.string.device_dosing_plan_invalid_schedule
        DeviceDosingChannelRejection.NOT_EDITABLE ->
            R.string.device_dosing_plan_rejected_not_editable
        DeviceDosingChannelRejection.NOT_CALIBRATED ->
            R.string.device_dosing_plan_rejected_not_calibrated
        DeviceDosingChannelRejection.BUSY -> R.string.device_dosing_plan_rejected_busy
        DeviceDosingChannelRejection.CONFLICT -> R.string.device_dosing_plan_rejected_conflict
        DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED ->
            R.string.device_dosing_error_output_stop_unconfirmed
        DeviceDosingChannelRejection.UNSAFE -> R.string.device_dosing_plan_rejected_unsafe
        DeviceDosingChannelRejection.UNKNOWN ->
            R.string.device_dosing_detail_operation_failed
    }

    private fun commercialError(
        context: DeviceDosingErrorContext,
        @StringRes messageRes: Int,
        severity: DeviceDosingCommercialErrorSeverity
    ) = DeviceDosingCommercialErrorMessage(
        titleRes = context.titleRes(),
        messageRes = messageRes,
        severity = severity
    )

    private fun DeviceDosingErrorContext.titleRes(): Int = when (this) {
        DeviceDosingErrorContext.CHANNEL_OPEN,
        DeviceDosingErrorContext.CHANNEL_DETAIL -> R.string.device_family_dosing
        DeviceDosingErrorContext.MANUAL_DOSE -> R.string.device_dosing_detail_manual_title
        DeviceDosingErrorContext.PLAN_SAVE -> R.string.device_dosing_detail_plan_title
        DeviceDosingErrorContext.RESERVOIR_SAVE,
        DeviceDosingErrorContext.REFILL -> R.string.device_dosing_detail_reservoir_title
        DeviceDosingErrorContext.CALIBRATION -> R.string.device_dosing_detail_calibration_title
    }
}

internal enum class DeviceDosingErrorContext {
    CHANNEL_OPEN,
    CHANNEL_DETAIL,
    MANUAL_DOSE,
    PLAN_SAVE,
    RESERVOIR_SAVE,
    REFILL,
    CALIBRATION
}

internal enum class DeviceDosingOperationFailure {
    CONNECTION,
    UNAVAILABLE,
    INTERNAL
}

internal enum class DeviceDosingCommercialErrorSeverity {
    WARNING,
    ERROR
}

internal data class DeviceDosingCommercialErrorMessage(
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
    val severity: DeviceDosingCommercialErrorSeverity
)

internal fun DeviceDosingChannelRejection.toCommercialDosingError(
    context: DeviceDosingErrorContext
): DeviceDosingCommercialErrorMessage = DeviceDosingCommercialErrorResolver.resolve(this, context)

internal fun DeviceDosingCalibrationFailure.toCommercialDosingError():
    DeviceDosingCommercialErrorMessage = DeviceDosingCommercialErrorResolver.resolve(this)

internal fun DeviceDosingOperationFailure.toCommercialDosingError(
    context: DeviceDosingErrorContext
): DeviceDosingCommercialErrorMessage = DeviceDosingCommercialErrorResolver.resolve(this, context)

internal fun DeviceDosingCommercialErrorSeverity.toSnackType(): BaseActivity.SnackType =
    when (this) {
        DeviceDosingCommercialErrorSeverity.WARNING -> BaseActivity.SnackType.WARNING
        DeviceDosingCommercialErrorSeverity.ERROR -> BaseActivity.SnackType.ERROR
    }
