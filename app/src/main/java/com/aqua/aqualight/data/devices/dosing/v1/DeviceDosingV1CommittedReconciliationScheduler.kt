package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
        address.traceReconciliation("schedule_requested", "minimumRevision" to minimumRevision)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            address.traceReconciliation("job_started", "minimumRevision" to minimumRevision)
            val result = refreshCoordinator.reconcileCommitted(address, minimumRevision)
            address.traceReconciliation(
                "job_result",
                "minimumRevision" to minimumRevision,
                "result" to result.javaClass.simpleName
            )
        }
        val previous = synchronized(lock) {
            val current = scheduled[address]
            if (current != null && current.minimumRevision >= minimumRevision) {
                address.traceReconciliation(
                    "schedule_ignored",
                    "minimumRevision" to minimumRevision,
                    "existingMinimumRevision" to current.minimumRevision
                )
                job.cancel()
                return
            }
            scheduled[address] = ScheduledReconciliation(minimumRevision, job)
            current?.job
        }
        address.traceReconciliation(
            "schedule_installed",
            "minimumRevision" to minimumRevision,
            "replaced" to (previous != null)
        )
        previous?.cancel()
        job.invokeOnCompletion { cause ->
            synchronized(lock) {
                if (scheduled[address]?.job === job) scheduled.remove(address)
            }
            address.traceReconciliation(
                "job_completed",
                "minimumRevision" to minimumRevision,
                "cancelled" to (cause != null)
            )
        }
        job.start()
    }

    private data class ScheduledReconciliation(
        val minimumRevision: Long,
        val job: Job
    )
}

private fun DeviceDosingV1Address.traceReconciliation(
    name: String,
    vararg fields: Pair<String, Any?>
) {
    AppDiagnosticTrace.event(
        DOSING_RECONCILIATION_CATEGORY,
        name,
        "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
        "slot" to channelKey.value,
        *fields
    )
}

private const val DOSING_RECONCILIATION_CATEGORY = "dosing_reconciliation"
