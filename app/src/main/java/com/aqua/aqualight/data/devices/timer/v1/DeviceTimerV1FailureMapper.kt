package com.aqua.aqualight.data.devices.timer.v1

import com.aqua.aqualight.application.devices.timer.DeviceTimerCommandFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeContract

/**
 * Maps the pinned Timer V1 firmware rejection contract into stable application semantics.
 *
 * Firmware main intentionally masks rejection prose as `Command rejected.`. Classification must
 * therefore use only the stable status/code/field identity. Known codes must carry their exact
 * HTTP status; status drift is treated as a protocol failure instead of being shown to customers.
 */
internal object DeviceTimerV1FailureMapper {

    fun map(error: DeviceRuntimeCommandOutcome.FirmwareError): DeviceTimerCommandFailure {
        if (!error.hasExpectedStatus()) return DeviceTimerCommandFailure.PROTOCOL_ERROR
        return when (error.code) {
            DeviceTimerRuntimeContract.Error.BAD_REQUEST,
            DeviceTimerRuntimeContract.Error.MISSING_FIELD ->
                DeviceTimerCommandFailure.INVALID_REQUEST

            DeviceTimerRuntimeContract.Error.INVALID_VALUE -> mapInvalidValue(error)
            DeviceTimerRuntimeContract.Error.NOT_FOUND -> mapNotFound(error)
            DeviceTimerRuntimeContract.Error.CONFLICT -> mapConflict(error)
            DeviceTimerRuntimeContract.Error.HARDWARE_ERROR -> mapHardware(error)
            DeviceTimerRuntimeContract.Error.STORAGE_ERROR ->
                DeviceTimerCommandFailure.STORAGE_FAILURE
            else -> DeviceTimerCommandFailure.UNKNOWN_REJECTION
        }
    }

    private fun DeviceRuntimeCommandOutcome.FirmwareError.hasExpectedStatus(): Boolean {
        val expectedStatus = when (code) {
            DeviceTimerRuntimeContract.Error.BAD_REQUEST -> HTTP_BAD_REQUEST
            DeviceTimerRuntimeContract.Error.MISSING_FIELD,
            DeviceTimerRuntimeContract.Error.INVALID_VALUE -> HTTP_UNPROCESSABLE_ENTITY
            DeviceTimerRuntimeContract.Error.NOT_FOUND -> HTTP_NOT_FOUND
            DeviceTimerRuntimeContract.Error.CONFLICT -> HTTP_CONFLICT
            DeviceTimerRuntimeContract.Error.HARDWARE_ERROR -> HTTP_SERVICE_UNAVAILABLE
            DeviceTimerRuntimeContract.Error.STORAGE_ERROR -> HTTP_INTERNAL_ERROR
            else -> return true
        }
        return statusCode == expectedStatus
    }

    private fun mapInvalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = when (error.field) {
        FirmwareField.DATA,
        FirmwareField.EXPECTED_REVISION -> DeviceTimerCommandFailure.INVALID_REQUEST

        FirmwareField.CHANNEL_KEY,
        FirmwareField.DISPLAY_NAME,
        FirmwareField.SCHEDULES,
        FirmwareField.SAVE,
        FirmwareField.REGIME,
        FirmwareField.DURATION_MS,
        FirmwareField.TIMER_CONFIG -> DeviceTimerCommandFailure.INVALID_CONFIGURATION

        else -> DeviceTimerCommandFailure.INVALID_CONFIGURATION
    }

    private fun mapNotFound(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = if (error.field == FirmwareField.CHANNEL_KEY) {
        DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE
    } else {
        DeviceTimerCommandFailure.UNKNOWN_REJECTION
    }

    private fun mapConflict(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = if (error.field == FirmwareField.EXPECTED_REVISION) {
        DeviceTimerCommandFailure.CONFLICT
    } else {
        DeviceTimerCommandFailure.UNKNOWN_REJECTION
    }

    private fun mapHardware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = if (error.field == FirmwareField.SCHEDULES) {
        DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE
    } else {
        // `timer` and `channelKey` each cover multiple internal firmware causes whose prose is
        // deliberately hidden on the wire. Do not invent a more specific lock/output diagnosis.
        DeviceTimerCommandFailure.HARDWARE_FAILURE
    }

    private object FirmwareField {
        const val DATA = "data"
        const val EXPECTED_REVISION = "expectedRevision"
        const val CHANNEL_KEY = "channelKey"
        const val DISPLAY_NAME = "displayName"
        const val SCHEDULES = "schedules"
        const val SAVE = "save"
        const val REGIME = "regime"
        const val DURATION_MS = "durationMs"
        const val TIMER_CONFIG = "timerConfig"
    }

    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_CONFLICT = 409
    private const val HTTP_UNPROCESSABLE_ENTITY = 422
    private const val HTTP_INTERNAL_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503

}
