package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

internal object DeviceRuntimeCommandOutcomeMapper {

    fun fromIncoming(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage
    ): DeviceRuntimeCommandOutcome = when (message) {
        is AqlWsIncomingMessage.Response -> fromResponse(deviceUid, message)
        is AqlWsIncomingMessage.Error -> DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = deviceUid,
            module = message.module,
            action = message.action,
            messageId = message.id,
            statusCode = message.statusCode,
            code = message.code,
            message = message.message,
            field = message.field
        )
        is AqlWsIncomingMessage.Event -> error("Events cannot complete command requests.")
    }

    fun correlationMismatch(
        pending: DeviceRuntimePendingRequest,
        messageId: String
    ): DeviceRuntimeCommandOutcome.FirmwareError = DeviceRuntimeCommandOutcome.FirmwareError(
        deviceUid = pending.request.deviceUid,
        module = pending.request.module,
        action = pending.request.action,
        messageId = messageId,
        statusCode = CORRELATION_MISMATCH_STATUS_CODE,
        code = CORRELATION_MISMATCH_CODE,
        message = "Firmware response correlation did not match the request.",
        field = "id"
    )

    private fun fromResponse(
        deviceUid: DeviceUid,
        message: AqlWsIncomingMessage.Response
    ): DeviceRuntimeCommandOutcome = if (message.ok) {
        DeviceRuntimeCommandOutcome.Success(
            deviceUid = deviceUid,
            module = message.module,
            action = message.action,
            messageId = message.id,
            statusCode = message.statusCode,
            data = JSONObject(message.data.toString())
        )
    } else {
        DeviceRuntimeCommandOutcome.FirmwareError(
            deviceUid = deviceUid,
            module = message.module,
            action = message.action,
            messageId = message.id,
            statusCode = message.statusCode,
            code = "response_not_ok",
            message = "Firmware returned a non-success response.",
            field = ""
        )
    }

    private const val CORRELATION_MISMATCH_STATUS_CODE = 500
    const val CORRELATION_MISMATCH_CODE = "correlation_mismatch"
}
