package com.aqua.aqualight.data.devices.runtime.ws

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AqlWsMessageParser {

    fun parse(raw: String): Result<AqlWsIncomingMessage> {
        return runCatching {
            val json = JSONObject(raw)
            val safeJson = json.redactedCopy()
            val safeRaw = safeJson.toString()
            val type = json.optString("type").trim()
            val id = json.optString("id").trim()

            when (type) {
                AqlWsContract.TYPE_HELLO -> AqlWsIncomingMessage.Hello(
                    raw = safeRaw,
                    id = id,
                    type = type,
                    json = safeJson
                )

                AqlWsContract.TYPE_RESPONSE -> AqlWsIncomingMessage.Response(
                    raw = safeRaw,
                    id = id,
                    type = type,
                    json = safeJson,
                    ok = json.optBoolean("ok", false),
                    module = json.optString("module").trim(),
                    action = json.optString("action").trim(),
                    statusCode = json.optInt("statusCode", json.optInt("status", 0))
                )

                AqlWsContract.TYPE_EVENT -> AqlWsIncomingMessage.Event(
                    raw = safeRaw,
                    id = id,
                    type = type,
                    json = safeJson,
                    module = json.optString("module").trim(),
                    event = json.optString("event").trim()
                )

                AqlWsContract.TYPE_ERROR -> {
                    val error = json.optJSONObject("error")
                    AqlWsIncomingMessage.Error(
                        raw = safeRaw,
                        id = id,
                        type = type,
                        json = safeJson,
                        message = error
                            ?.optString("message")
                            ?.trim()
                            .orEmpty()
                            .ifBlank { json.optString("message").trim() },
                        statusCode = json.optInt("statusCode", json.optInt("status", 0)),
                        code = error?.optString("code")?.trim().orEmpty(),
                        field = error?.optString("field")?.trim().orEmpty()
                    )
                }

                else -> AqlWsIncomingMessage.Generic(
                    raw = safeRaw,
                    id = id,
                    type = type,
                    json = safeJson
                )
            }
        }.recoverCatching { error ->
            throw JSONException("Invalid AquaLight WebSocket message: ${error.message}")
        }
    }


    private fun JSONObject.redactedCopy(): JSONObject {
        return JSONObject(toString()).also { copy ->
            copy.redactSensitiveValues()
        }
    }

    private fun JSONObject.redactSensitiveValues() {
        val names = keys().asSequence().toList()
        names.forEach { key ->
            val value = opt(key)
            when {
                key.isSensitiveJsonKey() -> put(key, REDACTED_VALUE)
                value is JSONObject -> value.redactSensitiveValues()
                value is JSONArray -> value.redactSensitiveValues()
            }
        }
    }

    private fun JSONArray.redactSensitiveValues() {
        for (index in 0 until length()) {
            when (val value = opt(index)) {
                is JSONObject -> value.redactSensitiveValues()
                is JSONArray -> value.redactSensitiveValues()
            }
        }
    }

    private fun String.isSensitiveJsonKey(): Boolean {
        return trim().lowercase() in SENSITIVE_JSON_KEYS
    }

    private companion object {
        const val REDACTED_VALUE = "[REDACTED]"
        val SENSITIVE_JSON_KEYS = setOf(
            "token",
            "apitoken",
            "websockettoken",
            "runtimetoken",
            "authorization",
            "password",
            "passphrase"
        )
    }
}
