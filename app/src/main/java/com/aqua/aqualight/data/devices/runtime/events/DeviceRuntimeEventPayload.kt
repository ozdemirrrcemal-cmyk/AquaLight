package com.aqua.aqualight.data.devices.runtime.events

import org.json.JSONObject

/** Validated payload shapes accepted by the authenticated event router. */
sealed interface DeviceRuntimeEventPayload {
    data class CommandResult(
        val commandId: String,
        val commandModule: String,
        val commandAction: String,
        val sessionId: String,
        val publishedAtMillis: Long,
        val result: JSONObject
    ) : DeviceRuntimeEventPayload

    data class Snapshot(
        val data: JSONObject
    ) : DeviceRuntimeEventPayload
}
