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
            return DeviceOtaHttpFailureClassifier.map(error.toDiagnostics())
        }
        val disposition = INVALID_VALUE_FIELD_DISPOSITIONS[error.field] ?: PROTOCOL_MISMATCH
        return disposition.toFailure(error.toDiagnostics())
    }
}

private object DeviceOtaSnapshotFailureClassifier {

    fun map(snapshot: DeviceFirmwareOtaSnapshot): DeviceOtaFailure {
        val diagnostics = snapshot.toDiagnostics()
        return when (snapshot.lastErrorField) {
            DeviceFirmwareRuntimeContract.ErrorField.HTTP_STATUS ->
                DeviceOtaHttpFailureClassifier.map(diagnostics)
            DeviceFirmwareRuntimeContract.ErrorField.URL ->
                DOWNLOAD_URL_OPEN_FAILED.toFailure(diagnostics)
            DeviceFirmwareRuntimeContract.ErrorField.STREAM ->
                DOWNLOAD_STREAM_INTERRUPTED.toFailure(diagnostics)
            DeviceFirmwareRuntimeContract.ErrorField.SIZE -> sizeFailure(diagnostics)
            else -> {
                val disposition = SNAPSHOT_FIELD_DISPOSITIONS[snapshot.lastErrorField]
                    ?: DEVICE_INTERNAL
                disposition.toFailure(diagnostics)
            }
        }
    }

    private fun sizeFailure(
        diagnostics: DeviceOtaFailureDiagnostics
    ): DeviceOtaFailure {
        val message = diagnostics.message
        val disposition = when {
            message.contains("larger than", ignoreCase = true) -> INSUFFICIENT_SPACE
            message.contains("downloaded byte count", ignoreCase = true) ->
                DOWNLOAD_SIZE_MISMATCH_RETRYABLE
            message.contains("does not match", ignoreCase = true) ->
                DOWNLOAD_SIZE_MISMATCH_TERMINAL
            else -> INSUFFICIENT_SPACE
        }
        return disposition.toFailure(diagnostics)
    }
}

private object DeviceOtaHttpFailureClassifier {

    fun map(diagnostics: DeviceOtaFailureDiagnostics): DeviceOtaFailure {
        val disposition = when (diagnostics.httpStatus) {
            HTTPC_ERROR_CONNECTION_REFUSED,
            HTTPC_ERROR_NOT_CONNECTED -> DOWNLOAD_CONNECTION_FAILED
            HTTPC_ERROR_SEND_HEADER_FAILED,
            HTTPC_ERROR_SEND_PAYLOAD_FAILED -> DOWNLOAD_SEND_FAILED
            HTTPC_ERROR_CONNECTION_LOST -> DOWNLOAD_CONNECTION_LOST
            HTTPC_ERROR_NO_STREAM -> DOWNLOAD_STREAM_UNAVAILABLE
            HTTPC_ERROR_NO_HTTP_SERVER -> DOWNLOAD_SERVER_NO_RESPONSE
            HTTPC_ERROR_TOO_LESS_RAM -> DOWNLOAD_DEVICE_MEMORY_LOW
            HTTPC_ERROR_ENCODING -> DOWNLOAD_ENCODING_UNSUPPORTED
            HTTPC_ERROR_STREAM_WRITE -> DOWNLOAD_STREAM_WRITE_FAILED
            HTTPC_ERROR_READ_TIMEOUT,
            HTTP_REQUEST_TIMEOUT -> DOWNLOAD_TIMEOUT
            HTTP_UNAUTHORIZED,
            HTTP_FORBIDDEN -> RELEASE_ACCESS_DENIED
            HTTP_NOT_FOUND -> RELEASE_UNAVAILABLE
            HTTP_TOO_MANY_REQUESTS -> RELEASE_RATE_LIMITED
            in HTTP_REDIRECT_START..HTTP_REDIRECT_END -> RELEASE_REDIRECT_FAILED
            in HTTP_CLIENT_ERROR_START..HTTP_CLIENT_ERROR_END -> RELEASE_REQUEST_REJECTED
            in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> RELEASE_SERVER_UNAVAILABLE
            else -> DOWNLOAD_FAILED
        }
        return disposition.toFailure(diagnostics)
    }

