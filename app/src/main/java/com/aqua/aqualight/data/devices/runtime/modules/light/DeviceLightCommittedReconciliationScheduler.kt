package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Schedules optional post-ACK central Light reconciliation without blocking the mutation. */
internal class DeviceLightCommittedReconciliationScheduler(
    private val scope: CoroutineScope,
    private val refreshCoordinator: DeviceLightRuntimeRefreshCoordinator
) {
    private val lock = Any()
    private val scheduled = HashMap<DeviceUid, ScheduledReconciliation>()

    fun schedule(
        deviceUid: DeviceUid,
        expectedMode: DeviceLightMode,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            refreshCoordinator.reconcileCommitted(deviceUid, expectedMode, generation)
        }
        val previous = synchronized(lock) {
            val current = scheduled[deviceUid]
            if (current?.isPendingFor(expectedMode, generation) == true) {
                job.cancel()
                return
            }
            scheduled[deviceUid] = ScheduledReconciliation(expectedMode, generation, job)
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

    private fun ScheduledReconciliation.isPendingFor(
        expectedMode: DeviceLightMode,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean =
        this.expectedMode == expectedMode &&
            this.generation == generation &&
            !job.isCompleted

    private data class ScheduledReconciliation(
        val expectedMode: DeviceLightMode,
        val generation: DeviceRuntimeConnectionGeneration,
        val job: Job
    )
}
