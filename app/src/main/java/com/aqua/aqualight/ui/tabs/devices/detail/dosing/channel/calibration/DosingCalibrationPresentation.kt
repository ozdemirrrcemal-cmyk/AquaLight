package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure

internal fun DeviceDosingCalibrationUiState.illustrationOperationDurationMillis(): Int = when (step) {
    DeviceDosingCalibrationStep.CALIBRATION_RUN -> operationDurationMs
        .takeIf { it > 0L }
        ?.coerceIn(MIN_ILLUSTRATION_OPERATION_MILLIS, MAX_ILLUSTRATION_OPERATION_MILLIS)
        ?.toInt()
        ?: DEFAULT_ILLUSTRATION_DURATION_MILLIS
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

internal val DeviceDosingCalibrationError.validationMessageRes: Int
    @StringRes get() = when (this) {
        DeviceDosingCalibrationError.DISPLAY_NAME_REQUIRED ->
            R.string.device_dosing_calibration_name_required
        DeviceDosingCalibrationError.DISPLAY_NAME_CONTROL_CHARACTER ->
            R.string.device_dosing_calibration_name_control_character
        DeviceDosingCalibrationError.DISPLAY_NAME_TOO_LONG ->
            R.string.device_dosing_calibration_name_too_long
        DeviceDosingCalibrationError.INVALID_MEASUREMENT ->
            R.string.device_dosing_calibration_invalid_measurement
        else -> error("$this is an operational calibration error")
    }

internal fun DeviceDosingCalibrationError.toOperationalFailureOrNull():
    DeviceDosingCalibrationFailure? =
    when (this) {
        DeviceDosingCalibrationError.DISPLAY_NAME_REQUIRED,
        DeviceDosingCalibrationError.DISPLAY_NAME_CONTROL_CHARACTER,
        DeviceDosingCalibrationError.DISPLAY_NAME_TOO_LONG,
        DeviceDosingCalibrationError.INVALID_MEASUREMENT -> null
        DeviceDosingCalibrationError.CONNECTION -> DeviceDosingCalibrationFailure.CONNECTION
        DeviceDosingCalibrationError.STORAGE -> DeviceDosingCalibrationFailure.STORAGE
        DeviceDosingCalibrationError.HARDWARE -> DeviceDosingCalibrationFailure.HARDWARE
        DeviceDosingCalibrationError.OUTPUT_STOP_UNCONFIRMED ->
            DeviceDosingCalibrationFailure.OUTPUT_STOP_UNCONFIRMED
        DeviceDosingCalibrationError.OPERATION_IN_PROGRESS ->
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
        DeviceDosingCalibrationError.DEVICE_TIME_NOT_READY ->
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY
        DeviceDosingCalibrationError.CALIBRATION_STATE_MISMATCH ->
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
        DeviceDosingCalibrationError.OPERATION_FAILED -> DeviceDosingCalibrationFailure.INTERNAL
    }
