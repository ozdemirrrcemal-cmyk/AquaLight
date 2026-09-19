package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Schedules optional post-ACK dashboard reconciliation without blocking the user mutation. */
internal class DeviceLightCommittedReconciliationScheduler(
    private val scope: CoroutineScope,
    private val refreshCoordinator: DeviceLightDashboardRefreshCoordinator
) {
    private val lock = Any()
    private val scheduled = HashMap<DeviceUid, ScheduledReconciliation>()

    fun schedule(deviceUid: DeviceUid, expectedMode: DeviceLightMode) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            refreshCoordinator.reconcileCommitted(deviceUid, expectedMode)
        }
        val previous = synchronized(lock) {
            val current = scheduled[deviceUid]
            if (
                current != null &&
                current.expectedMode == expectedMode &&
                !current.job.isCompleted
            ) {
                job.cancel()
                return
            }
            scheduled[deviceUid] = ScheduledReconciliation(expectedMode, job)
            current?.job
        }
        previous?.cancel()
        job.invokeOnCompletion {
            synchronized(lock) {
                if (scheduled[deviceUid]?.job === job) scheduled.remove(deviceUid)
            }
        }
        job.start()
    }

    fun cancel(deviceUid: DeviceUid) {
        synchronized(lock) { scheduled.remove(deviceUid) }?.job?.cancel()
    }

    private data class ScheduledReconciliation(
        val expectedMode: DeviceLightMode,
        val job: Job
    )
}
