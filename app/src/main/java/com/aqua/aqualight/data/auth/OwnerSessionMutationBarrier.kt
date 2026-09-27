package com.aqua.aqualight.data.auth

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orders admitted local writes with owner open/close. Lock order is session,
 * then owner/tank, then store. A write retains its session through durable commit;
 * checking a snapshot alone would leave a check/commit race with logout.
 */
internal class OwnerSessionMutationBarrier(
    private val stateMachine: OwnerSessionStateMachine
) {
    private val mutex = Mutex()

    suspend fun <T> withTransition(block: suspend () -> T): T = mutex.withLock {
        currentCoroutineContext().ensureActive()
        block()
    }

    fun bind(ownerUid: String, generation: Long): OwnerSessionWriteLease {
        require(ownerUid.isNotBlank() && ownerUid == ownerUid.trim())
        require(generation > 0L)
        return OwnerSessionWriteLease(ownerUid, generation, this).also { it.requireCurrent() }
    }

    internal fun requireCurrent(ownerUid: String, generation: Long) {
        val snapshot = stateMachine.snapshot()
        if (snapshot.activeOwnerUid != ownerUid || snapshot.pendingOwnerUid != null ||
            snapshot.generation != generation
        ) {
            throw OwnerSessionExpiredException()
        }
    }

    internal suspend fun <T> withWrite(lease: OwnerSessionWriteLease, block: suspend () -> T): T =
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            lease.requireCurrent()
            block()
        }
}

internal class OwnerSessionWriteLease internal constructor(
    val ownerUid: String,
    val generation: Long,
    private val barrier: OwnerSessionMutationBarrier
) {
    fun requireCurrent() = barrier.requireCurrent(ownerUid, generation)

    suspend fun <T> withWrite(block: suspend () -> T): T = barrier.withWrite(this, block)
}

internal class OwnerSessionExpiredException : CancellationException("The owner session is no longer active.")
