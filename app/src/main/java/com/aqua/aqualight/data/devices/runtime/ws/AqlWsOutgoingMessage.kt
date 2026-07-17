package com.aqua.aqualight.data.devices.runtime.ws

import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/** Typed application intent. Only [AqlWsWireCodec] may serialize it. */
sealed interface AqlWsOutgoingMessage {
    val id: String

    data class Command(
        val module: String,
        val action: String,
        val data: JSONObject = JSONObject(),
        override val id: String = nextId()
    ) : AqlWsOutgoingMessage

    companion object {
        private val commandCounter = AtomicLong(0L)

        fun nextId(prefix: String = "android"): String =
            "$prefix-${System.currentTimeMillis()}-${commandCounter.incrementAndGet()}"
    }
}
