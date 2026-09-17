package com.aqua.aqualight.application.devices.provisioning

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Temporary, owner-local provisioning trace used by the diagnostics APK.
 *
 * Callers must record state names and failure summaries only. Credentials,
 * encrypted envelopes and raw protocol frames are intentionally never accepted.
 */
object ProvisioningRuntimeDiagnostics {
    private val lock = Any()
    private val entries = ArrayDeque<String>()
    private var startedAtNanos: Long = System.nanoTime()

    fun reset() {
        synchronized(lock) {
            entries.clear()
            startedAtNanos = System.nanoTime()
            appendLocked("SESSION", "diagnostics started")
        }
    }

    fun record(stage: String, detail: String = "") {
        synchronized(lock) {
            appendLocked(stage, detail)
        }
    }

    fun report(error: Throwable? = null): String = synchronized(lock) {
        buildString {
            append("AquaLight provisioning diagnostics\n")
            entries.forEach { entry -> append(entry).append('\n') }
            error?.let { failure ->
                append("ERROR_CHAIN ")
                append(failure.safeCauseChain())
                append('\n')
            }
        }.trimEnd()
    }

    private fun appendLocked(stage: String, detail: String) {
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)
        val normalizedStage = stage
            .uppercase(Locale.US)
            .replace(NON_STAGE_CHARACTER, '_')
            .take(MAX_STAGE_CHARS)
        val normalizedDetail = sanitize(detail).take(MAX_DETAIL_CHARS)
        entries.addLast(
            buildString {
                append('+').append(elapsed).append("ms ")
                append(normalizedStage)
                if (normalizedDetail.isNotBlank()) append(' ').append(normalizedDetail)
            }
        )
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    private fun Throwable.safeCauseChain(): String {
        val seen = mutableSetOf<Throwable>()
        val parts = mutableListOf<String>()
        var current: Throwable? = this
        while (current != null && seen.add(current) && parts.size < MAX_CAUSE_DEPTH) {
            val type = current::class.java.simpleName.ifBlank { "Throwable" }
            val message = sanitize(current.message.orEmpty()).ifBlank { "no-message" }
            parts += "$type:$message"
            current = current.cause
        }
        return parts.joinToString(" <- ")
    }

    private fun sanitize(value: String): String = value
        .replace(CREDENTIAL_FIELD, "\$1=<redacted>")
        .replace(HEX_CREDENTIAL, "<redacted-64-hex>")
        .replace(WHITESPACE, " ")
        .trim()

    private const val MAX_ENTRIES = 96
    private const val MAX_STAGE_CHARS = 40
    private const val MAX_DETAIL_CHARS = 320
    private const val MAX_CAUSE_DEPTH = 6
    private val NON_STAGE_CHARACTER = Regex("[^A-Z0-9_]")
    private val WHITESPACE = Regex("\\s+")
    private val HEX_CREDENTIAL = Regex("(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])")
    private val CREDENTIAL_FIELD = Regex(
        "(?i)\\b(token|password|claim|proof|nonce|secret)\\b\\s*[:=]\\s*[^,;\\s]+"
    )
}
