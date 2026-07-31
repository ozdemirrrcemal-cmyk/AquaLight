package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.runtime.ws.AqlWsIncomingMessage
import org.json.JSONObject

internal object DeviceRuntimeWireRecorder {

    fun record(
        previous: DeviceRuntimeState,
        message: AqlWsIncomingMessage,
        nowMillis: Long
    ): DeviceRuntimeState {
        val key = "${message.type}:${message.module}:${message.action}"
        val next = LinkedHashMap(previous.lastPayloads)
        next[key] = DeviceRuntimeWireRecord(
            type = message.type,
            module = message.module,
            action = message.action,
            messageId = message.id,
            dataJson = JSONObject(message.data.toString()).toString(),
            receivedAtMillis = nowMillis
        )
        trimOldest(next)
        return previous.copy(
            lastPayloads = next,
            lastMessageAtMillis = nowMillis
        )
    }

    private fun trimOldest(records: LinkedHashMap<String, DeviceRuntimeWireRecord>) {
        while (records.size > MAX_WIRE_RECORDS) {
            val oldest = records.keys.firstOrNull() ?: break
            records.remove(oldest)
        }
    }

    private const val MAX_WIRE_RECORDS = 64
}
