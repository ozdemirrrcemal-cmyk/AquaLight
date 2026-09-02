package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.annotation.StringRes
import com.aqua.aqualight.R

internal fun DeviceDosingCalibrationUiState.illustrationOperationDurationMillis(): Int = when (step) {
    DeviceDosingCalibrationStep.CALIBRATION_RUN -> CALIBRATION_RUN_DURATION_MILLIS
    DeviceDosingCalibrationStep.VERIFICATION -> candidateDoseMsPerMl
        ?.times(VERIFICATION_DOSE_ML)
        ?.coerceIn(MIN_ILLUSTRATION_OPERATION_MILLIS, MAX_ILLUSTRATION_OPERATION_MILLIS)
        ?.toInt()
        ?: DEFAULT_VERIFICATION_DURATION_MILLIS
    else -> DEFAULT_ILLUSTRATION_DURATION_MILLIS
}

internal val DeviceDosingCalibrationStep.titleRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME -> R.string.device_dosing_calibration_name_title
        DeviceDosingCalibrationStep.PRIME -> R.string.device_dosing_calibration_prime_title
        DeviceDosingCalibrationStep.CALIBRATION_RUN -> R.string.device_dosing_calibration_run_title
        DeviceDosingCalibrationStep.MEASUREMENT -> R.string.device_dosing_calibration_measure_title
        DeviceDosingCalibrationStep.VERIFICATION -> R.string.device_dosing_calibration_verify_title
        DeviceDosingCalibrationStep.CONFIRMATION -> R.string.device_dosing_calibration_confirm_title
    }

internal val DeviceDosingCalibrationStep.descriptionRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME -> R.string.device_dosing_calibration_name_description
        DeviceDosingCalibrationStep.PRIME -> R.string.device_dosing_calibration_prime_description
        DeviceDosingCalibrationStep.CALIBRATION_RUN ->
            R.string.device_dosing_calibration_run_description
        DeviceDosingCalibrationStep.MEASUREMENT -> R.string.device_dosing_calibration_measure_description
        DeviceDosingCalibrationStep.VERIFICATION -> R.string.device_dosing_calibration_verify_description
        DeviceDosingCalibrationStep.CONFIRMATION -> R.string.device_dosing_calibration_confirm_description
    }

internal val DeviceDosingCalibrationStep.illustrationDescriptionRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationStep.NAME ->
            R.string.device_dosing_calibration_name_illustration_description
        DeviceDosingCalibrationStep.PRIME ->
            R.string.device_dosing_calibration_prime_illustration_description
        DeviceDosingCalibrationStep.CALIBRATION_RUN ->
            R.string.device_dosing_calibration_run_illustration_description
        DeviceDosingCalibrationStep.MEASUREMENT ->
            R.string.device_dosing_calibration_measure_illustration_description
        DeviceDosingCalibrationStep.VERIFICATION ->
            R.string.device_dosing_calibration_verify_illustration_description
        DeviceDosingCalibrationStep.CONFIRMATION ->
            R.string.device_dosing_calibration_confirm_illustration_description
    }

internal val DeviceDosingCalibrationError.messageRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationError.DISPLAY_NAME_REQUIRED ->
            R.string.device_dosing_calibration_name_required
        DeviceDosingCalibrationError.DISPLAY_NAME_CONTROL_CHARACTER ->
            R.string.device_dosing_calibration_name_control_character
        DeviceDosingCalibrationError.DISPLAY_NAME_TOO_LONG ->
            R.string.device_dosing_calibration_name_too_long
        DeviceDosingCalibrationError.INVALID_MEASUREMENT ->
            R.string.device_dosing_calibration_invalid_measurement
        DeviceDosingCalibrationError.CONNECTION -> R.string.device_dosing_calibration_connection_error
        DeviceDosingCalibrationError.STORAGE -> R.string.device_dosing_calibration_storage_error
        DeviceDosingCalibrationError.HARDWARE -> R.string.device_dosing_calibration_hardware_error
        DeviceDosingCalibrationError.OUTPUT_STOP_UNCONFIRMED ->
            R.string.device_dosing_error_output_stop_unconfirmed
        DeviceDosingCalibrationError.OPERATION_IN_PROGRESS ->
            R.string.device_dosing_calibration_operation_in_progress
        DeviceDosingCalibrationError.DEVICE_TIME_NOT_READY ->
            R.string.device_dosing_calibration_device_time_not_ready
        DeviceDosingCalibrationError.CALIBRATION_STATE_MISMATCH ->
            R.string.device_dosing_calibration_state_mismatch
        DeviceDosingCalibrationError.OPERATION_FAILED ->
            R.string.device_dosing_calibration_operation_failed
    }
