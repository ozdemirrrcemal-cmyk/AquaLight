package com.aqua.aqualight.data.devices.dosing.v1

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owner-scoped single writer for one Dosing channel.
 *
 * Firmware mutations for the same device/channel are consumed by exactly one worker in submission
 * order. A UI waiter may disappear without cancelling an accepted owner-scoped mutation. Different
 * channels remain independent. Adapters without an owner scope use a mutex-backed test fallback.
 */
internal class DeviceDosingV1ChannelMutationProcessor(
    private val ownerScope: CoroutineScope?
) {
    private val workers = ConcurrentHashMap<DeviceDosingV1Address, MutationWorker>()
    private val fallbackLocks = ConcurrentHashMap<DeviceDosingV1Address, Mutex>()

    suspend fun <T> submit(
        address: DeviceDosingV1Address,
        afterResultPublished: (T) -> Unit = {},
        mutation: suspend () -> T
    ): T {
        val scope = ownerScope ?: return fallbackLocks
            .computeIfAbsent(address) { Mutex() }
            .withLock {
                mutation().also(afterResultPublished)
            }

        val result = CompletableDeferred<T>()
        val task = MutationTask(
            execute = {
                try {
                    val value = mutation()
                    // Foreground completion wins scheduling priority over optional reconciliation.
                    result.complete(value)
                    afterResultPublished(value)
                } catch (cancellation: CancellationException) {
                    result.cancel(cancellation)
                    throw cancellation
                } catch (failure: Throwable) {
                    result.completeExceptionally(failure)
                }
            },
            cancel = { cancellation -> result.cancel(cancellation) }
        )
        val accepted = worker(address, scope).queue.trySend(task)
        if (accepted.isFailure) {
            task.cancel(CancellationException("Dosing mutation owner is unavailable"))
        }
        return result.await()
    }

    private fun worker(
        address: DeviceDosingV1Address,
        scope: CoroutineScope
    ): MutationWorker {
        workers[address]?.let { return it }

        val queue = Channel<MutationTask>(
            capacity = Channel.UNLIMITED,
            onUndeliveredElement = { task ->
                task.cancel(CancellationException("Dosing mutation worker closed"))
            }
        )
        lateinit var candidate: MutationWorker
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                for (task in queue) task.execute()
            } finally {
                queue.cancel()
                workers.remove(address, candidate)
            }
        }
        candidate = MutationWorker(queue)
        val existing = workers.putIfAbsent(address, candidate)
        return if (existing == null) {
            job.start()
            candidate
        } else {
            queue.cancel()
            job.cancel()
            existing
        }
    }

    private data class MutationWorker(
        val queue: Channel<MutationTask>
    )

    private data class MutationTask(
        val execute: suspend () -> Unit,
        val cancel: (CancellationException) -> Unit
    )
}
