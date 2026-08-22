package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Central latest-intent queue for the persisted missed-dose recovery assignment.
 *
 * Every caller for one channel joins the same owner-scoped worker. If intent changes while a
 * firmware write is in flight, the intermediate result is never surfaced as the final outcome;
 * the worker continues from the ACK revision until the latest requested value settles.
 */
internal class DeviceDosingV1MissedDoseRecoveryIntentCoordinator(
    private val scope: CoroutineScope?,
    private val execute: suspend (
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ) -> DeviceDosingChannelOperationResult
) {
    private val lock = Any()
    private val pending = HashMap<IntentAddress, PendingIntent>()

    suspend fun submit(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        val address = IntentAddress(deviceUid.trim(), slotId.trim())
        val waiter = CompletableDeferred<DeviceDosingChannelOperationResult>()
        val startWorker = synchronized(lock) {
            val intent = pending.getOrPut(address) { PendingIntent(enabled) }
            intent.targetEnabled = enabled
            intent.generation += 1L
            intent.waiters += waiter
            if (intent.running) {
                false
            } else {
                intent.running = true
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
            val intent = checkNotNull(pending[address])
            IntentAttempt(intent.targetEnabled, intent.generation)
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
        execute(address.deviceUid, address.slotId, attempt.targetEnabled)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DeviceDosingChannelOperationResult.Failed
    }

    private fun takeWaitersIfLatest(
        address: IntentAddress,
        attempt: IntentAttempt
    ): List<CompletableDeferred<DeviceDosingChannelOperationResult>>? = synchronized(lock) {
        val intent = checkNotNull(pending[address])
        if (intent.generation != attempt.generation) {
            null
        } else {
            pending.remove(address)
            intent.waiters.toList()
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
        val targetEnabled: Boolean,
        val generation: Long
    )

    private class PendingIntent(initialTargetEnabled: Boolean) {
        var targetEnabled: Boolean = initialTargetEnabled
        var generation: Long = 0L
        var running: Boolean = false
        val waiters = mutableListOf<CompletableDeferred<DeviceDosingChannelOperationResult>>()
    }
}
