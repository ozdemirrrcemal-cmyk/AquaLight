package com.aqua.aqualight.data.devices.runtime.ws

import org.json.JSONObject

sealed interface AqlWsIncomingMessage {
    val raw: String
    val id: String
    val type: String
    val json: JSONObject

    data class Hello(
        override val raw: String,
        override val id: String,
        override val type: String,
        override val json: JSONObject
    ) : AqlWsIncomingMessage

    data class Response(
        override val raw: String,
        override val id: String,
        override val type: String,
        override val json: JSONObject,
        val ok: Boolean,
        val module: String,
        val action: String,
        val statusCode: Int
    ) : AqlWsIncomingMessage

    data class Event(
        override val raw: String,
        override val id: String,
        override val type: String,
        override val json: JSONObject,
        val module: String,
        val event: String
    ) : AqlWsIncomingMessage

    data class Error(
        override val raw: String,
        override val id: String,
        override val type: String,
        override val json: JSONObject,
        val message: String,
        val statusCode: Int,
        val code: String = "",
        val field: String = ""
    ) : AqlWsIncomingMessage

    data class Generic(
        override val raw: String,
        override val id: String,
        override val type: String,
        override val json: JSONObject
    ) : AqlWsIncomingMessage
}
