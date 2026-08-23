package com.aqua.aqualight.data.devices.dosing.v1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * Owner-scoped scheduler for post-ACK readback.
 *
 * At most one optional reconciliation waiter is retained per channel. Its shared refresh producer
 * is owner-scoped, so replacing or cancelling this waiter never aborts an in-flight device read.
 */
internal class DeviceDosingV1CommittedReconciliationScheduler(
    private val scope: CoroutineScope,
    private val refreshCoordinator: DeviceDosingV1RefreshCoordinator
) {
    private val lock = Any()
    private val scheduled = HashMap<DeviceDosingV1Address, ScheduledReconciliation>()

    fun schedule(address: DeviceDosingV1Address, minimumRevision: Long) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // Let the durable ACK result reach the foreground before optional readback competes for
            // runtime bandwidth. This is scheduling priority, not a correctness dependency.
            yield()
            repeat(MAX_STALE_RECONCILIATION_ATTEMPTS) {
                val result = refreshCoordinator.reconcileCommitted(address, minimumRevision)
                if (result != DeviceDosingV1RefreshResult.RejectedStale) return@launch
            }
        }
        val previous = synchronized(lock) {
            val current = scheduled[address]
            if (current != null && current.minimumRevision >= minimumRevision) {
                job.cancel()
                return
            }
            scheduled[address] = ScheduledReconciliation(minimumRevision, job)
            current?.job
        }
        previous?.cancel()
        job.invokeOnCompletion {
            synchronized(lock) {
                if (scheduled[address]?.job === job) scheduled.remove(address)
            }
        }
        job.start()
    }

    private data class ScheduledReconciliation(
        val minimumRevision: Long,
        val job: Job
    )
}

private const val MAX_STALE_RECONCILIATION_ATTEMPTS = 2
