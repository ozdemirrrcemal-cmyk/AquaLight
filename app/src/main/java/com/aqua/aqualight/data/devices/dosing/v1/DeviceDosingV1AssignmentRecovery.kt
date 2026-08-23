package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Owner-scoped recovery checkpoint for assignment intents.
 *
 * This class owns no firmware or presentation state. It only records that a transport/ambiguous
 * command boundary was crossed and lets the latest-intent workers wait until a reconnect bootstrap
 * has completed. An interruption reconciled immediately is recorded as already recovered, which
 * still forces the caller to restart from the latest desired assignment instead of replaying the
 * stale in-flight target.
 */
internal class DeviceDosingV1AssignmentRecoveryGate {
    private val lock = Any()
    private val devices = HashMap<DeviceUid, RecoveryState>()

    fun currentInterruptionEpoch(deviceUid: DeviceUid): Long = synchronized(lock) {
        devices[deviceUid]?.interruptionEpoch ?: 0L
    }

    fun pendingInterruption(deviceUid: DeviceUid): Long? = synchronized(lock) {
        devices[deviceUid]?.let { state ->
            state.interruptionEpoch.takeIf { it > state.recoveredEpoch }
        }
    }

    fun interruptionAfter(deviceUid: DeviceUid, epoch: Long): Long? = synchronized(lock) {
        devices[deviceUid]?.interruptionEpoch?.takeIf { it > epoch }
    }

    fun markTransportInterrupted(deviceUid: DeviceUid): Long = synchronized(lock) {
        state(deviceUid).let { current ->
            current.interruptionEpoch += 1L
            current.interruptionEpoch
        }
    }

    fun markRecoveredInterruption(deviceUid: DeviceUid): Long {
        val completed = mutableListOf<CompletableDeferred<Unit>>()
        val epoch = synchronized(lock) {
            state(deviceUid).let { current ->
                current.interruptionEpoch += 1L
                current.recoveredEpoch = current.interruptionEpoch
                drainRecoveredWaiters(current, completed)
                current.interruptionEpoch
            }
        }
        completed.forEach { waiter -> waiter.complete(Unit) }
        return epoch
    }

    /** Called only after the normal authenticated Dosing bootstrap has had its first read chance. */
    fun markAuthenticated(deviceUid: DeviceUid) {
        val completed = mutableListOf<CompletableDeferred<Unit>>()
        synchronized(lock) {
            state(deviceUid).let { current ->
                current.recoveredEpoch = current.interruptionEpoch
                drainRecoveredWaiters(current, completed)
            }
        }
        completed.forEach { waiter -> waiter.complete(Unit) }
    }

    suspend fun awaitRecovery(deviceUid: DeviceUid, interruptionEpoch: Long) {
        val waiter = synchronized(lock) {
            val current = state(deviceUid)
            if (current.recoveredEpoch >= interruptionEpoch) {
                null
            } else {
                CompletableDeferred<Unit>().also { deferred ->
                    current.waiters += RecoveryWaiter(interruptionEpoch, deferred)
                }
            }
        } ?: return

        try {
            waiter.await()
        } catch (cancellation: CancellationException) {
            synchronized(lock) {
                devices[deviceUid]?.waiters?.removeAll { candidate ->
                    candidate.deferred === waiter
                }
            }
            throw cancellation
        }
    }

    private fun state(deviceUid: DeviceUid): RecoveryState =
        devices.getOrPut(deviceUid) { RecoveryState() }

    private fun drainRecoveredWaiters(
        state: RecoveryState,
        completed: MutableList<CompletableDeferred<Unit>>
    ) {
        val iterator = state.waiters.iterator()
        while (iterator.hasNext()) {
            val waiter = iterator.next()
            if (waiter.interruptionEpoch <= state.recoveredEpoch) {
                iterator.remove()
                completed += waiter.deferred
            }
        }
    }

    private class RecoveryState(
        var interruptionEpoch: Long = 0L,
        var recoveredEpoch: Long = 0L,
        val waiters: MutableList<RecoveryWaiter> = mutableListOf()
    )

    private data class RecoveryWaiter(
        val interruptionEpoch: Long,
        val deferred: CompletableDeferred<Unit>
    )
}

/** Typed owner-scope context; lifecycle coordination is not a second state/source-of-truth. */
internal class DeviceDosingV1AssignmentRecoveryContext(
    val gate: DeviceDosingV1AssignmentRecoveryGate
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<DeviceDosingV1AssignmentRecoveryContext>
}

internal fun CoroutineScope?.dosingAssignmentRecoveryGate(): DeviceDosingV1AssignmentRecoveryGate? =
    this?.coroutineContext?.get(DeviceDosingV1AssignmentRecoveryContext)?.gate
