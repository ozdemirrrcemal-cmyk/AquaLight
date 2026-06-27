package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONException
import org.json.JSONObject

class AqlWsMessageParser {

    fun parse(raw: String): Result<AqlWsIncomingMessage> {
        return runCatching {
            val json = JSONObject(raw)
            val type = json.optString("type").trim()
            val id = json.optString("id").trim()

            when (type) {
                AqlWsContract.TYPE_HELLO -> AqlWsIncomingMessage.Hello(
                    raw = raw,
                    id = id,
                    type = type,
                    json = json
                )

                AqlWsContract.TYPE_RESPONSE -> AqlWsIncomingMessage.Response(
                    raw = raw,
                    id = id,
                    type = type,
                    json = json,
                    ok = json.optBoolean("ok", false),
                    module = json.optString("module").trim(),
                    action = json.optString("action").trim(),
                    statusCode = json.optInt("statusCode", 0)
                )

                AqlWsContract.TYPE_EVENT -> AqlWsIncomingMessage.Event(
                    raw = raw,
                    id = id,
                    type = type,
                    json = json,
                    module = json.optString("module").trim(),
                    event = json.optString("event").trim()
                )

                AqlWsContract.TYPE_ERROR -> AqlWsIncomingMessage.Error(
                    raw = raw,
                    id = id,
                    type = type,
                    json = json,
                    message = json.optString("message").trim(),
                    statusCode = json.optInt("statusCode", 0)
                )

                else -> AqlWsIncomingMessage.Generic(
                    raw = raw,
                    id = id,
                    type = type,
                    json = json
                )
            }
        }.recoverCatching { error ->
            throw JSONException("Invalid AquaLight WebSocket message: ${error.message}")
        }
    }
}
