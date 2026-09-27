package com.aqua.aqualight.data.aquarium

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Process-wide ordering for cross-store tank mutations. Acquire before any store
 * transaction; cleanup callbacks execute under their caller's gate and must not
 * acquire it again. No coroutine-context bypass is used: child jobs also wait.
 */
internal class OwnerTankMutationGate {
    private data class Key(val ownerUid: String, val tankId: Long)
    private class Entry(val mutex: Mutex = Mutex(), var users: Int = 0)

    private val monitor = Any()
    private val entries = mutableMapOf<Key, Entry>()

    internal val reservedTankCount: Int
        get() = synchronized(monitor) { entries.size }

    suspend fun <T> withTanks(
        ownerUid: String,
        tankIds: Iterable<Long>,
        block: suspend () -> T
    ): T {
        require(ownerUid.isNotBlank() && ownerUid == ownerUid.trim())
        val keys = tankIds.distinct().sorted().map { tankId ->
            require(tankId > 0L)
            Key(ownerUid, tankId)
        }
        require(keys.isNotEmpty())
        val reserved = synchronized(monitor) {
            keys.map { key ->
                key to entries.getOrPut(key, ::Entry).also { it.users += 1 }
            }
        }
        var acquired = 0
        try {
            reserved.forEach { (_, entry) ->
                entry.mutex.lock()
                acquired += 1
            }
            currentCoroutineContext().ensureActive()
            return block()
        } finally {
            // Non-suspending release also runs when a waiter or holder is cancelled.
            reserved.take(acquired).asReversed().forEach { (_, entry) -> entry.mutex.unlock() }
            synchronized(monitor) {
                reserved.forEach { (key, entry) ->
                    entry.users -= 1
                    if (entry.users == 0) entries.remove(key)
                }
            }
        }
    }

    companion object {
        val shared = OwnerTankMutationGate()
    }
}
