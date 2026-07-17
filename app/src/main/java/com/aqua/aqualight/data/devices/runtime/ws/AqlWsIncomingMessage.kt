package com.aqua.aqualight.data.devices.runtime.ws

import org.json.JSONObject

/** Decoded, authenticated runtime messages. Wire security fields never escape this layer. */
sealed interface AqlWsIncomingMessage {
    val id: String
    val type: String
    val module: String
    val action: String
    val data: JSONObject

    data class Response(
        override val id: String,
        override val type: String,
        override val module: String,
        override val action: String,
        override val data: JSONObject,
        val ok: Boolean,
        val statusCode: Int
    ) : AqlWsIncomingMessage

    data class Event(
        override val id: String,
        override val type: String,
        override val module: String,
        override val action: String,
        override val data: JSONObject
    ) : AqlWsIncomingMessage

    data class Error(
        override val id: String,
        override val type: String,
        override val module: String,
        override val action: String,
        override val data: JSONObject,
        val message: String,
        val statusCode: Int,
        val code: String = "",
        val field: String = ""
    ) : AqlWsIncomingMessage
}
