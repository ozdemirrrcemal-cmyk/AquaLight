package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.aqua.aqualight.R

enum class DosingCalibrationStep(
    val position: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int
) {
    NAME(
        position = 1,
        titleRes = R.string.device_dosing_calibration_name_title,
        descriptionRes = R.string.device_dosing_calibration_name_description
    ),
    PRIME(
        position = 2,
        titleRes = R.string.device_dosing_calibration_prime_title,
        descriptionRes = R.string.device_dosing_calibration_prime_description
    ),
    CALIBRATION_DOSE(
        position = 3,
        titleRes = R.string.device_dosing_calibration_dose_title,
        descriptionRes = R.string.device_dosing_calibration_dose_description
    ),
    MEASURE(
        position = 4,
        titleRes = R.string.device_dosing_calibration_measure_title,
        descriptionRes = R.string.device_dosing_calibration_measure_description
    ),
    VERIFY_DOSE(
        position = 5,
        titleRes = R.string.device_dosing_calibration_verify_title,
        descriptionRes = R.string.device_dosing_calibration_verify_description
    ),
    CONFIRM(
        position = 6,
        titleRes = R.string.device_dosing_calibration_confirm_title,
        descriptionRes = R.string.device_dosing_calibration_confirm_description
    );

    companion object {
        const val COUNT = 6
    }
}

enum class DosingCalibrationOperation {
    LOADING,
    IDLE,
    SAVING_NAME,
    PRIMING,
    STARTING_PRIME,
    STOPPING_PRIME,
    CALIBRATION_DOSING,
    SAVING_MEASUREMENT,
    VERIFYING,
    CONFIRMING,
    RESETTING,
    EXITING,
    ERROR
}

@Immutable
data class DosingCalibrationUiState(
    val deviceUid: String = "",
    val channelKey: String = "",
    val pumpCount: Int = 0,
    val channelNumber: Int = 0,
    val step: DosingCalibrationStep = DosingCalibrationStep.NAME,
    val operation: DosingCalibrationOperation = DosingCalibrationOperation.LOADING,
    val originalDisplayName: String = "",
    val displayNameInput: String = "",
    val measuredMlInput: String = "",
    val verificationMlInput: String = "",
    val minimumMeasuredMl: Double = 0.0,
    val maximumMeasuredMl: Double = 0.0,
    val maximumVerificationDoseMl: Double = 0.0,
    val calibrationDurationMs: Long = 0L,
    val verificationDurationMs: Long = 0L,
    val pendingDoseMsPerMl: Long = 0L,
    val loaded: Boolean = false,
    @StringRes val errorMessageRes: Int? = null
) {
    val busy: Boolean
        get() = operation !in setOf(
            DosingCalibrationOperation.IDLE,
            DosingCalibrationOperation.PRIMING,
            DosingCalibrationOperation.ERROR
        )

    val primeActive: Boolean
        get() = operation == DosingCalibrationOperation.PRIMING ||
            operation == DosingCalibrationOperation.STARTING_PRIME
}

sealed interface DosingCalibrationAction {
    data class NameChanged(val value: String) : DosingCalibrationAction
    data object ContinueName : DosingCalibrationAction
    data object PrimePressed : DosingCalibrationAction
    data object PrimeReleased : DosingCalibrationAction
    data object ContinuePrime : DosingCalibrationAction
    data object StartCalibrationDose : DosingCalibrationAction
    data class MeasuredVolumeChanged(val value: String) : DosingCalibrationAction
    data object SubmitMeasuredVolume : DosingCalibrationAction
    data class VerificationVolumeChanged(val value: String) : DosingCalibrationAction
    data object StartVerificationDose : DosingCalibrationAction
    data object ConfirmCalibration : DosingCalibrationAction
    data object Recalibrate : DosingCalibrationAction
    data object Exit : DosingCalibrationAction
}

sealed interface DosingCalibrationEvent {
    data object Exit : DosingCalibrationEvent
    data object Completed : DosingCalibrationEvent
}
