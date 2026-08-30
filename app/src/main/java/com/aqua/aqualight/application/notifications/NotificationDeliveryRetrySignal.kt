package com.aqua.aqualight.application.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-local, payload-free signal for retrying durable notification intents.
 *
 * The signal owns no notification or owner state. A missed signal is safe because every durable
 * producer re-evaluates its pending ledger from authoritative state when its runtime starts.
 */
object NotificationDeliveryRetrySignal {
    private val signals = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun observe(): Flow<Unit> = signals

    fun requestRetry() {
        signals.tryEmit(Unit)
    }
}
