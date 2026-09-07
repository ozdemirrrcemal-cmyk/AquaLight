package com.aqua.aqualight.data.devices.cooling.v1

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract

/**
 * Maps the pinned Cool Pro 1F firmware rejection contract into stable application semantics.
 *
 * Matching intentionally validates the complete firmware identity: HTTP status, code and the
 * relevant field/message pair. Presentation never receives raw firmware prose.
 */
internal object DeviceCoolingV1FailureMapper {

    fun map(error: DeviceRuntimeCommandOutcome.FirmwareError): DeviceCoolingCommandFailure {
        if (!error.hasExpectedStatus()) return DeviceCoolingCommandFailure.PROTOCOL_ERROR
        return when (error.code) {
            DeviceCoolingV1Contract.Error.CONFLICT -> DeviceCoolingCommandFailure.CONFLICT
            DeviceCoolingV1Contract.Error.BAD_REQUEST,
            DeviceCoolingV1Contract.Error.MISSING_FIELD -> DeviceCoolingCommandFailure.INVALID_REQUEST

            DeviceCoolingV1Contract.Error.INVALID_VALUE -> mapInvalidValue(error)
            DeviceCoolingV1Contract.Error.NOT_FOUND -> mapNotFound(error)
            DeviceCoolingV1Contract.Error.HARDWARE_ERROR -> mapHardware(error)
            DeviceCoolingV1Contract.Error.STORAGE_ERROR -> mapStorage(error)
            DeviceCoolingV1Contract.Error.CLOCK_UNSYNCED -> DeviceCoolingCommandFailure.CLOCK_UNSYNCED
            else -> DeviceCoolingCommandFailure.UNKNOWN_REJECTION
        }
    }

    private fun DeviceRuntimeCommandOutcome.FirmwareError.hasExpectedStatus(): Boolean {
        val expectedStatus = when (code) {
            DeviceCoolingV1Contract.Error.BAD_REQUEST -> HTTP_BAD_REQUEST
            DeviceCoolingV1Contract.Error.MISSING_FIELD,
            DeviceCoolingV1Contract.Error.INVALID_VALUE -> HTTP_UNPROCESSABLE_ENTITY
            DeviceCoolingV1Contract.Error.NOT_FOUND -> HTTP_NOT_FOUND
            DeviceCoolingV1Contract.Error.CONFLICT -> HTTP_CONFLICT
            DeviceCoolingV1Contract.Error.HARDWARE_ERROR,
            DeviceCoolingV1Contract.Error.CLOCK_UNSYNCED -> HTTP_SERVICE_UNAVAILABLE
            DeviceCoolingV1Contract.Error.STORAGE_ERROR -> HTTP_INTERNAL_ERROR
            else -> return true
        }
        return statusCode == expectedStatus
    }

    private fun mapInvalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceCoolingCommandFailure = when {
        error.field == FirmwareField.CONTROL_MODE &&
            error.message == MANUAL_MODE_REQUIRED_MESSAGE ->
            DeviceCoolingCommandFailure.MANUAL_MODE_REQUIRED

        error.field == FirmwareField.DATA &&
            error.message == HARDWARE_OWNED_FIELDS_MESSAGE ->
            DeviceCoolingCommandFailure.INVALID_REQUEST

        error.field == FirmwareField.RANGE &&
            error.message == HISTORY_RANGE_MESSAGE ->
            DeviceCoolingCommandFailure.INVALID_REQUEST

        error.field in CONFIGURATION_FIELDS ||
            error.field == FirmwareField.SLOTS ||
            error.field == FirmwareField.PROGRAM ||
            error.field == FirmwareField.MANUAL ||
            error.field == FirmwareField.TARGET_PERCENT ->
            DeviceCoolingCommandFailure.INVALID_CONFIGURATION

        else -> DeviceCoolingCommandFailure.INVALID_CONFIGURATION
    }

    private fun mapNotFound(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceCoolingCommandFailure = if (
        error.field == FirmwareField.FAN_KEY &&
        error.message == FAN_NOT_FOUND_MESSAGE
    ) {
        DeviceCoolingCommandFailure.HARDWARE_UNAVAILABLE
    } else {
        DeviceCoolingCommandFailure.UNKNOWN_REJECTION
    }

    private fun mapHardware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceCoolingCommandFailure = if (
        error.field == FirmwareField.FAN1 &&
        error.message == FAN_UNAVAILABLE_MESSAGE
    ) {
        DeviceCoolingCommandFailure.HARDWARE_UNAVAILABLE
    } else {
        DeviceCoolingCommandFailure.HARDWARE_FAILURE
    }

    private fun mapStorage(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceCoolingCommandFailure = if (
        error.field == FirmwareField.HISTORY &&
        error.message == HISTORY_READ_MESSAGE
    ) {
        DeviceCoolingCommandFailure.HISTORY_UNAVAILABLE
    } else {
        DeviceCoolingCommandFailure.STORAGE_FAILURE
    }

    private object FirmwareField {
        const val DATA = "data"
        const val CONTROL_MODE = "controlMode"
        const val COOLING_CONFIG = "coolingConfig"
        const val EXPECTED_CONFIG_REVISION = "expectedConfigRevision"
        const val EXPECTED_PROGRAM_REVISION = "expectedProgramRevision"
        const val TARGET_PERCENT = "targetPercent"
        const val FAN_KEY = "fanKey"
        const val FAN1 = "fan1"
        const val MANUAL = "manual"
        const val SLOTS = "slots"
        const val PROGRAM = "program"
        const val RANGE = "range"
        const val HISTORY = "history"
    }

    private val CONFIGURATION_FIELDS = setOf(
        FirmwareField.CONTROL_MODE,
        FirmwareField.COOLING_CONFIG,
        FirmwareField.EXPECTED_CONFIG_REVISION,
        FirmwareField.EXPECTED_PROGRAM_REVISION
    )

    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_CONFLICT = 409
    private const val HTTP_UNPROCESSABLE_ENTITY = 422
    private const val HTTP_INTERNAL_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503

    private const val MANUAL_MODE_REQUIRED_MESSAGE =
        "manual output requires MANUAL control mode"
    private const val HARDWARE_OWNED_FIELDS_MESSAGE =
        "cooling.config.apply contains unsupported or hardware-owned fields"
    private const val HISTORY_RANGE_MESSAGE = "range must be 24h, 7d, or 30d"
    private const val FAN_NOT_FOUND_MESSAGE = "only the catalog fan1 output exists"
    private const val FAN_UNAVAILABLE_MESSAGE = "catalog fan1 output is unavailable"
    private const val HISTORY_READ_MESSAGE = "cooling history could not be read"
}
