package com.aqua.aqualight.base.diagnostics

import android.os.SystemClock
import java.security.MessageDigest

/**
 * Process-local diagnostic event boundary.
 *
 * Production builds keep the default no-op sink. The debug source set may install a bounded,
 * in-memory observer without creating another application or device state owner.
 */
internal object AppDiagnosticTrace {

    internal data class Event(
        val wallClockMillis: Long,
        val elapsedRealtimeMillis: Long,
        val category: String,
        val name: String,
        val fields: List<Field>
    )

    internal data class Field(
        val key: String,
        val value: String
    )

    internal fun interface Sink {
        fun record(event: Event)
    }

    private object NoOpSink : Sink {
        override fun record(event: Event) = Unit
    }

    @Volatile
    private var sink: Sink = NoOpSink

    val isEnabled: Boolean
        get() = sink !== NoOpSink

    fun install(installedSink: Sink) {
        sink = installedSink
    }

    /** Returns an opaque value accepted only by the sanitizer's `device` field. */
    fun deviceRef(rawUid: String): Any =
        DiagnosticSanitizer.deviceReference(rawUid)

    fun event(
        category: String,
        name: String,
        vararg fields: Pair<String, Any?>
    ) {
        val activeSink = sink
        if (activeSink === NoOpSink) return

        val safeEvent = Event(
            wallClockMillis = System.currentTimeMillis(),
            elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            category = DiagnosticSanitizer.token(category, MAX_CATEGORY_LENGTH),
            name = DiagnosticSanitizer.token(name, MAX_EVENT_NAME_LENGTH),
            fields = DiagnosticSanitizer.fields(fields)
        )
        runCatching { activeSink.record(safeEvent) }.getOrNull()
    }

    private const val MAX_CATEGORY_LENGTH = 24
    private const val MAX_EVENT_NAME_LENGTH = 72
}

@JvmInline
private value class DiagnosticDeviceReference(val value: String)

/** Only explicitly diagnostic, non-user-content fields are allowed across the central boundary. */
private object DiagnosticSanitizer {

    private val booleanKeys = setOf(
        "accepted",
        "authoritative",
        "busy",
        "canSave",
        "checked",
        "completionAccepted",
        "connected",
        "cancelled",
        "editable",
        "enabled",
        "hasAssignment",
        "hasContinuation",
        "hasOrigin",
        "handled",
        "initialized",
        "persisted",
        "recalibration",
        "replaced",
        "replayable",
        "saved",
        "satisfied",
        "target"
    )

    private val numericKeys = setOf(
        "ackRevision",
        "attempt",
        "baselineRevision",
        "baseRevision",
        "cachedRevision",
        "channelGeneration",
        "currentRevision",
        "currentRuntimeSequence",
        "destinationId",
        "eventRevision",
        "eventSequence",
        "existingMinimumRevision",
        "expectedRevision",
        "generation",
        "globalGeneration",
        "minimumRevision",
        "originRevision",
        "progressRevision",
        "progressGeneration",
        "previousRevision",
        "requestGeneration",
        "requiredRevision",
        "revision",
        "runtimeSequence",
        "slotCount",
        "status",
        "statusCode",
        "timeoutMillis"
    )

    private val tokenKeys = setOf(
        "action",
        "activity",
        "authority",
        "baselineSource",
        "cancellationReason",
        "channel",
        "control",
        "destination",
        "disposition",
        "envelope",
        "errorCode",
        "errorField",
        "eventId",
        "eventType",
        "expectedAction",
        "expectedModule",
        "failure",
        "fromPhase",
        "fromStep",
        "lifecycleType",
        "module",
        "operation",
        "outcome",
        "phase",
        "previousAuthority",
        "reason",
        "requestId",
        "result",
        "route",
        "screen",
        "scope",
        "slot",
        "source",
        "state",
        "step",
        "toPhase",
        "toStep",
        "viewClass",
        "viewId",
        "visibility"
    )

    fun fields(rawFields: Array<out Pair<String, Any?>>): List<AppDiagnosticTrace.Field> =
        rawFields
            .asSequence()
            .mapNotNull { (key, value) -> field(key, value) }
            .take(MAX_FIELDS_PER_EVENT)
            .toList()

    fun token(rawValue: String, maximumLength: Int = MAX_FIELD_VALUE_LENGTH): String {
        val normalized = rawValue
            .trim()
            .take(maximumLength)
            .map { character ->
                if (character.isSafeDiagnosticCharacter()) character else '_'
            }
            .joinToString(separator = "")
        return normalized.ifBlank { UNKNOWN_VALUE }
    }

    fun deviceReference(rawUid: String): DiagnosticDeviceReference {
        val normalized = rawUid.trim()
        if (normalized.isEmpty()) {
            return DiagnosticDeviceReference(UNKNOWN_DEVICE_REFERENCE)
        }
        val digest = MessageDigest.getInstance(DIGEST_ALGORITHM)
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val reference = buildString(DEVICE_REFERENCE_PREFIX.length + DEVICE_REFERENCE_HEX_LENGTH) {
            append(DEVICE_REFERENCE_PREFIX)
            repeat(DEVICE_REFERENCE_BYTE_COUNT) { index ->
                val value = digest[index].toInt() and UNSIGNED_BYTE_MASK
                append(HEX_DIGITS[value ushr HEX_NIBBLE_BITS])
                append(HEX_DIGITS[value and HEX_NIBBLE_MASK])
            }
        }
        return DiagnosticDeviceReference(reference)
    }

    private fun field(key: String, value: Any?): AppDiagnosticTrace.Field? {
        val safeValue = when {
            key == DEVICE_KEY && value is DiagnosticDeviceReference -> value.value
            key in booleanKeys && value is Boolean -> value.toString()
            key in numericKeys && value is Number -> value.toString()
            key in tokenKeys && value is Enum<*> -> token(value.name)
            key in tokenKeys && value is String -> token(value)
            else -> null
        } ?: return null

        return AppDiagnosticTrace.Field(
            key = key,
            value = safeValue
        )
    }

    private fun Char.isSafeDiagnosticCharacter(): Boolean =
        this in 'a'..'z' ||
            this in 'A'..'Z' ||
            this in '0'..'9' ||
            this in SAFE_PUNCTUATION

    private const val MAX_FIELD_VALUE_LENGTH = 96
    private const val MAX_FIELDS_PER_EVENT = 24
    private const val UNKNOWN_VALUE = "unknown"
    private const val DEVICE_KEY = "device"
    private const val UNKNOWN_DEVICE_REFERENCE = "device-unknown"
    private const val DEVICE_REFERENCE_PREFIX = "device-"
    private const val DEVICE_REFERENCE_BYTE_COUNT = 6
    private const val DEVICE_REFERENCE_HEX_LENGTH = DEVICE_REFERENCE_BYTE_COUNT * 2
    private const val DIGEST_ALGORITHM = "SHA-256"
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val HEX_NIBBLE_BITS = 4
    private const val HEX_NIBBLE_MASK = 0x0F
    private const val UNSIGNED_BYTE_MASK = 0xFF
    private const val SAFE_PUNCTUATION = "._:/@#=+- "
}
