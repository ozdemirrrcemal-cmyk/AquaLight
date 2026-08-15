package com.aqua.aqualight.ui.common.notification

import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job

/**
 * Serializes one notification-enablement intent and invalidates every older async result.
 *
 * A duplicate tap for the active action is ignored. A different action, an explicit cancel,
 * or a resumed platform continuation advances the generation and cancels the previous job.
 */
internal class NotificationEnablementOperationGate {
    data class Ticket(
        val actionToken: String,
        internal val generation: Long
    )

    private var generation: Long = 0L
    private var activeActionToken: String? = null
    private var activeJob: Job? = null

    val hasActiveOperation: Boolean
        get() = activeActionToken != null

    fun begin(actionToken: String): Ticket? {
        val normalized = actionToken.requireActionToken()
        if (activeActionToken == normalized) return null
        return replaceWith(normalized)
    }

    /** Resumes a process-safe platform continuation as the only current operation. */
    fun resume(actionToken: String): Ticket = replaceWith(actionToken.requireActionToken())

    fun attach(ticket: Ticket, job: Job) {
        if (!isCurrent(ticket)) {
            job.cancel()
            return
        }
        activeJob?.takeUnless { current -> current === job }?.cancel()
        activeJob = job
        job.invokeOnCompletion { failure ->
            if (failure is CancellationException) complete(ticket)
        }
    }

    fun isCurrent(ticket: Ticket): Boolean =
        ticket.generation == generation && activeActionToken == ticket.actionToken

    fun complete(ticket: Ticket) {
        if (!isCurrent(ticket)) return
        advanceGeneration()
        activeActionToken = null
        activeJob = null
    }

    fun cancel() {
        advanceGeneration()
        activeJob?.cancel()
        activeActionToken = null
        activeJob = null
    }

    private fun replaceWith(actionToken: String): Ticket {
        cancel()
        activeActionToken = actionToken
        return Ticket(actionToken = actionToken, generation = generation)
    }

    private fun advanceGeneration() {
        generation = if (generation == Long.MAX_VALUE) 0L else generation + 1L
    }
}

private fun String.requireActionToken(): String = trim().also { normalized ->
    require(normalized.isNotEmpty()) { "Notification enablement action token must not be blank." }
}
