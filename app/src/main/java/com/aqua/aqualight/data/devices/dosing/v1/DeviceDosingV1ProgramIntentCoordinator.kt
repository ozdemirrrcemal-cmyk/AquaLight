package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMutationOrigin
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
 * Every waiter completes from the final target that actually settles. Cancelling a screen waiter
 * never cancels the accepted owner-scoped worker.
 */
internal class DeviceDosingV1ProgramIntentCoordinator(
    private val scope: CoroutineScope?,
    private val execute: suspend (
        deviceUid: String,
        slotId: String,
        intent: DeviceDosingV1ProgramAssignmentIntent
    ) -> DeviceDosingChannelOperationResult
) {
    private val lock = Any()
    private val pending = HashMap<IntentAddress, PendingIntent>()

    suspend fun submit(
        deviceUid: String,
        slotId: String,
        intent: DeviceDosingV1ProgramAssignmentIntent
    ): DeviceDosingChannelOperationResult {
        val address = IntentAddress(deviceUid.trim(), slotId.trim())
        val waiter = CompletableDeferred<DeviceDosingChannelOperationResult>()
        val startWorker = synchronized(lock) {
            val current = pending.getOrPut(address) { PendingIntent(intent) }
            current.target = intent
            current.generation += 1L
            current.waiters += waiter
            if (current.running) {
                false
            } else {
                current.running = true
                true
            }
        }
        if (startWorker) {
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
        return waiter.await()
    }

    private suspend fun drive(address: IntentAddress) {
        try {
            while (!runLatestAttempt(address)) Unit
        } catch (cancellation: CancellationException) {
            synchronized(lock) { pending.remove(address) }
                ?.waiters
                ?.forEach { waiter -> waiter.cancel(cancellation) }
            throw cancellation
        }
    }

    private suspend fun runLatestAttempt(address: IntentAddress): Boolean {
        val attempt = synchronized(lock) {
            val current = checkNotNull(pending[address])
            IntentAttempt(current.target, current.generation)
        }
        val result = executeAttempt(address, attempt)
        val completed = takeWaitersIfLatest(address, attempt) ?: return false
        completed.forEach { waiter -> waiter.complete(result) }
        return true
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

    private fun takeWaitersIfLatest(
        address: IntentAddress,
        attempt: IntentAttempt
    ): List<CompletableDeferred<DeviceDosingChannelOperationResult>>? = synchronized(lock) {
        val current = checkNotNull(pending[address])
        if (current.generation != attempt.generation) {
            null
        } else {
            pending.remove(address)
            current.waiters.toList()
        }
    }

    private fun completeTerminatedWorker(address: IntentAddress, failure: Throwable) {
        val waiters = synchronized(lock) { pending.remove(address) }
            ?.waiters
            .orEmpty()
        val cancellation = failure as? CancellationException
        waiters.forEach { waiter ->
            if (cancellation == null) {
                waiter.complete(DeviceDosingChannelOperationResult.Failed)
            } else {
                waiter.cancel(cancellation)
            }
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

    private class PendingIntent(initialTarget: DeviceDosingV1ProgramAssignmentIntent) {
        var target: DeviceDosingV1ProgramAssignmentIntent = initialTarget
        var generation: Long = 0L
        var running: Boolean = false
        val waiters = mutableListOf<CompletableDeferred<DeviceDosingChannelOperationResult>>()
    }
}
