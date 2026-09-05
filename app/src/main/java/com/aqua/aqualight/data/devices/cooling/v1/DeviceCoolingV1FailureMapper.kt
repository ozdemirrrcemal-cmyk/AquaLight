package com.aqua.aqualight.data.devices.cooling.v1

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Maps the pinned Cool Pro 1F firmware rejection contract into stable application semantics.
 *
 * Matching intentionally uses the complete firmware identity where a code is not specific enough.
 * Presentation never receives raw firmware prose.
 */
internal object DeviceCoolingV1FailureMapper {

    fun map(error: DeviceRuntimeCommandOutcome.FirmwareError): DeviceCoolingCommandFailure =
        when (error.code) {
            FirmwareCode.CONFLICT -> DeviceCoolingCommandFailure.CONFLICT
            FirmwareCode.BAD_REQUEST,
            FirmwareCode.MISSING_FIELD -> DeviceCoolingCommandFailure.INVALID_REQUEST

            FirmwareCode.INVALID_VALUE -> mapInvalidValue(error)
            FirmwareCode.NOT_FOUND -> mapNotFound(error)
            FirmwareCode.HARDWARE_ERROR -> mapHardware(error)
            FirmwareCode.STORAGE_ERROR -> DeviceCoolingCommandFailure.STORAGE_FAILURE
            FirmwareCode.CLOCK_UNSYNCED -> DeviceCoolingCommandFailure.CLOCK_UNSYNCED
            else -> DeviceCoolingCommandFailure.UNKNOWN_REJECTION
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

    private object FirmwareCode {
        const val BAD_REQUEST = "BAD_REQUEST"
        const val MISSING_FIELD = "MISSING_FIELD"
        const val INVALID_VALUE = "INVALID_VALUE"
        const val NOT_FOUND = "NOT_FOUND"
        const val CONFLICT = "CONFLICT"
        const val HARDWARE_ERROR = "HARDWARE_ERROR"
        const val STORAGE_ERROR = "STORAGE_ERROR"
        const val CLOCK_UNSYNCED = "CLOCK_UNSYNCED"
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
    }

    private val CONFIGURATION_FIELDS = setOf(
        FirmwareField.CONTROL_MODE,
        FirmwareField.COOLING_CONFIG,
        FirmwareField.EXPECTED_CONFIG_REVISION,
        FirmwareField.EXPECTED_PROGRAM_REVISION
    )

    private const val MANUAL_MODE_REQUIRED_MESSAGE =
        "manual output requires MANUAL control mode"
    private const val HARDWARE_OWNED_FIELDS_MESSAGE =
        "cooling.config.apply contains unsupported or hardware-owned fields"
    private const val HISTORY_RANGE_MESSAGE = "range must be 24h, 7d, or 30d"
    private const val FAN_NOT_FOUND_MESSAGE = "only the catalog fan1 output exists"
    private const val FAN_UNAVAILABLE_MESSAGE = "catalog fan1 output is unavailable"
}
