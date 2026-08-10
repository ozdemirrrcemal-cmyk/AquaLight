package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Temporary, in-memory recorder for the standalone Dosing diagnostic APK.
 *
 * It deliberately keeps only protocol stages and sizes. Device identifiers, payloads, tokens,
 * signatures, nonces and message IDs never enter the published diagnostic snapshot.
 */
@Suppress("TooManyFunctions")
internal object DeviceRuntimeDiagnosticRecorder {
    private const val DOSING_MODULE = "dosing"
    private const val DOSING_STATUS_ACTION = "status.get"
    private const val MAX_DETAIL_LENGTH = 240

    private val lock = Any()
    private val states = MutableStateFlow<Map<DeviceUid, DeviceRuntimeDiagnosticState>>(emptyMap())

    fun observe(deviceUid: DeviceUid): Flow<DeviceRuntimeDiagnosticState> = states
        .map { snapshots -> snapshots[deviceUid] ?: DeviceRuntimeDiagnosticState() }
        .distinctUntilChanged()

    fun recordConnection(
        deviceUid: DeviceUid,
        connectionState: String,
        authenticated: Boolean
    ) = update(deviceUid) { current ->
        current.copy(
            connectionState = connectionState,
            authenticated = authenticated
        )
    }

    fun recordPreparation(
        deviceUid: DeviceUid,
        module: String,
        action: String,
        authenticated: Boolean,
        generation: DeviceRuntimeConnectionGeneration?
    ) {
        if (!tracks(module, action)) return
        update(deviceUid) { current ->
            current.copy(
                stage = if (authenticated) "REQUEST_PREPARING" else "REQUEST_REJECTED",
                outcome = "WAITING",
                attempt = current.attempt + 1,
                authenticated = authenticated,
                generation = generation?.value,
                responseDataBytes = null,
                responseStatusCode = null,
                elapsedMillis = null,
                detail = null,
                socketCloseCode = null,
                socketCloseReason = null,
                rejectedWireFrameBytes = null,
                transportProtocolError = null,
                startedAtNanos = System.nanoTime()
            )
        }
    }

    fun recordRequestReady(
        deviceUid: DeviceUid,
        module: String,
        action: String
    ) {
        if (!tracks(module, action)) return
        update(deviceUid) { current -> current.copy(stage = "REQUEST_READY") }
    }

    fun recordSend(
        deviceUid: DeviceUid,
        module: String,
        action: String,
        sent: Boolean
    ) {
        if (!tracks(module, action)) return
        update(deviceUid) { current ->
            current.copy(stage = if (sent) "REQUEST_SENT" else "SEND_FAILED")
        }
    }

    fun recordReply(
        deviceUid: DeviceUid,
        module: String,
        action: String,
        message: AqlWsIncomingMessage
    ) {
        if (!tracks(module, action)) return
        val statusCode = when (message) {
            is AqlWsIncomingMessage.Response -> message.statusCode
            is AqlWsIncomingMessage.Error -> message.statusCode
            is AqlWsIncomingMessage.Event -> null
        }
        val rawDataBytes = message.data.toString()
            .toByteArray(StandardCharsets.UTF_8)
            .size
        update(deviceUid) { current ->
            current.copy(
                stage = "REPLY_RECEIVED",
                responseDataBytes = rawDataBytes,
                responseStatusCode = statusCode,
                elapsedMillis = current.elapsedNow()
            )
        }
    }

    fun recordParserFailure(
        deviceUid: DeviceUid,
        module: String,
        action: String,
        failure: Throwable
    ) {
        if (!tracks(module, action)) return
        val failureDetail = listOfNotNull(
            failure::class.java.simpleName.takeIf(String::isNotBlank),
            failure.message?.trim()?.takeIf(String::isNotBlank)
        ).joinToString(": ").take(MAX_DETAIL_LENGTH)
        update(deviceUid) { current ->
            current.copy(
                stage = "PARSER_FAILED",
                detail = failureDetail.ifBlank { "Parser rejected the response." },
                elapsedMillis = current.elapsedNow()
            )
        }
    }

    fun recordOutcome(outcome: DeviceRuntimeCommandOutcome<*>) {
        if (!tracks(outcome.module, outcome.action)) return
        update(outcome.deviceUid) { current ->
            current.copy(
                stage = "COMPLETED",
                outcome = outcome.diagnosticName(),
                elapsedMillis = current.elapsedNow(),
                detail = if (current.stage == "PARSER_FAILED") {
                    current.detail
                } else {
                    outcome.diagnosticDetail() ?: current.detail
                }
            )
        }
    }

