package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage

internal object DeviceRuntimeFaultFactory {

    fun fromPayload(
        message: AqlWsIncomingMessage,
        error: Throwable,
        nowMillis: Long
    ): DeviceRuntimeFault = DeviceRuntimeFault(
        code = "invalid_runtime_payload",
        message = error.message.orEmpty().ifBlank {
            "Firmware payload validation failed."
        },
        module = message.module,
        action = message.action,
        messageId = message.id,
        occurredAtMillis = nowMillis
    )

    fun fromError(
        message: AqlWsIncomingMessage.Error,
        nowMillis: Long
    ): DeviceRuntimeFault = DeviceRuntimeFault(
        code = message.code.ifBlank { "firmware_error_${message.statusCode}" },
        message = message.message,
        field = message.field,
        module = message.module,
        action = message.action,
        messageId = message.id,
        occurredAtMillis = nowMillis
    )

    fun fromRejectedResponse(
        message: AqlWsIncomingMessage.Response,
        nowMillis: Long
    ): DeviceRuntimeFault = DeviceRuntimeFault(
        code = "firmware_response_not_ok",
        message = "Firmware returned a non-success response.",
        module = message.module,
        action = message.action,
        messageId = message.id,
        occurredAtMillis = nowMillis
    )
}
