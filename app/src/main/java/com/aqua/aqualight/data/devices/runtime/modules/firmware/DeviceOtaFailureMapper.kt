package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.io.IOException

internal object DeviceOtaFailureMapper {

    fun availability(error: Throwable): DeviceOtaFailure = if (error.hasIoCause()) {
        simpleFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            message = error.message.orEmpty()
        )
    } else {
        simpleFailure(
            reason = DeviceOtaFailureReason.CHECK_FAILED,
            recoverable = true,
            message = error.message.orEmpty()
        )
    }

    fun command(outcome: DeviceRuntimeCommandOutcome<*>): DeviceOtaFailure = when (outcome) {
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Cancelled -> simpleFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            message = outcome.diagnosticMessage()
        )
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> simpleFailure(
            reason = DeviceOtaFailureReason.AUTHENTICATION,
            recoverable = true,
            message = "Device runtime is not authenticated."
        )
        is DeviceRuntimeCommandOutcome.Timeout -> simpleFailure(
            reason = DeviceOtaFailureReason.CONNECTION,
            recoverable = true,
            message = "OTA command timed out after ${outcome.timeoutMillis} ms."
        )
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> simpleFailure(
            reason = DeviceOtaFailureReason.UNSUPPORTED,
            recoverable = false,
            message = "Device does not support this OTA command."
        )
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            DeviceOtaFirmwareFailureClassifier.map(outcome)
        is DeviceRuntimeCommandOutcome.ProtocolError -> simpleFailure(
            reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
            recoverable = false,
            message = outcome.reason
        )
        is DeviceRuntimeCommandOutcome.Success<*> -> error(
            "A successful OTA command cannot be mapped to a failure."
        )
    }

    fun snapshot(snapshot: DeviceFirmwareOtaSnapshot): DeviceOtaFailure =
        DeviceOtaSnapshotFailureClassifier.map(snapshot)

    fun checkFailure(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.CHECK_FAILED,
        recoverable = true,
        message = message
    )

    fun connection(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.CONNECTION,
        recoverable = true,
        message = message
    )

    fun busy(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.DEVICE_BUSY,
        recoverable = true,
        message = message
    )

    fun incompatible(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE,
        recoverable = false,
        message = message
    )

    fun protocol(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.PROTOCOL_MISMATCH,
        recoverable = false,
        message = message
    )

    fun internal(message: String): DeviceOtaFailure = simpleFailure(
        reason = DeviceOtaFailureReason.DEVICE_INTERNAL,
        recoverable = false,
        message = message
    )
}

private object DeviceOtaFirmwareFailureClassifier {

    fun map(error: DeviceRuntimeCommandOutcome.FirmwareError): DeviceOtaFailure {
        if (error.code == DeviceFirmwareRuntimeContract.ErrorCode.INVALID_VALUE) {
            return invalidValue(error)
        }
        val disposition = FIRMWARE_CODE_DISPOSITIONS[error.code] ?: DEVICE_INTERNAL
        return disposition.toFailure(error.toDiagnostics())
    }

    private fun invalidValue(
        error: DeviceRuntimeCommandOutcome.FirmwareError
    ): DeviceOtaFailure {
        if (error.field == DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS) {
            return DeviceOtaHttpFailureClassifier.map(
                diagnostics = error.toDiagnostics()
            )
        }
        val disposition = INVALID_VALUE_FIELD_DISPOSITIONS[error.field] ?: PROTOCOL_MISMATCH
        return disposition.toFailure(error.toDiagnostics())
    }
}

private object DeviceOtaSnapshotFailureClassifier {

    fun map(snapshot: DeviceFirmwareOtaSnapshot): DeviceOtaFailure {
        val diagnostics = snapshot.toDiagnostics()
        if (snapshot.lastErrorField == DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS) {
            return DeviceOtaHttpFailureClassifier.map(diagnostics)
        }
        val disposition = SNAPSHOT_FIELD_DISPOSITIONS[snapshot.lastErrorField] ?: DEVICE_INTERNAL
        return disposition.toFailure(diagnostics)
    }
}

private object DeviceOtaHttpFailureClassifier {

    fun map(diagnostics: DeviceOtaFailureDiagnostics): DeviceOtaFailure {
        val status = diagnostics.httpStatus
        val releaseMissing = status == HTTP_NOT_FOUND
        val retryable = status == 0 ||
            status == HTTP_REQUEST_TIMEOUT ||
            status == HTTP_TOO_MANY_REQUESTS ||
            status >= HTTP_SERVER_ERROR_START
        val disposition = DeviceOtaFailureDisposition(
            reason = if (releaseMissing) {
                DeviceOtaFailureReason.RELEASE_UNAVAILABLE
            } else {
                DeviceOtaFailureReason.DOWNLOAD_FAILED
            },
            recoverable = retryable
        )
        return disposition.toFailure(diagnostics)
    }

    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR_START = 500
}

private data class DeviceOtaFailureDisposition(
    val reason: DeviceOtaFailureReason,
    val recoverable: Boolean
)

private data class DeviceOtaFailureDiagnostics(
    val code: String = "",
    val field: String = "",
    val httpStatus: Int = 0,
    val message: String = ""
)

private fun DeviceOtaFailureDisposition.toFailure(
    diagnostics: DeviceOtaFailureDiagnostics
): DeviceOtaFailure = DeviceOtaFailure(
    reason = reason,
    recoverable = recoverable,
    code = diagnostics.code,
    field = diagnostics.field,
    httpStatus = diagnostics.httpStatus,
    diagnosticMessage = diagnostics.message
)

