package com.aqua.aqualight.data.devices.runtime.state

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.Job

internal class DeviceRuntimeRefreshJobRegistry {
    private val lock = Any()
    private val queuedTargets = mutableMapOf<DeviceUid, MutableSet<DeviceRuntimeRefreshTarget>>()
    private val refreshJobs = mutableMapOf<DeviceUid, Job>()
    private val bootstrapJobs = mutableMapOf<DeviceUid, Job>()

    fun replaceBootstrap(
        deviceUid: DeviceUid,
        createJob: () -> Job
    ) {
        synchronized(lock) {
            bootstrapJobs.remove(deviceUid)?.cancel()
            createJob().also { job ->
                bootstrapJobs[deviceUid] = job
                job.start()
            }
        }
    }

    fun completeBootstrap(deviceUid: DeviceUid, job: Job) {
        synchronized(lock) {
            if (bootstrapJobs[deviceUid] == job) {
                bootstrapJobs.remove(deviceUid)
            }
        }
    }

    fun enqueueAndStartRefresh(
        deviceUid: DeviceUid,
        targets: Set<DeviceRuntimeRefreshTarget>,
        createJob: () -> Job
    ) {
        synchronized(lock) {
            queuedTargets.getOrPut(deviceUid) { linkedSetOf() }.addAll(targets)
            if (refreshJobs[deviceUid]?.isActive != true) {
                createJob().also { job ->
                    refreshJobs[deviceUid] = job
                    job.start()
                }
            }
        }
    }

    fun takeTargets(deviceUid: DeviceUid): Set<DeviceRuntimeRefreshTarget> =
        synchronized(lock) {
            queuedTargets.remove(deviceUid).orEmpty().toSet()
        }

    fun completeRefresh(deviceUid: DeviceUid, job: Job): Boolean = synchronized(lock) {
        if (refreshJobs[deviceUid] == job) {
            refreshJobs.remove(deviceUid)
        }
        queuedTargets[deviceUid].orEmpty().isNotEmpty()
    }

    fun cancelDevice(deviceUid: DeviceUid) {
        synchronized(lock) {
            bootstrapJobs.remove(deviceUid)?.cancel()
            refreshJobs.remove(deviceUid)?.cancel()
            queuedTargets.remove(deviceUid)
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            bootstrapJobs.values.forEach(Job::cancel)
            refreshJobs.values.forEach(Job::cancel)
            bootstrapJobs.clear()
            refreshJobs.clear()
            queuedTargets.clear()
        }
    }
}
