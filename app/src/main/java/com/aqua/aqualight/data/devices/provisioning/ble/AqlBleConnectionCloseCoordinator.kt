package com.aqua.aqualight.data.devices.provisioning.ble

import java.util.IdentityHashMap

/**
 * Releases a BLE connection only after Android reports disconnection, with a
 * bounded fallback for Bluetooth stacks that never deliver the callback.
 */
internal class AqlBleConnectionCloseCoordinator<T : Any>(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val disconnect: (T) -> Unit,
    private val release: (T) -> Unit,
    private val fallbackDelayMillis: Long
) {
    private val lock = Any()
    private val pending = IdentityHashMap<T, PendingClose>()

    fun beginGracefulClose(
        connection: T,
        onReleased: () -> Unit = {}
    ) {
        val timeout = Runnable { finish(connection) }
        val added = synchronized(lock) {
            if (pending.containsKey(connection)) {
                false
            } else {
                pending[connection] = PendingClose(timeout, onReleased)
                true
            }
        }
        if (!added) return

        val scheduled = runCatching {
            schedule(timeout, fallbackDelayMillis)
        }.isSuccess
        if (!scheduled) {
            finish(connection)
            return
        }

        runCatching { disconnect(connection) }
            .onFailure { finish(connection) }
    }

    fun handleConnectionState(
        connection: T,
        disconnected: Boolean,
        failed: Boolean
    ): Boolean {
        val isPending = synchronized(lock) { pending.containsKey(connection) }
        if (!isPending) return false
        if (disconnected || failed) finish(connection)
        return true
    }

    private fun finish(connection: T) {
        val closing = synchronized(lock) { pending.remove(connection) } ?: return
        runCatching { cancel(closing.timeout) }
        runCatching { release(connection) }
        runCatching { closing.onReleased() }
    }

    private data class PendingClose(
        val timeout: Runnable,
        val onReleased: () -> Unit
    )
}
