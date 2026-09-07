package com.aqua.aqualight.data.devices.timer.v1

import com.aqua.aqualight.application.devices.timer.DeviceTimerCommandFailure
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.timer.DeviceTimerRuntimeContract

/**
 * Maps the pinned Timer V1 firmware rejection contract into stable application semantics.
 *
 * Raw firmware prose remains inside the data layer. Known codes must carry their exact HTTP
 * status; status drift is treated as a protocol failure instead of being shown to the customer.
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
            DeviceTimerRuntimeContract.Error.STORAGE_ERROR -> mapStorage(error)
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
    ): DeviceTimerCommandFailure = if (
        error.field == FirmwareField.CHANNEL_KEY &&
        error.message.startsWith(CHANNEL_NOT_FOUND_MESSAGE_PREFIX)
    ) {
        DeviceTimerCommandFailure.CHANNEL_UNAVAILABLE
    } else {
        DeviceTimerCommandFailure.UNKNOWN_REJECTION
    }

    private fun mapConflict(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = if (
        error.field == FirmwareField.EXPECTED_REVISION &&
        error.message == REVISION_STALE_MESSAGE
    ) {
        DeviceTimerCommandFailure.CONFLICT
    } else {
        DeviceTimerCommandFailure.UNKNOWN_REJECTION
    }

    private fun mapHardware(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = when {
        (error.field to error.message) in RESOURCE_FAILURE_IDENTITIES ->
            DeviceTimerCommandFailure.RESOURCE_UNAVAILABLE

        error.message in RUNTIME_LOCKED_MESSAGES -> DeviceTimerCommandFailure.RUNTIME_LOCKED
        error.field == FirmwareField.CHANNEL_KEY && error.message in OUTPUT_FAILURE_MESSAGES ->
            DeviceTimerCommandFailure.HARDWARE_FAILURE

        error.field == FirmwareField.CHANNEL_KEY &&
            error.message.startsWith(CHANNEL_OUTPUT_FAILURE_MESSAGE_PREFIX) ->
            DeviceTimerCommandFailure.HARDWARE_FAILURE

        else -> DeviceTimerCommandFailure.HARDWARE_FAILURE
    }

    private fun mapStorage(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceTimerCommandFailure = if (error.message in RUNTIME_LOCKED_MESSAGES) {
        DeviceTimerCommandFailure.RUNTIME_LOCKED
    } else {
        DeviceTimerCommandFailure.STORAGE_FAILURE
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
        const val TIMER = "timer"
    }

    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_CONFLICT = 409
    private const val HTTP_UNPROCESSABLE_ENTITY = 422
    private const val HTTP_INTERNAL_ERROR = 500
    private const val HTTP_SERVICE_UNAVAILABLE = 503

    private const val CHANNEL_NOT_FOUND_MESSAGE_PREFIX =
        "configured timer channel not found for channelKey: "
    private const val CHANNEL_OUTPUT_FAILURE_MESSAGE_PREFIX =
        "timer channel output write failed: "
    private const val REVISION_STALE_MESSAGE = "timer config revision is stale"

    private val RESOURCE_FAILURE_IDENTITIES = setOf(
        FirmwareField.TIMER to "timer transaction snapshot allocation failed",
        FirmwareField.SCHEDULES to "timer schedule transaction workspace allocation failed"
    )
    private val OUTPUT_FAILURE_MESSAGES = setOf(
        "timer output write failed; channel config was rolled back"
    )
    private val RUNTIME_LOCKED_MESSAGES = setOf(
        "timer runtime transaction is locked until restart",
        "timer output write and rollback failed; timer runtime remains locked until restart",
        "timer persistence and runtime rollback failed; timer is locked until restart"
    )
}
