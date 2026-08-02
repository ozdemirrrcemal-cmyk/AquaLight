package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareFailure
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureKind
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureSource
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureStage
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.Locale

/** Converts every OTA failure source into one lossless application failure contract. */
@Suppress("LongParameterList", "ComplexCondition")
internal object DeviceFirmwareFailureMapper {

    fun fromThrowable(
        error: Throwable,
        source: DeviceFirmwareFailureSource,
        stage: DeviceFirmwareFailureStage,
        code: String,
        field: String = "",
        recoverable: Boolean? = null
    ): DeviceFirmwareFailure = local(
        technicalMessage = error.message?.takeIf(String::isNotBlank)
            ?: error::class.java.simpleName,
        source = source,
        stage = stage,
        code = code,
        field = field,
        recoverable = recoverable
    )

    fun local(
        technicalMessage: String,
        source: DeviceFirmwareFailureSource,
        stage: DeviceFirmwareFailureStage,
        code: String,
        field: String = "",
        statusCode: Int = 0,
        httpStatus: Int = 0,
        requestId: String = "",
        firmwarePhase: String = "",
        recoverable: Boolean? = null,
        kind: DeviceFirmwareFailureKind? = null
    ): DeviceFirmwareFailure {
        val resolvedKind = kind ?: classify(
            code = code,
            field = field,
            message = technicalMessage,
            stage = stage
        )
        return DeviceFirmwareFailure(
            kind = resolvedKind,
            source = source,
            stage = stage,
            technicalMessage = technicalMessage.ifBlank { "OTA operation failed." },
            code = code,
            field = field,
            statusCode = statusCode,
            httpStatus = httpStatus,
            requestId = requestId,
            firmwarePhase = firmwarePhase,
            recoverable = recoverable ?: resolvedKind.defaultRecoverable
        )
    }

