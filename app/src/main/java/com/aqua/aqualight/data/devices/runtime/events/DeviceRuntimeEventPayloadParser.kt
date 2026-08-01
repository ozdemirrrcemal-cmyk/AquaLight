package com.aqua.aqualight.data.devices.runtime.events

import org.json.JSONObject

/** Validates the two firmware event payload shapes without weakening malformed envelopes. */
internal object DeviceRuntimeEventPayloadParser {

    fun parse(data: JSONObject): Result {
        val keys = data.keys().asSequence().toSet()
        return when {
            keys.none(COMMAND_EVENT_FIELDS::contains) -> parseSnapshot(data)
            keys != COMMAND_EVENT_FIELDS -> Result.Invalid(FIELD_DATA)
            else -> parseCommandResult(data)
        }
    }

    private fun parseSnapshot(data: JSONObject): Result = copyJson(data)?.let { copy ->
        Result.Parsed(DeviceRuntimeEventPayload.Snapshot(copy))
    } ?: Result.Invalid(FIELD_DATA)

    private fun parseCommandResult(data: JSONObject): Result {
        val candidate = CommandResultCandidate(
            commandId = requiredText(data, FIELD_COMMAND_ID),
            commandModule = requiredText(data, FIELD_MODULE),
            commandAction = requiredText(data, FIELD_ACTION),
            sessionId = requiredText(data, FIELD_SESSION_ID),
            publishedAtMillis = runCatching { data.getLong(FIELD_PUBLISHED_AT_MS) }
                .getOrNull()
                ?.takeIf { value -> value >= 0L },
            result = data.optJSONObject(FIELD_RESULT)?.let(::copyJson)
        )
        return candidate.invalidField()?.let(Result::Invalid)
            ?: Result.Parsed(candidate.toPayload())
    }

    private fun requiredText(data: JSONObject, field: String): String? =
        data.optString(field, "").trim().takeIf(String::isNotEmpty)

    private fun copyJson(source: JSONObject): JSONObject? =
        runCatching { JSONObject(source.toString()) }.getOrNull()

    private data class CommandResultCandidate(
        val commandId: String?,
        val commandModule: String?,
        val commandAction: String?,
        val sessionId: String?,
        val publishedAtMillis: Long?,
        val result: JSONObject?
    ) {
        fun invalidField(): String? = when {
            commandId == null -> FIELD_COMMAND_ID
            commandModule == null -> FIELD_MODULE
            commandAction == null -> FIELD_ACTION
            sessionId == null -> FIELD_SESSION_ID
            publishedAtMillis == null -> FIELD_PUBLISHED_AT_MS
            result == null -> FIELD_RESULT
            else -> null
        }

        fun toPayload(): DeviceRuntimeEventPayload.CommandResult =
            DeviceRuntimeEventPayload.CommandResult(
                commandId = checkNotNull(commandId),
                commandModule = checkNotNull(commandModule),
                commandAction = checkNotNull(commandAction),
                sessionId = checkNotNull(sessionId),
                publishedAtMillis = checkNotNull(publishedAtMillis),
                result = checkNotNull(result)
            )
    }

    sealed interface Result {
        data class Parsed(
            val payload: DeviceRuntimeEventPayload
        ) : Result

        data class Invalid(
            val field: String
        ) : Result
    }

    private const val FIELD_DATA = "data"
    private const val FIELD_COMMAND_ID = "commandId"
    private const val FIELD_MODULE = "module"
    private const val FIELD_ACTION = "action"
    private const val FIELD_SESSION_ID = "sessionId"
    private const val FIELD_PUBLISHED_AT_MS = "publishedAtMs"
    private const val FIELD_RESULT = "result"

    private val COMMAND_EVENT_FIELDS = setOf(
        FIELD_COMMAND_ID,
        FIELD_MODULE,
        FIELD_ACTION,
        FIELD_SESSION_ID,
        FIELD_PUBLISHED_AT_MS,
        FIELD_RESULT
    )
}
