package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.delay

/**
 * Authenticated runtime-bootstrap coordinator for the mandatory-RTC firmware.
 *
 * It is called only after authenticated runtime metadata has been validated. Every evaluation is
 * bound to the connection generation which produced that bootstrap, and every retry starts with a
 * fresh status read. This prevents a status result from an old socket generation from authorizing
 * a mutation on a replacement connection.
 *
 * RTC-ready devices with matching phone timezone policy are left untouched. The existing
 * phone.sync command is used only to establish RTC readiness or apply a timezone/auto-sync policy
 * change, without writing persistent storage. Only transient transport/session failures receive
 * the bounded status-first retry below; firmware, protocol and capability failures fail closed.
 */
class DeviceTimeSyncCoordinator internal constructor(
    private val requestStatus: suspend (
        DeviceUid,
        DeviceRuntimeConnectionGeneration
    ) -> DeviceRuntimeCommandOutcome<DeviceTimeStatus>?,
    private val syncPhoneNow: suspend (
        DeviceUid,
        DeviceRuntimeConnectionGeneration
    ) -> DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>?,
    private val currentConnectionGeneration: (DeviceUid) ->
        DeviceRuntimeConnectionGeneration?,
    private val currentTimeZoneSnapshot: () -> DeviceTimeZoneSnapshot =
        DeviceSystemTimePayloadFactory::currentTimeZoneSnapshot,
    private val retryDelay: suspend (Long) -> Unit = { delayMillis -> delay(delayMillis) }
) {
    private data class SyncKey(
        val deviceUid: String,
        val generation: DeviceRuntimeConnectionGeneration
    )

    private sealed interface AttemptResult {
        data class Complete(
            val decision: DeviceTimeSyncDecision
        ) : AttemptResult

        data class Retry(
            val exhaustedDecision: DeviceTimeSyncDecision
        ) : AttemptResult
    }

    private val lock = Any()
    private val syncingGenerations = mutableSetOf<SyncKey>()

    suspend fun syncPhoneNowIfNeeded(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceTimeSyncDecision {
        val key = SyncKey(deviceUid.value, generation)
        val registered = isCurrentGeneration(deviceUid, generation) && synchronized(lock) {
            syncingGenerations.add(key)
        }
        return if (!registered) {
            DeviceTimeSyncDecision.Skipped
        } else {
            try {
                evaluateWithStatusFirstRetry(deviceUid, generation)
            } finally {
                synchronized(lock) {
                    syncingGenerations -= key
                }
            }
        }
    }

    fun clearSessionMemory(deviceUid: DeviceUid) {
        synchronized(lock) {
            syncingGenerations.removeAll { key -> key.deviceUid == deviceUid.value }
        }
    }

    private suspend fun evaluateWithStatusFirstRetry(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): DeviceTimeSyncDecision {
        var attemptIndex = 0
        var decision: DeviceTimeSyncDecision? = null
        while (decision == null) {
            val attempt = if (isCurrentGeneration(deviceUid, generation)) {
                evaluateStatusAttempt(deviceUid, generation)
            } else {
                AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
            }
            when (attempt) {
                is AttemptResult.Complete -> decision = attempt.decision
                is AttemptResult.Retry -> {
                    if (!retryStatusFirst(attemptIndex, deviceUid, generation)) {
                        decision = attempt.exhaustedDecision
                    }
                }
            }
            attemptIndex += 1
        }
        return checkNotNull(decision)
    }

    private suspend fun evaluateStatusAttempt(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): AttemptResult {
        val statusOutcome = requestStatus(deviceUid, generation)
        return when {
            !isCurrentGeneration(deviceUid, generation) ->
                AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
            statusOutcome == null ->
                AttemptResult.Retry(DeviceTimeSyncDecision.Skipped)
            statusOutcome is DeviceRuntimeCommandOutcome.Success ->
                evaluateSuccessfulStatus(deviceUid, generation, statusOutcome)
            !statusOutcome.belongsTo(generation) ->
                AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
            statusOutcome.isTransientFailure() ->
                AttemptResult.Retry(DeviceTimeSyncDecision.Skipped)
            else -> AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
        }
    }

    private suspend fun evaluateSuccessfulStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        statusOutcome: DeviceRuntimeCommandOutcome.Success<DeviceTimeStatus>
    ): AttemptResult = when {
        statusOutcome.generation != generation ->
            AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
        !statusOutcome.value.requiresPhoneDiscipline(currentTimeZoneSnapshot()) ->
            AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
        !isCurrentGeneration(deviceUid, generation) ->
            AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
        else -> evaluateSyncAttempt(deviceUid, generation)
    }

    private suspend fun evaluateSyncAttempt(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): AttemptResult {
        val syncOutcome = syncPhoneNow(deviceUid, generation)
        return when {
            !isCurrentGeneration(deviceUid, generation) ->
                AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
            syncOutcome == null ->
                AttemptResult.Retry(DeviceTimeSyncDecision.Skipped)
            !syncOutcome.belongsTo(generation) ->
                AttemptResult.Complete(DeviceTimeSyncDecision.Skipped)
            syncOutcome.isTransientFailure() -> AttemptResult.Retry(
                DeviceTimeSyncDecision.Attempted(syncOutcome)
            )
            else -> AttemptResult.Complete(DeviceTimeSyncDecision.Attempted(syncOutcome))
        }
    }

    private suspend fun retryStatusFirst(
        attemptIndex: Int,
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean {
        val delayMillis = STATUS_FIRST_RETRY_DELAYS_MILLIS.getOrNull(attemptIndex)
        var retryAllowed = delayMillis != null && isCurrentGeneration(deviceUid, generation)
        if (retryAllowed) {
            retryDelay(checkNotNull(delayMillis))
            retryAllowed = isCurrentGeneration(deviceUid, generation)
        }
        return retryAllowed
    }

    private fun isCurrentGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = currentConnectionGeneration(deviceUid) == generation

    private companion object {
        val STATUS_FIRST_RETRY_DELAYS_MILLIS = listOf(250L, 750L)
    }
}

private fun DeviceTimeStatus.requiresPhoneDiscipline(
    phoneZone: DeviceTimeZoneSnapshot
): Boolean = !timeSet ||
    timezoneId != phoneZone.timezoneId ||
    posixTimeZone != phoneZone.posixTimeZone ||
    utcOffsetMinutes != phoneZone.utcOffsetMinutes ||
    !autoSyncNtpEnabled ||
    !autoSyncGadgetEnabled

private fun DeviceRuntimeCommandOutcome<*>.belongsTo(
    generation: DeviceRuntimeConnectionGeneration
): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> this.generation == generation
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> this.generation == generation
    is DeviceRuntimeCommandOutcome.SendFailed -> this.generation == generation
    is DeviceRuntimeCommandOutcome.Timeout -> this.generation == generation
    is DeviceRuntimeCommandOutcome.FirmwareError -> this.generation == generation
    is DeviceRuntimeCommandOutcome.ProtocolError -> this.generation == generation
    is DeviceRuntimeCommandOutcome.Cancelled -> this.generation == generation
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> true
}

private fun DeviceRuntimeCommandOutcome<*>.isTransientFailure(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> true
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

sealed interface DeviceTimeSyncDecision {
    /** No mutation was sent: status was current/unavailable, stale, or already in flight. */
    data object Skipped : DeviceTimeSyncDecision

    /** The existing v1 phone.sync mutation was required and attempted. */
    data class Attempted(
        val outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
    ) : DeviceTimeSyncDecision
}
