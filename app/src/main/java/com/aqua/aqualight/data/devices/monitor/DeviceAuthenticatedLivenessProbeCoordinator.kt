package com.aqua.aqualight.data.devices.monitor

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DeviceRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns only the in-flight and backoff mechanics of authenticated application probes.
 * Device online/offline decisions remain exclusively in [DevicePresenceRuntimeMonitor].
 */
internal class DeviceAuthenticatedLivenessProbeCoordinator(
    private val runtimeRepository: DeviceRuntimeRepository?
) {
    private val lastProbeAtMillis = ConcurrentHashMap<DeviceUid, Long>()
    private val inFlight = ConcurrentHashMap<DeviceUid, Job>()

    fun request(
        scope: CoroutineScope,
        deviceUid: DeviceUid,
        nowMillis: Long,
        force: Boolean,
        minimumIntervalMillis: Long,
        timeoutMillis: Long
    ): Boolean {
        val runtime = runtimeRepository ?: return false
        if (runtime.currentConnectionState(deviceUid) !is AqlWsConnectionState.Authenticated) {
            return false
        }
        if (inFlight[deviceUid]?.isActive == true) return true

        val previousAt = lastProbeAtMillis[deviceUid]
        if (!force && previousAt != null && nowMillis - previousAt < minimumIntervalMillis) {
            return false
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = runtime.runtimeModules.network.requestStatus(
                    deviceUid = deviceUid,
                    timeoutMillis = timeoutMillis
                )
                if (outcome !is DeviceRuntimeCommandOutcome.Success) {
                    lastProbeAtMillis.remove(deviceUid, nowMillis)
                }
            } catch (cancelled: CancellationException) {
                lastProbeAtMillis.remove(deviceUid, nowMillis)
                throw cancelled
            } catch (_: Throwable) {
                lastProbeAtMillis.remove(deviceUid, nowMillis)
            }
        }
        val existing = inFlight.putIfAbsent(deviceUid, job)
        if (existing != null) {
            job.cancel()
            return existing.isActive
        }

        lastProbeAtMillis[deviceUid] = nowMillis
        job.invokeOnCompletion {
            inFlight.remove(deviceUid, job)
        }
        job.start()
        return true
    }

    fun reset() {
        lastProbeAtMillis.clear()
        cancelInFlight()
    }

    fun cancelInFlight() {
        inFlight.values.forEach(Job::cancel)
        inFlight.clear()
    }

    internal fun isInFlight(deviceUid: DeviceUid): Boolean = inFlight[deviceUid]?.isActive == true

    internal fun lastProbeAt(deviceUid: DeviceUid): Long? = lastProbeAtMillis[deviceUid]
}
