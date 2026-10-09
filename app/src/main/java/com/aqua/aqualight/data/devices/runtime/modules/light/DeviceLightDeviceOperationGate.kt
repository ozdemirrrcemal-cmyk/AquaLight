package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-device serialization boundary shared by Light refresh and mutations.
 * Optional reads yield to user writes at their next cancellable suspension, without waiting for
 * a firmware timeout. Mutation/ACK work is never registered as preemptible. Cancellation cleanup
 * must finish before the next command is dispatched; this is a software scheduling guarantee,
 * not a bound on the transport or on physical device response time. Writes still serialize behind
 * earlier mutations (including their inline ACK readback) and required minimal bootstrap work.
 */
internal class DeviceLightDeviceOperationGate(
    private val cancelOptionalReconciliation: (DeviceUid) -> Unit = {}
) {
    private class DeviceOperations {
        val mutex = Mutex()
        var pendingMutations = 0
        var refresh: Job? = null
    }

    private val devices = ConcurrentHashMap<DeviceUid, DeviceOperations>()

    suspend fun <T> withDevice(deviceUid: DeviceUid, block: suspend () -> T): T =
        operations(deviceUid).mutex.withLock { block() }

    /** A superseded read returns null without cancelling its caller or coalesced waiters. */
    suspend fun <T : Any> withRefresh(deviceUid: DeviceUid, block: suspend () -> T): T? =
        supervisorScope {
            val operations = operations(deviceUid)
            val flight = async(start = CoroutineStart.UNDISPATCHED) {
                operations.mutex.withLock {
                    val job = currentCoroutineContext().job
                    synchronized(operations) {
                        if (operations.pendingMutations > 0) throw RefreshSuperseded()
                        operations.refresh = job
                    }
                    try {
                        block()
                    } finally {
                        synchronized(operations) { operations.refresh = null }
                    }
                }
            }
            try {
                flight.await()
            } catch (_: RefreshSuperseded) {
                currentCoroutineContext().ensureActive()
                null
            }
        }

    suspend fun <T> withMutation(deviceUid: DeviceUid, block: suspend () -> T): T {
        val operations = operations(deviceUid)
        val refresh = synchronized(operations) {
            operations.pendingMutations += 1
            operations.refresh
        }
        return try {
            refresh?.cancel(RefreshSuperseded())
            cancelOptionalReconciliation(deviceUid)
            operations.mutex.withLock { block() }
        } finally {
            synchronized(operations) { operations.pendingMutations -= 1 }
        }
    }

    private fun operations(deviceUid: DeviceUid): DeviceOperations =
        devices.computeIfAbsent(deviceUid) { DeviceOperations() }

    private class RefreshSuperseded : CancellationException("Light refresh superseded by a user write")
}
