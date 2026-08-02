package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import org.json.JSONObject

/** A command-specific Android mirror of one authenticated firmware operation. */
interface DeviceRuntimeCommand<T> {
    val module: String
    val action: String

    /** Returns a new canonical request object on every invocation. */
    fun encodeData(): JSONObject

    /** Parses the exact successful firmware response for this command. */
    fun parseSuccess(response: AqlWsIncomingMessage.Response): T
}

@JvmInline
value class DeviceRuntimeConnectionGeneration(val value: Long) {
    init {
        require(value > 0L) { "Runtime connection generation must be positive." }
    }
}

data class DeviceRuntimeCorrelationKey(
    val deviceUid: DeviceUid,
    val generation: DeviceRuntimeConnectionGeneration,
    val messageId: String,
    val module: String,
    val action: String
)

/** Internal bridge from the per-device runtime repository to the common executor. */
internal data class DeviceRuntimeCommandSession(
    val deviceUid: DeviceUid,
    val generation: DeviceRuntimeConnectionGeneration,
    val authenticated: Boolean,
    val send: (AqlWsOutgoingMessage) -> Boolean
)
