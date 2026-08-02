package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns the single in-flight authenticated liveness probe allowed for each device. */
internal class DeviceAuthenticatedLivenessProbeScheduler {
    private val jobs = ConcurrentHashMap<DeviceUid, Job>()

    fun isActive(deviceUid: DeviceUid): Boolean = jobs[deviceUid]?.isActive == true

    fun schedule(
        scope: CoroutineScope,
        deviceUid: DeviceUid,
        execute: suspend () -> Boolean,
        onRejected: () -> Unit
    ): Boolean {
        if (isActive(deviceUid)) return false
        val candidate = scope.launch(start = CoroutineStart.LAZY) {
            if (!execute()) onRejected()
        }
        val existing = jobs.putIfAbsent(deviceUid, candidate)
        return if (existing == null) {
            candidate.invokeOnCompletion { jobs.remove(deviceUid, candidate) }
            candidate.start()
            true
        } else {
            candidate.cancel()
            false
        }
    }

    fun cancelAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
