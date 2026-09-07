package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/** Maps the pinned firmware Dosing rejection contract into channel-level application semantics. */
internal object DeviceDosingChannelFailureMapper {

    fun map(outcome: DeviceRuntimeCommandOutcome<*>): DeviceDosingChannelOperationResult =
        when (outcome) {
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                DeviceDosingChannelOperationResult.Unavailable

            is DeviceRuntimeCommandOutcome.FirmwareError ->
                DeviceDosingChannelOperationResult.Rejected(mapFirmware(outcome))

            is DeviceRuntimeCommandOutcome.NotConnected,
            is DeviceRuntimeCommandOutcome.NotAuthenticated,
            is DeviceRuntimeCommandOutcome.SendFailed,
            is DeviceRuntimeCommandOutcome.Timeout,
            is DeviceRuntimeCommandOutcome.Cancelled,
            is DeviceRuntimeCommandOutcome.ProtocolError ->
                DeviceDosingChannelOperationResult.Failed

            is DeviceRuntimeCommandOutcome.Success<*> -> error(
                "A successful Dosing command cannot be mapped to a channel failure."
            )
        }

    private fun mapFirmware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceDosingChannelRejection = when (error.code) {
        FirmwareCode.DEVICE_BUSY -> DeviceDosingChannelRejection.BUSY
        FirmwareCode.STORAGE_ERROR -> DeviceDosingChannelRejection.UNSAFE
        FirmwareCode.HARDWARE_ERROR -> if (
            DeviceDosingV1Contract.OutputStopFailure.matches(error.field, error.message)
        ) {
            DeviceDosingChannelRejection.OUTPUT_STOP_UNCONFIRMED
        } else {
            DeviceDosingChannelRejection.UNSAFE
        }
        FirmwareCode.INVALID_VALUE -> mapInvalidValue(error)
        FirmwareCode.NOT_FOUND -> if (
            error.field == FirmwareField.CHANNEL_KEY &&
            error.message == CHANNEL_NOT_FOUND_MESSAGE
        ) {
            DeviceDosingChannelRejection.CONFLICT
        } else {
            DeviceDosingChannelRejection.UNKNOWN
        }
        else -> DeviceDosingChannelRejection.UNKNOWN
    }

    private fun mapInvalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceDosingChannelRejection = when {
        error.field == FirmwareField.EXPECTED_REVISION &&
            error.message == STALE_REVISION_MESSAGE ->
            DeviceDosingChannelRejection.CONFLICT

        error.field == FirmwareField.CALIBRATION &&
            error.message in CALIBRATION_REQUIRED_MESSAGES ->
            DeviceDosingChannelRejection.NOT_CALIBRATED

        (error.field to error.message) in BUSY_IDENTITIES ->
            DeviceDosingChannelRejection.BUSY

        error.field == FirmwareField.RESERVOIR ->
            DeviceDosingChannelRejection.UNSAFE

        error.field == FirmwareField.AMOUNT_ML ||
            error.field == FirmwareField.PROGRAM ||
            error.field.startsWith(PROGRAM_FIELD_PREFIX) ->
            DeviceDosingChannelRejection.INVALID_DRAFT

        else -> DeviceDosingChannelRejection.UNKNOWN
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
        const val RESERVOIR = "reservoir"
        const val AMOUNT_ML = "amountMl"
        const val PROGRAM = "program"
    }

    private val CALIBRATION_REQUIRED_MESSAGES = setOf(
        "pump must be calibrated before manual dosing",
        "confirmed calibration is required for enabled automatic dosing"
    )

    private val BUSY_IDENTITIES = setOf(
        FirmwareField.DOSING to
            "dosing channel is busy with an active run or calibration session",
        FirmwareField.PUMP to
            "dosing channel already has an unfinished physical run",
        FirmwareField.PUMP to
            "this run must be stopped by its matching dosing command",
        FirmwareField.CALIBRATION to
            "normal manual dosing is unavailable while calibration is open"
    )

    private const val PROGRAM_FIELD_PREFIX = "program."
    private const val STALE_REVISION_MESSAGE = "stale dosing channel revision"
    private const val CHANNEL_NOT_FOUND_MESSAGE =
        "configured dosing channel was not found"
}