private fun simpleFailure(
    reason: DeviceOtaFailureReason,
    recoverable: Boolean,
    message: String
): DeviceOtaFailure = DeviceOtaFailure(
    reason = reason,
    recoverable = recoverable,
    diagnosticMessage = message
)

private fun DeviceRuntimeCommandOutcome.FirmwareError.toDiagnostics() =
    DeviceOtaFailureDiagnostics(
        code = code,
        field = field,
        httpStatus = statusCode,
        message = message
    )

private fun DeviceFirmwareOtaSnapshot.toDiagnostics() = DeviceOtaFailureDiagnostics(
    field = lastErrorField,
    httpStatus = httpStatus,
    message = lastError
)

private fun Throwable.hasIoCause(): Boolean =
    generateSequence(this) { current -> current.cause }.any { cause -> cause is IOException }

private fun DeviceRuntimeCommandOutcome<*>.diagnosticMessage(): String = when (this) {
    is DeviceRuntimeCommandOutcome.SendFailed -> "OTA command could not be sent."
    is DeviceRuntimeCommandOutcome.Cancelled -> reason
    else -> "Device runtime is not connected."
}

private val AUTHENTICATION = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.AUTHENTICATION,
    recoverable = true
)
private val UNSUPPORTED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.UNSUPPORTED,
    recoverable = false
)
private val DEVICE_BUSY = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DEVICE_BUSY,
    recoverable = true
)
private val DEVICE_INTERNAL = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DEVICE_INTERNAL,
    recoverable = false
)
private val DEVICE_INTERNAL_RETRYABLE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DEVICE_INTERNAL,
    recoverable = true
)
private val PROTOCOL_MISMATCH = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.PROTOCOL_MISMATCH,
    recoverable = false
)
private val CONNECTION = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.CONNECTION,
    recoverable = true
)
private val INCOMPATIBLE_FIRMWARE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.INCOMPATIBLE_FIRMWARE,
    recoverable = false
)
private val INTEGRITY_CHECK_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
    recoverable = false
)
private val INSUFFICIENT_SPACE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.INSUFFICIENT_SPACE,
    recoverable = false
)
private val SAFE_MODE_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.SAFE_MODE_FAILED,
    recoverable = true
)
private val SECURITY_VALIDATION_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.SECURITY_VALIDATION_FAILED,
    recoverable = false
)
private val DOWNLOAD_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_FAILED,
    recoverable = true
)
private val FLASH_WRITE_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.FLASH_WRITE_FAILED,
    recoverable = false
)

private val FIRMWARE_CODE_DISPOSITIONS = mapOf(
    DeviceFirmwareRuntimeContract.ErrorCode.UNAUTHORIZED to AUTHENTICATION,
    DeviceFirmwareRuntimeContract.ErrorCode.MODULE_NOT_AVAILABLE to UNSUPPORTED,
    DeviceFirmwareRuntimeContract.ErrorCode.FEATURE_NOT_AVAILABLE to UNSUPPORTED,
    DeviceFirmwareRuntimeContract.ErrorCode.NOT_FOUND to UNSUPPORTED,
    DeviceFirmwareRuntimeContract.ErrorCode.DEVICE_BUSY to DEVICE_BUSY,
    DeviceFirmwareRuntimeContract.ErrorCode.STORAGE_ERROR to FLASH_WRITE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorCode.HARDWARE_ERROR to DEVICE_INTERNAL,
    DeviceFirmwareRuntimeContract.ErrorCode.INTERNAL_ERROR to DEVICE_INTERNAL,
    DeviceFirmwareRuntimeContract.ErrorCode.BAD_REQUEST to PROTOCOL_MISMATCH,
    DeviceFirmwareRuntimeContract.ErrorCode.MISSING_FIELD to PROTOCOL_MISMATCH
)

private val INVALID_VALUE_FIELD_DISPOSITIONS = mapOf(
    DeviceFirmwareRuntimeContract.ErrorField.WIFI to CONNECTION,
    DeviceFirmwareRuntimeContract.ErrorField.STATE to DEVICE_BUSY,
    DeviceFirmwareRuntimeContract.ErrorField.URL to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.VERSION to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.PRODUCT_KEY to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.PRODUCT_ID to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.MODEL to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.HARDWARE_REVISION to INCOMPATIBLE_FIRMWARE,
    DeviceFirmwareRuntimeContract.ErrorField.SHA256 to INTEGRITY_CHECK_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE to INSUFFICIENT_SPACE,
    DeviceFirmwareRuntimeContract.ErrorField.SIZE to INSUFFICIENT_SPACE,
    DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE to SAFE_MODE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.TLS to SECURITY_VALIDATION_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.STREAM to DOWNLOAD_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.FLASH to FLASH_WRITE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.TASK to DEVICE_INTERNAL_RETRYABLE
)

private val SNAPSHOT_FIELD_DISPOSITIONS = mapOf(
    DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE to SAFE_MODE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.TLS to SECURITY_VALIDATION_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.URL to DOWNLOAD_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.STREAM to DOWNLOAD_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.SIZE to INSUFFICIENT_SPACE,
    DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE to INSUFFICIENT_SPACE,
    DeviceFirmwareRuntimeContract.ErrorField.SHA256 to INTEGRITY_CHECK_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.FLASH to FLASH_WRITE_FAILED
)
