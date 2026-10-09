package com.aqua.aqualight.data.devices.runtime.core

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsOutgoingMessage
import org.json.JSONObject

/** A command-specific Android mirror of one authenticated firmware operation. */
interface DeviceRuntimeCommand<T> {
    val module: String
    val action: String

    /** Optional connection captured when the caller validated this command's authority. */
    val expectedGeneration: DeviceRuntimeConnectionGeneration?
        get() = null

    /** Returns a new canonical request object on every invocation. */
    fun encodeData(): JSONObject

    /** Parses the exact successful firmware response for this command. */
    fun parseSuccess(response: AqlWsIncomingMessage.Response): T
}

/** Keeps a checked intent attached to its connection without changing the gateway contract. */
internal fun <T> DeviceRuntimeCommand<T>.boundToGeneration(
    generation: DeviceRuntimeConnectionGeneration?
): DeviceRuntimeCommand<T> = if (generation == null) {
    this
} else {
    object : DeviceRuntimeCommand<T> by this {
        override val expectedGeneration: DeviceRuntimeConnectionGeneration = generation
    }
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

/** The same lock must protect reconnect and this final generation check plus transport send. */
internal fun DeviceRuntimeCommandSession.withGenerationBoundSend(
    sessionLock: Any,
    currentGeneration: () -> DeviceRuntimeConnectionGeneration?
): DeviceRuntimeCommandSession = copy(
    send = { message ->
        synchronized(sessionLock) {
            currentGeneration() == generation && send(message)
        }
    }
)
