package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.application.devices.DeviceFirmwareFailure
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureKind
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureSource
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureStage
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.util.Locale

/** Converts every OTA failure source into one lossless application failure contract. */
@Suppress("LongParameterList")
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
        val resolvedKind = kind ?: FailureSignals(
            code = code,
            field = field,
            message = technicalMessage,
            stage = stage
        ).classify()
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
        is DeviceRuntimeCommandOutcome.NotConnected -> runtimeFailure(
            stage = stage,
            technicalMessage = "Device runtime is not connected.",
            code = "runtime_not_connected",
            kind = DeviceFirmwareFailureKind.CONNECTION
        )
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> runtimeFailure(
            stage = stage,
            technicalMessage = "Device runtime is not authenticated.",
            code = "runtime_not_authenticated",
            kind = DeviceFirmwareFailureKind.AUTHENTICATION
        )
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> runtimeFailure(
            stage = stage,
            technicalMessage = "Device does not support this OTA command.",
            code = "unsupported_by_device",
            kind = DeviceFirmwareFailureKind.UNSUPPORTED,
            recoverable = false
        )
        is DeviceRuntimeCommandOutcome.SendFailed -> requestFailure(
            stage = stage,
            technicalMessage = "OTA command could not be sent.",
            code = "send_failed",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.CONNECTION
        )
        is DeviceRuntimeCommandOutcome.Timeout -> requestFailure(
            stage = stage,
            technicalMessage = "OTA command timed out after ${outcome.timeoutMillis} ms.",
            code = "timeout",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.TIMEOUT
        )
        is DeviceRuntimeCommandOutcome.FirmwareError -> firmwareCommandFailure(outcome, stage)
        is DeviceRuntimeCommandOutcome.ProtocolError -> requestFailure(
            stage = stage,
            technicalMessage = outcome.reason.ifBlank { "Invalid OTA response." },
            code = "protocol_error",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.PROTOCOL,
            recoverable = false
        )
        is DeviceRuntimeCommandOutcome.Cancelled -> requestFailure(
            stage = stage,
            technicalMessage = outcome.reason.ifBlank { "OTA command was cancelled." },
            code = "cancelled",
            requestId = outcome.messageId,
            kind = DeviceFirmwareFailureKind.CANCELLED
        )
    }

    fun fromSnapshot(
        snapshot: DeviceFirmwareOtaSnapshot,
        requestId: String
    ): DeviceFirmwareFailure = local(
        technicalMessage = snapshot.lastError.ifBlank { "Firmware OTA failed." },
        source = DeviceFirmwareFailureSource.FIRMWARE_STATUS,
        stage = snapshot.failureStage(),
        code = "firmware_ota_failed",
        field = snapshot.lastErrorField,
        httpStatus = snapshot.httpStatus,
        requestId = requestId,
        firmwarePhase = snapshot.phaseRaw
    )

    private fun runtimeFailure(
        stage: DeviceFirmwareFailureStage,
        technicalMessage: String,
        code: String,
        kind: DeviceFirmwareFailureKind,
        recoverable: Boolean? = null
    ): DeviceFirmwareFailure = local(
        technicalMessage = technicalMessage,
        source = DeviceFirmwareFailureSource.RUNTIME,
        stage = stage,
        code = code,
        recoverable = recoverable,
        kind = kind
    )

    private fun requestFailure(
        stage: DeviceFirmwareFailureStage,
        technicalMessage: String,
        code: String,
        requestId: String,
        kind: DeviceFirmwareFailureKind,
        recoverable: Boolean? = null
    ): DeviceFirmwareFailure = local(
        technicalMessage = technicalMessage,
        source = DeviceFirmwareFailureSource.RUNTIME,
        stage = stage,
        code = code,
        requestId = requestId,
        recoverable = recoverable,
        kind = kind
    )

    private fun firmwareCommandFailure(
        outcome: DeviceRuntimeCommandOutcome.FirmwareError,
        stage: DeviceFirmwareFailureStage
    ): DeviceFirmwareFailure = local(
        technicalMessage = outcome.message.ifBlank { "Firmware rejected OTA." },
        source = DeviceFirmwareFailureSource.FIRMWARE_COMMAND,
        stage = stage,
        code = outcome.code,
        field = outcome.field,
        statusCode = outcome.statusCode,
        requestId = outcome.messageId
    )

    private class FailureSignals(
        code: String,
        field: String,
        message: String,
        private val stage: DeviceFirmwareFailureStage
    ) {
        private val normalizedCode = code.lowercase(Locale.ROOT)
        private val normalizedField = field.lowercase(Locale.ROOT)
        private val normalizedMessage = message.lowercase(Locale.ROOT)

        private val unsupported: Boolean
            get() = "module_not_available" in normalizedCode ||
                "unsupported" in normalizedMessage
        private val compatibilityFailure: Boolean
            get() = normalizedField in IDENTITY_FIELDS ||
                normalizedField == "version" ||
                normalizedMessage.containsAny(IDENTITY_TERMS)
        private val integrityFailure: Boolean
            get() = normalizedField == "sha256" ||
                normalizedMessage.containsAny(INTEGRITY_TERMS)
        private val authenticationFailure: Boolean
            get() = normalizedField in AUTH_FIELDS ||
                normalizedMessage.containsAny(AUTH_TERMS)
        private val connectionFailure: Boolean
            get() = normalizedField in CONNECTION_FIELDS ||
                normalizedMessage.containsAny(CONNECTION_TERMS)
        private val downloadFailure: Boolean
            get() = normalizedField in DOWNLOAD_FIELDS ||
                normalizedMessage.containsAny(DOWNLOAD_TERMS)
        private val storageFailure: Boolean
            get() = normalizedField in STORAGE_FIELDS ||
                normalizedMessage.containsAny(STORAGE_TERMS)
        private val timeout: Boolean
            get() = "timeout" in normalizedCode || "timed out" in normalizedMessage
        private val cancelled: Boolean
            get() = "cancel" in normalizedCode || "cancel" in normalizedMessage
        private val protocolFailure: Boolean
            get() = "protocol" in normalizedCode ||
                normalizedMessage.containsAny(PROTOCOL_TERMS)
        private val invalidRequest: Boolean
            get() = normalizedField in REQUEST_FIELDS ||
                stage == DeviceFirmwareFailureStage.PREPARATION

        fun classify(): DeviceFirmwareFailureKind = when {
            unsupported -> DeviceFirmwareFailureKind.UNSUPPORTED
            compatibilityFailure -> DeviceFirmwareFailureKind.COMPATIBILITY
            integrityFailure -> DeviceFirmwareFailureKind.INTEGRITY
            authenticationFailure -> DeviceFirmwareFailureKind.AUTHENTICATION
            connectionFailure -> DeviceFirmwareFailureKind.CONNECTION
            downloadFailure -> DeviceFirmwareFailureKind.DOWNLOAD
            storageFailure -> DeviceFirmwareFailureKind.STORAGE
            timeout -> DeviceFirmwareFailureKind.TIMEOUT
            cancelled -> DeviceFirmwareFailureKind.CANCELLED
            protocolFailure -> DeviceFirmwareFailureKind.PROTOCOL
            invalidRequest -> DeviceFirmwareFailureKind.INVALID_REQUEST
            else -> DeviceFirmwareFailureKind.INTERNAL
        }
    }

    private fun String.containsAny(terms: Set<String>): Boolean = terms.any(::contains)

    private fun DeviceFirmwareOtaSnapshot.failureStage(): DeviceFirmwareFailureStage =
        if (lastErrorField.lowercase(Locale.ROOT) in VERIFICATION_FIELDS) {
            DeviceFirmwareFailureStage.VERIFICATION
        } else {
            DeviceFirmwareFailureStage.TRANSFER
        }

    private val DeviceFirmwareFailureKind.defaultRecoverable: Boolean
        get() = this in RECOVERABLE_KINDS

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
    private val INTEGRITY_TERMS = setOf("sha256", "checksum", "integrity")
    private val AUTH_FIELDS = setOf("auth", "authentication", "session")
    private val AUTH_TERMS = setOf("not authenticated", "authentication")
    private val CONNECTION_FIELDS = setOf("wifi", "network", "connection")
    private val CONNECTION_TERMS = setOf("not connected", "connection", "wi-fi")
    private val DOWNLOAD_FIELDS = setOf("url", "download", "stream", "http", "tls")
    private val DOWNLOAD_TERMS = setOf("download", "http", "tls", "url")
    private val STORAGE_FIELDS = setOf("size", "storage", "partition", "update", "write", "task")
    private val STORAGE_TERMS = setOf("slot", "partition", "write", "storage")
    private val PROTOCOL_TERMS = setOf("response", "echo", "keys differ")
    private val REQUEST_FIELDS = setOf("data", "request", "applynow", "state")
    private val VERIFICATION_FIELDS = setOf("sha256", "verify")
    private val RECOVERABLE_KINDS = setOf(
        DeviceFirmwareFailureKind.CONNECTION,
        DeviceFirmwareFailureKind.AUTHENTICATION,
        DeviceFirmwareFailureKind.DOWNLOAD,
        DeviceFirmwareFailureKind.TIMEOUT,
        DeviceFirmwareFailureKind.CANCELLED
    )
}
