package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/** Maps the pinned firmware rejection contract into application-owned calibration semantics. */
internal object DeviceDosingCalibrationFailureMapper {

    fun map(outcome: DeviceRuntimeCommandOutcome<*>): DeviceDosingCalibrationFailure =
        when (outcome) {
            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated,
            is DeviceRuntimeCommandOutcome.SendFailed,
            is DeviceRuntimeCommandOutcome.Timeout,
            is DeviceRuntimeCommandOutcome.Cancelled ->
                DeviceDosingCalibrationFailure.CONNECTION

            is DeviceRuntimeCommandOutcome.FirmwareError -> mapFirmware(outcome)

            is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
            is DeviceRuntimeCommandOutcome.ProtocolError ->
                DeviceDosingCalibrationFailure.INTERNAL

            is DeviceRuntimeCommandOutcome.Success<*> -> error(
                "A successful Dosing command cannot be mapped to a calibration failure."
            )
        }

    private fun mapFirmware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceDosingCalibrationFailure = when (error.code) {
        FirmwareCode.STORAGE_ERROR -> DeviceDosingCalibrationFailure.STORAGE
        FirmwareCode.DEVICE_BUSY -> DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
        FirmwareCode.HARDWARE_ERROR -> mapHardware(error)
        FirmwareCode.INVALID_VALUE -> mapInvalidValue(error)
        FirmwareCode.NOT_FOUND -> if (
            error.field == FirmwareField.CHANNEL_KEY &&
            error.message == CHANNEL_NOT_FOUND_MESSAGE
        ) {
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
        } else {
            DeviceDosingCalibrationFailure.INTERNAL
        }
        else -> DeviceDosingCalibrationFailure.INTERNAL
    }

    private fun mapHardware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceDosingCalibrationFailure = if (
        error.field == FirmwareField.PUMP && error.message == HARDWARE_START_FAILURE
    ) {
        DeviceDosingCalibrationFailure.HARDWARE
    } else {
        // The pinned firmware also transports NotReady/InternalFailure as HARDWARE_ERROR.
        DeviceDosingCalibrationFailure.INTERNAL
    }

    private fun mapInvalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceDosingCalibrationFailure = when {
        error.field == FirmwareField.DEVICE_TIME && error.message == DEVICE_TIME_REQUIRED_MESSAGE ->
            DeviceDosingCalibrationFailure.DEVICE_TIME_NOT_READY
        error.field == FirmwareField.MEASURED_ML && error.message in MEASUREMENT_MESSAGES ->
            DeviceDosingCalibrationFailure.INVALID_MEASUREMENT
        (error.field to error.message) in BUSY_IDENTITIES ->
            DeviceDosingCalibrationFailure.OPERATION_IN_PROGRESS
        (error.field to error.message) in STATE_MISMATCH_IDENTITIES ->
            DeviceDosingCalibrationFailure.CALIBRATION_STATE_MISMATCH
        else -> DeviceDosingCalibrationFailure.INTERNAL
    }

    private object FirmwareCode {
        const val INVALID_VALUE = "INVALID_VALUE"
        const val NOT_FOUND = "NOT_FOUND"
        const val DEVICE_BUSY = "DEVICE_BUSY"
        const val STORAGE_ERROR = "STORAGE_ERROR"
        const val HARDWARE_ERROR = "HARDWARE_ERROR"
    }

    private object FirmwareField {
        const val CHANNEL_KEY = "channelKey"
        const val EXPECTED_REVISION = "expectedRevision"
        const val DOSING = "dosing"
        const val PUMP = "pump"
        const val CALIBRATION = "calibration"
        const val VERIFICATION = "verification"
        const val USE_PENDING_CALIBRATION = "usePendingCalibration"
        const val DEVICE_TIME = "deviceTime"
        const val MEASURED_ML = "measuredMl"
        const val AMOUNT_ML = "amountMl"
    }

    private val BUSY_IDENTITIES = setOf(
        FirmwareField.CALIBRATION to
            "calibration is already running/pending or the pump is busy",
        FirmwareField.PUMP to
            "prime is unavailable while another dosing/calibration run is active",
        FirmwareField.PUMP to "the dosing pump is already running",
        FirmwareField.PUMP to "dosing channel already has an unfinished physical run",
        FirmwareField.DOSING to
            "dosing channel is busy with an active run or calibration session"
    )

    private val STATE_MISMATCH_IDENTITIES = setOf(
        FirmwareField.CALIBRATION to
            "calibration.start must complete before calibration.finish",
        FirmwareField.CALIBRATION to "calibration run has not finished yet",
        FirmwareField.CALIBRATION to "no pending calibration exists for this channel",
        FirmwareField.USE_PENDING_CALIBRATION to
            "no pending calibration exists for this channel",
        FirmwareField.VERIFICATION to
            "the pending verification dose has already started",
        FirmwareField.VERIFICATION to
            "the 4 ml verification dose must finish before confirmation",
        FirmwareField.CALIBRATION to
            "normal manual dosing is unavailable while calibration is open",
        FirmwareField.AMOUNT_ML to
            "pending calibration verification requires exactly 4 ml",
        FirmwareField.PUMP to
            "this run must be stopped by its matching dosing command",
        FirmwareField.EXPECTED_REVISION to "stale dosing channel revision"
    )

    private val MEASUREMENT_MESSAGES = setOf(
        "measuredMl must be a finite positive number",
        "measuredMl is outside the supported calibration range",
        "measuredMl cannot be represented safely",
        "calculated doseMsPerMl is outside the safe range"
    )

    private const val HARDWARE_START_FAILURE =
        "dosing output hardware could not be energized"
    private const val DEVICE_TIME_REQUIRED_MESSAGE =
        "trusted device time is required before calibration confirmation"
    private const val CHANNEL_NOT_FOUND_MESSAGE =
        "configured dosing channel was not found"
}