    private const val HTTPC_ERROR_CONNECTION_REFUSED = -1
    private const val HTTPC_ERROR_SEND_HEADER_FAILED = -2
    private const val HTTPC_ERROR_SEND_PAYLOAD_FAILED = -3
    private const val HTTPC_ERROR_NOT_CONNECTED = -4
    private const val HTTPC_ERROR_CONNECTION_LOST = -5
    private const val HTTPC_ERROR_NO_STREAM = -6
    private const val HTTPC_ERROR_NO_HTTP_SERVER = -7
    private const val HTTPC_ERROR_TOO_LESS_RAM = -8
    private const val HTTPC_ERROR_ENCODING = -9
    private const val HTTPC_ERROR_STREAM_WRITE = -10
    private const val HTTPC_ERROR_READ_TIMEOUT = -11

    private const val HTTP_REDIRECT_START = 300
    private const val HTTP_REDIRECT_END = 399
    private const val HTTP_CLIENT_ERROR_START = 400
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_CLIENT_ERROR_END = 499
    private const val HTTP_SERVER_ERROR_START = 500
    private const val HTTP_SERVER_ERROR_END = 599
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
private val DOWNLOAD_CONNECTION_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_CONNECTION_FAILED,
    recoverable = true
)
private val DOWNLOAD_SEND_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_SEND_FAILED,
    recoverable = true
)
private val DOWNLOAD_CONNECTION_LOST = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_CONNECTION_LOST,
    recoverable = true
)
private val DOWNLOAD_STREAM_UNAVAILABLE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_STREAM_UNAVAILABLE,
    recoverable = true
)
private val DOWNLOAD_SERVER_NO_RESPONSE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_SERVER_NO_RESPONSE,
    recoverable = true
)
private val DOWNLOAD_DEVICE_MEMORY_LOW = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_DEVICE_MEMORY_LOW,
    recoverable = false
)
private val DOWNLOAD_ENCODING_UNSUPPORTED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_ENCODING_UNSUPPORTED,
    recoverable = false
)
private val DOWNLOAD_STREAM_WRITE_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_STREAM_WRITE_FAILED,
    recoverable = false
)
private val DOWNLOAD_TIMEOUT = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_TIMEOUT,
    recoverable = true
)
private val DOWNLOAD_URL_OPEN_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_URL_OPEN_FAILED,
    recoverable = true
)
private val DOWNLOAD_STREAM_INTERRUPTED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_STREAM_INTERRUPTED,
    recoverable = true
)
private val DOWNLOAD_SIZE_MISMATCH_RETRYABLE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH,
    recoverable = true
)
private val DOWNLOAD_SIZE_MISMATCH_TERMINAL = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_SIZE_MISMATCH,
    recoverable = false
)
private val DOWNLOAD_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.DOWNLOAD_FAILED,
    recoverable = true
)
private val RELEASE_UNAVAILABLE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_UNAVAILABLE,
    recoverable = false
)
private val RELEASE_ACCESS_DENIED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_ACCESS_DENIED,
    recoverable = false
)
private val RELEASE_RATE_LIMITED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_RATE_LIMITED,
    recoverable = true
)
private val RELEASE_REDIRECT_FAILED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_REDIRECT_FAILED,
    recoverable = false
)
private val RELEASE_REQUEST_REJECTED = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_REQUEST_REJECTED,
    recoverable = false
)
private val RELEASE_SERVER_UNAVAILABLE = DeviceOtaFailureDisposition(
    DeviceOtaFailureReason.RELEASE_SERVER_UNAVAILABLE,
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
    DeviceFirmwareRuntimeContract.ErrorField.STREAM to DOWNLOAD_STREAM_INTERRUPTED,
    DeviceFirmwareRuntimeContract.ErrorField.FLASH to FLASH_WRITE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.TASK to DEVICE_INTERNAL_RETRYABLE
)

private val SNAPSHOT_FIELD_DISPOSITIONS = mapOf(
    DeviceFirmwareRuntimeContract.ErrorField.SAFE_MODE to SAFE_MODE_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.TLS to SECURITY_VALIDATION_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.EXPECTED_SIZE to INSUFFICIENT_SPACE,
    DeviceFirmwareRuntimeContract.ErrorField.SHA256 to INTEGRITY_CHECK_FAILED,
    DeviceFirmwareRuntimeContract.ErrorField.FLASH to FLASH_WRITE_FAILED
)