    fun recordSocketClosed(deviceUid: DeviceUid, code: Int, reason: String) =
        update(deviceUid) { current ->
            current.copy(
                connectionState = "CLOSED",
                authenticated = false,
                stage = "SOCKET_CLOSED_AFTER_${current.stage}",
                socketCloseCode = code,
                socketCloseReason = reason.trim().take(MAX_DETAIL_LENGTH),
                elapsedMillis = current.elapsedNow()
            )
        }

    fun recordSocketFailure(
        deviceUid: DeviceUid,
        message: String,
        cause: Throwable?,
        frameBytes: Int?,
        protocolError: String?
    ) =
        update(deviceUid) { current ->
            val detail = listOfNotNull(
                message.trim().takeIf(String::isNotBlank),
                cause?.javaClass?.simpleName?.takeIf(String::isNotBlank)
            ).joinToString(": ").take(MAX_DETAIL_LENGTH)
            current.copy(
                connectionState = "FAILED",
                authenticated = false,
                stage = "SOCKET_FAILED_AFTER_${current.stage}",
                socketCloseReason = detail,
                rejectedWireFrameBytes = frameBytes,
                transportProtocolError = protocolError,
                elapsedMillis = current.elapsedNow()
            )
        }

    internal fun resetForTests() {
        synchronized(lock) { states.value = emptyMap() }
    }

    private fun tracks(module: String, action: String): Boolean =
        module == DOSING_MODULE && action == DOSING_STATUS_ACTION

    private fun update(
        deviceUid: DeviceUid,
        transform: (DeviceRuntimeDiagnosticState) -> DeviceRuntimeDiagnosticState
    ) {
        synchronized(lock) {
            val current = states.value[deviceUid] ?: DeviceRuntimeDiagnosticState()
            states.value = states.value + (deviceUid to transform(current))
        }
    }
}

@Suppress("LongParameterList")
internal data class DeviceRuntimeDiagnosticState(
    val connectionState: String = "NOT_OBSERVED",
    val authenticated: Boolean = false,
    val stage: String = "WAITING_FOR_DOSING_STATUS",
    val outcome: String = "WAITING",
    val attempt: Int = 0,
    val generation: Long? = null,
    val responseDataBytes: Int? = null,
    val responseStatusCode: Int? = null,
    val elapsedMillis: Long? = null,
    val detail: String? = null,
    val socketCloseCode: Int? = null,
    val socketCloseReason: String? = null,
    val rejectedWireFrameBytes: Int? = null,
    val transportProtocolError: String? = null,
    internal val startedAtNanos: Long? = null
) {
    fun elapsedNow(): Long? = startedAtNanos?.let { started ->
        ((System.nanoTime() - started) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticName(): String = when (this) {
    is DeviceRuntimeCommandOutcome.Success<*> -> "SUCCESS"
    is DeviceRuntimeCommandOutcome.NotConnected -> "NOT_CONNECTED"
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> "NOT_AUTHENTICATED"
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> "UNSUPPORTED_BY_DEVICE"
    is DeviceRuntimeCommandOutcome.SendFailed -> "SEND_FAILED"
    is DeviceRuntimeCommandOutcome.Timeout -> "TIMEOUT"
    is DeviceRuntimeCommandOutcome.FirmwareError -> "FIRMWARE_ERROR"
    is DeviceRuntimeCommandOutcome.ProtocolError -> "PROTOCOL_ERROR"
    is DeviceRuntimeCommandOutcome.Cancelled -> "CANCELLED"
}

private fun DeviceRuntimeCommandOutcome<*>.diagnosticDetail(): String? = when (this) {
    is DeviceRuntimeCommandOutcome.Timeout -> "timeoutMillis=$timeoutMillis"
    is DeviceRuntimeCommandOutcome.FirmwareError -> listOf(
        "code=$code",
        "field=$field",
        "status=$statusCode",
        message
    ).filter(String::isNotBlank).joinToString("; ")
    is DeviceRuntimeCommandOutcome.ProtocolError -> reason
    is DeviceRuntimeCommandOutcome.Cancelled -> reason
    is DeviceRuntimeCommandOutcome.Success<*>,
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.SendFailed -> null
}