    fun fromOutcome(
        outcome: DeviceRuntimeCommandOutcome<*>,
        stage: DeviceFirmwareFailureStage
    ): DeviceFirmwareFailure = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success<*> -> error(
            "Successful OTA outcome cannot be converted to a failure."
        )
        is DeviceRuntimeCommandOutcome.NotConnected -> local(
            technicalMessage = "Device runtime is not connected.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "runtime_not_connected",
            kind = DeviceFirmwareFailureKind.CONNECTION
        )
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> local(
            technicalMessage = "Device runtime is not authenticated.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "runtime_not_authenticated",
            kind = DeviceFirmwareFailureKind.AUTHENTICATION
        )
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> local(
            technicalMessage = "Device does not support this OTA command.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "unsupported_by_device",
            kind = DeviceFirmwareFailureKind.UNSUPPORTED,
            recoverable = false
        )
        is DeviceRuntimeCommandOutcome.SendFailed -> local(
            technicalMessage = "OTA command could not be sent.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "send_failed",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.CONNECTION
        )
        is DeviceRuntimeCommandOutcome.Timeout -> local(
            technicalMessage = "OTA command timed out after ${outcome.timeoutMillis} ms.",
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "timeout",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.TIMEOUT
        )
        is DeviceRuntimeCommandOutcome.FirmwareError -> local(
            technicalMessage = outcome.message.ifBlank { "Firmware rejected OTA." },
            source = DeviceFirmwareFailureSource.FIRMWARE_COMMAND,
            stage = stage,
            code = outcome.code,
            field = outcome.field,
            statusCode = outcome.statusCode,
            requestId = outcome.messageId
        )
        is DeviceRuntimeCommandOutcome.ProtocolError -> local(
            technicalMessage = outcome.reason.ifBlank { "Invalid OTA response." },
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "protocol_error",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.PROTOCOL,
            recoverable = false
        )
        is DeviceRuntimeCommandOutcome.Cancelled -> local(
            technicalMessage = outcome.reason.ifBlank { "OTA command was cancelled." },
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = stage,
            code = "cancelled",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.CANCELLED
        )
    }

    fun fromSnapshot(snapshot: DeviceFirmwareOtaSnapshot): DeviceFirmwareFailure = local(
        technicalMessage = snapshot.lastError.ifBlank { "Firmware OTA failed." },
        source = DeviceFirmwareFailureSource.FIRMWARE_STATUS,
        stage = snapshot.failureStage(),
        code = "firmware_ota_failed",
        field = snapshot.lastErrorField,
        httpStatus = snapshot.httpStatus,
        firmwarePhase = snapshot.phaseRaw
    )

    private fun classify(
        code: String,
        field: String,
        message: String,
        stage: DeviceFirmwareFailureStage
    ): DeviceFirmwareFailureKind {
        val normalizedCode = code.lowercase(Locale.ROOT)
        val normalizedField = field.lowercase(Locale.ROOT)
        val normalizedMessage = message.lowercase(Locale.ROOT)
        return when {
            "module_not_available" in normalizedCode ||
                "unsupported" in normalizedMessage -> DeviceFirmwareFailureKind.UNSUPPORTED
            normalizedField in IDENTITY_FIELDS ||
                normalizedField == "version" ||
                IDENTITY_TERMS.any(normalizedMessage::contains) ->
                DeviceFirmwareFailureKind.COMPATIBILITY
            normalizedField == "sha256" ||
                "sha256" in normalizedMessage ||
                "checksum" in normalizedMessage ||
                "integrity" in normalizedMessage -> DeviceFirmwareFailureKind.INTEGRITY
            normalizedField in AUTH_FIELDS ||
                "not authenticated" in normalizedMessage ||
                "authentication" in normalizedMessage ->
                DeviceFirmwareFailureKind.AUTHENTICATION
            normalizedField in CONNECTION_FIELDS ||
                "not connected" in normalizedMessage ||
                "connection" in normalizedMessage ||
                "wi-fi" in normalizedMessage -> DeviceFirmwareFailureKind.CONNECTION
            normalizedField in DOWNLOAD_FIELDS ||
                "download" in normalizedMessage ||
                "http" in normalizedMessage ||
                "tls" in normalizedMessage ||
                "url" in normalizedMessage -> DeviceFirmwareFailureKind.DOWNLOAD
            normalizedField in STORAGE_FIELDS ||
                "slot" in normalizedMessage ||
                "partition" in normalizedMessage ||
                "write" in normalizedMessage ||
                "storage" in normalizedMessage -> DeviceFirmwareFailureKind.STORAGE
            "timeout" in normalizedCode || "timed out" in normalizedMessage ->
                DeviceFirmwareFailureKind.TIMEOUT
            "cancel" in normalizedCode || "cancel" in normalizedMessage ->
                DeviceFirmwareFailureKind.CANCELLED
            "protocol" in normalizedCode ||
                "response" in normalizedMessage ||
                "echo" in normalizedMessage ||
                "keys differ" in normalizedMessage -> DeviceFirmwareFailureKind.PROTOCOL
            normalizedField in REQUEST_FIELDS ||
                stage == DeviceFirmwareFailureStage.PREPARATION ->
                DeviceFirmwareFailureKind.INVALID_REQUEST
            else -> DeviceFirmwareFailureKind.INTERNAL
        }
    }

    private fun DeviceFirmwareOtaSnapshot.failureStage(): DeviceFirmwareFailureStage {
        val normalizedField = lastErrorField.lowercase(Locale.ROOT)
        return when {
            normalizedField == "sha256" || normalizedField == "verify" ->
                DeviceFirmwareFailureStage.VERIFICATION
            normalizedField in STORAGE_FIELDS || normalizedField in DOWNLOAD_FIELDS ->
                DeviceFirmwareFailureStage.TRANSFER
            else -> DeviceFirmwareFailureStage.TRANSFER
        }
    }

    private val DeviceFirmwareFailureKind.defaultRecoverable: Boolean
        get() = this == DeviceFirmwareFailureKind.CONNECTION ||
            this == DeviceFirmwareFailureKind.AUTHENTICATION ||
            this == DeviceFirmwareFailureKind.DOWNLOAD ||
            this == DeviceFirmwareFailureKind.TIMEOUT ||
            this == DeviceFirmwareFailureKind.CANCELLED

    private val IDENTITY_FIELDS = setOf(
        "productkey",
        "productid",
        "model",
        "hardwarerevision"
    )
    private val IDENTITY_TERMS = setOf(
        "productkey",
        "productid",
        "hardware revision",
        "hardwarerevision",
        "device model",
        "firmware version"
    )
    private val AUTH_FIELDS = setOf("auth", "authentication", "session")
    private val CONNECTION_FIELDS = setOf("wifi", "network", "connection")
    private val DOWNLOAD_FIELDS = setOf("url", "download", "stream", "http", "tls")
    private val STORAGE_FIELDS = setOf("size", "storage", "partition", "update", "write", "task")
    private val REQUEST_FIELDS = setOf("data", "request", "applynow", "state")
}
