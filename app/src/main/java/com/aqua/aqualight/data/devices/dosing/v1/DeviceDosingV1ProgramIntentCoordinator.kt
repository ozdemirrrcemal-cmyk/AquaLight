package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMutationOrigin
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Complete persisted program assignment carried by the central latest-intent worker. */
internal data class DeviceDosingV1ProgramAssignmentIntent(
    val program: DeviceDosingProgram,
    val origin: DeviceDosingProgramMutationOrigin?
)

/**
 * Owner-scoped latest-intent queue for persisted program assignments.
 *
 * At most one program assignment for one channel is executing. While that write is in flight,
 * later Save intents replace the pending target instead of growing an unbounded firmware queue.
 * All concurrent callers await one shared settlement, so rapid input also cannot grow an unbounded
 * waiter list. Cancelling a screen waiter never cancels the accepted owner-scoped worker.
 *
 * If a transport/ambiguous boundary is recorded during an attempt, the result is not surfaced as a
 * terminal failure. The worker waits for recovery when necessary and restarts from the newest target.
 */
internal class DeviceDosingV1ProgramIntentCoordinator(
    private val scope: CoroutineScope?,
    recoveryGate: DeviceDosingV1AssignmentRecoveryGate? = null,
    private val execute: suspend (
        deviceUid: String,
        slotId: String,
        intent: DeviceDosingV1ProgramAssignmentIntent
    ) -> DeviceDosingChannelOperationResult
) {
    private val recoveryGate = recoveryGate ?: scope.dosingAssignmentRecoveryGate()
    private val lock = Any()
    private val pending = HashMap<IntentAddress, PendingIntent>()

    suspend fun submit(
        deviceUid: String,
        slotId: String,
        intent: DeviceDosingV1ProgramAssignmentIntent
    ): DeviceDosingChannelOperationResult {
        val address = IntentAddress(deviceUid.trim(), slotId.trim())
        val submission = synchronized(lock) {
            val current = pending.getOrPut(address) { PendingIntent(intent) }
            current.target = intent
            current.generation += 1L
            val startWorker = if (current.running) {
                false
            } else {
                current.running = true
                true
            }
            Submission(current.settlement, startWorker)
        }
        if (submission.startWorker) {
            val ownerScope = scope
            if (ownerScope == null) {
                drive(address)
            } else {
                ownerScope.launch { drive(address) }
                    .invokeOnCompletion { failure ->
                        if (failure != null) completeTerminatedWorker(address, failure)
                    }
            }
        }
        return submission.settlement.await()
    }

    private suspend fun drive(address: IntentAddress) {
        try {
            while (!runLatestAttempt(address)) Unit
        } catch (cancellation: CancellationException) {
            synchronized(lock) { pending.remove(address) }
                ?.settlement
                ?.cancel(cancellation)
            throw cancellation
        }
    }

    private suspend fun runLatestAttempt(address: IntentAddress): Boolean {
        awaitPendingRecovery(address)
        val attempt = synchronized(lock) {
            val current = checkNotNull(pending[address])
            IntentAttempt(current.target, current.generation)
        }
        val deviceUid = DeviceUid(address.deviceUid)
        val beforeRecovery = recoveryGate?.currentInterruptionEpoch(deviceUid) ?: 0L
        val result = executeAttempt(address, attempt)
        val interruption = recoveryGate?.interruptionAfter(deviceUid, beforeRecovery)
        if (interruption != null) {
            recoveryGate.awaitRecovery(deviceUid, interruption)
            return false
        }
        val settlement = takeSettlementIfLatest(address, attempt) ?: return false
        settlement.complete(result)
        return true
    }

    private suspend fun awaitPendingRecovery(address: IntentAddress) {
        val gate = recoveryGate ?: return
        val deviceUid = DeviceUid(address.deviceUid)
        gate.pendingInterruption(deviceUid)?.let { epoch ->
            gate.awaitRecovery(deviceUid, epoch)
        }
    }

    private suspend fun executeAttempt(
        address: IntentAddress,
        attempt: IntentAttempt
    ): DeviceDosingChannelOperationResult = try {
        execute(address.deviceUid, address.slotId, attempt.target)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DeviceDosingChannelOperationResult.Failed
    }

    private fun takeSettlementIfLatest(
        address: IntentAddress,
        attempt: IntentAttempt
    ): CompletableDeferred<DeviceDosingChannelOperationResult>? = synchronized(lock) {
        val current = checkNotNull(pending[address])
        if (current.generation != attempt.generation) {
            null
        } else {
            pending.remove(address)
            current.settlement
        }
    }

    private fun completeTerminatedWorker(address: IntentAddress, failure: Throwable) {
        val settlement = synchronized(lock) { pending.remove(address) }?.settlement ?: return
        val cancellation = failure as? CancellationException
        if (cancellation == null) {
            settlement.complete(DeviceDosingChannelOperationResult.Failed)
        } else {
            settlement.cancel(cancellation)
        }
    }

    private data class IntentAddress(
        val deviceUid: String,
        val slotId: String
    )

    private data class IntentAttempt(
        val target: DeviceDosingV1ProgramAssignmentIntent,
        val generation: Long
    )

    private data class Submission(
        val settlement: CompletableDeferred<DeviceDosingChannelOperationResult>,
        val startWorker: Boolean
    )

    private class PendingIntent(initialTarget: DeviceDosingV1ProgramAssignmentIntent) {
        var target: DeviceDosingV1ProgramAssignmentIntent = initialTarget
        var generation: Long = 0L
        var running: Boolean = false
        val settlement = CompletableDeferred<DeviceDosingChannelOperationResult>()
    }
}
