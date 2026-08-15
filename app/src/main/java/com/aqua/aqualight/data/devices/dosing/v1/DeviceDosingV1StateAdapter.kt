package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeLifecycleEvent
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DeviceDosingV1AuthoritativeState(
    val channel: DeviceDosingChannelSnapshot,
    val calibration: DeviceDosingCalibrationSnapshot
)

internal sealed interface DeviceDosingV1RefreshResult {
    data class Success(val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1RefreshResult
    data class Failed(val outcome: DeviceRuntimeCommandOutcome<*>) : DeviceDosingV1RefreshResult
    data object RejectedStale : DeviceDosingV1RefreshResult
    data object Malformed : DeviceDosingV1RefreshResult
}

internal sealed interface DeviceDosingV1MutationResult<out T> {
    data class Success<T>(
        val value: T,
        val state: DeviceDosingV1AuthoritativeState
    ) : DeviceDosingV1MutationResult<T>

    data class Failed(
        val outcome: DeviceRuntimeCommandOutcome<*>
    ) : DeviceDosingV1MutationResult<Nothing>

    data class LocallyRejected(
        val reason: DeviceDosingChannelRejection
    ) : DeviceDosingV1MutationResult<Nothing>

    data object Conflict : DeviceDosingV1MutationResult<Nothing>
    data object RejectedStale : DeviceDosingV1MutationResult<Nothing>
    data object Malformed : DeviceDosingV1MutationResult<Nothing>
}

internal sealed interface DeviceDosingV1EventResult {
    data class Refreshed(val state: DeviceDosingV1AuthoritativeState) : DeviceDosingV1EventResult
    data object Ignored : DeviceDosingV1EventResult
    data object Malformed : DeviceDosingV1EventResult
    data object RefreshFailed : DeviceDosingV1EventResult
}

/**
 * Central Dosing facade over the v1 wire repository and the single state owner.
 *
 * It is deliberately not installed in production composition before Stage 12.
 */
internal class DeviceDosingV1StateAdapter(
    internal val repository: DeviceDosingV1Repository,
    private val stateOwner: DeviceDosingV1StateOwner = DeviceDosingV1StateOwner()
) {
    private val mutationLocks = ConcurrentHashMap<DosingMutationAddress, Mutex>()

    val channelOperations = DeviceDosingV1ChannelOperationsAdapter(this)
    val calibrationOperations = DeviceDosingV1CalibrationOperationsAdapter(this)

    fun observeChannel(deviceUid: String, slotId: String): Flow<DeviceDosingChannelSnapshot?> {
        val address = address(deviceUid, slotId)
        return stateOwner.observeChannel(address.deviceUid, address.channelKey)
    }

    fun observeCalibration(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> {
        val address = address(deviceUid, slotId)
        return stateOwner.observeCalibration(address.deviceUid, address.channelKey)
    }

    fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        stateOwner.observeAll(DeviceUid(deviceUid.trim()))

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingV1RefreshResult =
        refresh(address(deviceUid, slotId))

    suspend fun refreshAll(deviceUid: String): Boolean {
        val uid = DeviceUid(deviceUid.trim())
        val globalOutcome = repository.requestGlobalStatus(uid)
        if (globalOutcome !is DeviceRuntimeCommandOutcome.Success) return false
        return globalOutcome.value.channels.all { channel ->
            val address = DosingMutationAddress(uid, channel.channelKey)
            refresh(address, globalOutcome).isAuthoritative()
        }
    }

    suspend fun consume(event: DeviceRuntimeTypedEvent): DeviceDosingV1EventResult {
        if (event.type != DeviceRuntimeTypedEvent.Type.DOSING_STATUS_CHANGED) {
            return DeviceDosingV1EventResult.Ignored
        }
        val invalidation = runCatching {
            DeviceDosingV1EventParser.parseInvalidation(event.payload)
        }.getOrElse { return DeviceDosingV1EventResult.Malformed }
        return when (
            stateOwner.invalidate(
                deviceUid = event.deviceUid,
                channelKey = invalidation.channelKey,
                connectionGeneration = event.generation,
                revisionHint = invalidation.revisionHint
            )
        ) {
            DeviceDosingV1InvalidationDisposition.STALE_CONNECTION,
            DeviceDosingV1InvalidationDisposition.STALE_REVISION ->
                DeviceDosingV1EventResult.Ignored
            DeviceDosingV1InvalidationDisposition.APPLIED -> when (
                val refreshed = refresh(
                    DosingMutationAddress(event.deviceUid, invalidation.channelKey)
                )
            ) {
                is DeviceDosingV1RefreshResult.Success ->
                    DeviceDosingV1EventResult.Refreshed(refreshed.state)
                DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1EventResult.Malformed
                is DeviceDosingV1RefreshResult.Failed,
                DeviceDosingV1RefreshResult.RejectedStale ->
                    DeviceDosingV1EventResult.RefreshFailed
            }
        }
    }

    /** A reconnect or disconnect boundary must never retain a previous session's snapshots. */
    fun consume(event: DeviceRuntimeLifecycleEvent) {
        stateOwner.clear(event.deviceUid)
    }

    suspend fun <T> mutatePersisted(
        deviceUid: String,
        slotId: String,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit = {}
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = address(deviceUid, slotId),
        requiresRevision = true,
        execute = execute,
        channel = channel,
        onAccepted = onAccepted
    )

    suspend fun <T> mutateRuntime(
        deviceUid: String,
        slotId: String,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail
    ): DeviceDosingV1MutationResult<T> = mutateSerialized(
        address = address(deviceUid, slotId),
        requiresRevision = false,
        execute = execute,
        channel = channel
    )

    fun currentChannel(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? {
        val address = address(deviceUid, slotId)
        return stateOwner.currentChannel(address.deviceUid, address.channelKey)
    }

    fun currentCalibration(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? {
        val address = address(deviceUid, slotId)
        return stateOwner.currentCalibration(address.deviceUid, address.channelKey)
    }

    fun setLowLevelAlertIntent(deviceUid: String, slotId: String, enabled: Boolean) {
        val address = address(deviceUid, slotId)
        stateOwner.setLowLevelAlertIntent(address.deviceUid, address.channelKey, enabled)
    }

    private suspend fun refresh(address: DosingMutationAddress): DeviceDosingV1RefreshResult {
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        val global = repository.requestGlobalStatus(address.deviceUid)
        return refresh(address, token, global)
    }

    private suspend fun refresh(
        address: DosingMutationAddress,
        global: DeviceRuntimeCommandOutcome.Success<DeviceDosingV1GlobalStatus>
    ): DeviceDosingV1RefreshResult {
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        return refresh(address, token, global)
    }

    private suspend fun refresh(
        address: DosingMutationAddress,
        token: DeviceDosingV1RequestToken,
        globalOutcome: DeviceRuntimeCommandOutcome<DeviceDosingV1GlobalStatus>
    ): DeviceDosingV1RefreshResult {
        if (globalOutcome !is DeviceRuntimeCommandOutcome.Success) {
            return DeviceDosingV1RefreshResult.Failed(globalOutcome)
        }
        val global = globalOutcome
        val channel = repository.requestChannelStatus(address.deviceUid, address.channelKey)
        if (channel !is DeviceRuntimeCommandOutcome.Success) {
            return DeviceDosingV1RefreshResult.Failed(channel)
        }
        val progress = repository.requestProgress(address.deviceUid, address.channelKey)
        if (progress !is DeviceRuntimeCommandOutcome.Success) {
            return DeviceDosingV1RefreshResult.Failed(progress)
        }
        if (!sameConnectionGeneration(global, channel, progress)) {
            return DeviceDosingV1RefreshResult.RejectedStale
        }
        return when (
            stateOwner.commitRefresh(
                token = token,
                connectionGeneration = global.generation,
                global = global.value,
                channelStatus = channel.value,
                progressStatus = progress.value
            )
        ) {
            DeviceDosingV1CommitDisposition.APPLIED -> currentState(address)?.let {
                DeviceDosingV1RefreshResult.Success(it)
            } ?: DeviceDosingV1RefreshResult.Malformed
            DeviceDosingV1CommitDisposition.MALFORMED -> DeviceDosingV1RefreshResult.Malformed
            DeviceDosingV1CommitDisposition.STALE_CONNECTION,
            DeviceDosingV1CommitDisposition.STALE_REVISION ->
                DeviceDosingV1RefreshResult.RejectedStale
            DeviceDosingV1CommitDisposition.STALE_REQUEST -> currentState(address)?.let {
                DeviceDosingV1RefreshResult.Success(it)
            } ?: DeviceDosingV1RefreshResult.RejectedStale
        }
    }

    private suspend fun <T> mutateSerialized(
        address: DosingMutationAddress,
        requiresRevision: Boolean,
        execute: suspend (
            DeviceUid,
            DeviceDosingV1ChannelKey,
            Long,
            DeviceDosingChannelSnapshot
        ) -> DeviceRuntimeCommandOutcome<T>,
        channel: (T) -> DeviceDosingV1ChannelDetail,
        onAccepted: () -> Unit = {}
    ): DeviceDosingV1MutationResult<T> = mutationLock(address).withLock {
        val baseline = authoritativeBaseline(address)
            ?: return@withLock DeviceDosingV1MutationResult.Malformed
        val revision = if (requiresRevision) {
            stateOwner.authoritativeRevision(address.deviceUid, address.channelKey)
                ?: return@withLock DeviceDosingV1MutationResult.Malformed
        } else {
            baseline.channel.revision
        }
        val token = stateOwner.beginRequest(address.deviceUid, address.channelKey)
        val outcome = try {
            execute(address.deviceUid, address.channelKey, revision, baseline.channel)
        } catch (rejection: LocalDosingMutationRejection) {
            return@withLock DeviceDosingV1MutationResult.LocallyRejected(rejection.reason)
        } catch (_: IllegalArgumentException) {
            return@withLock DeviceDosingV1MutationResult.LocallyRejected(
                DeviceDosingChannelRejection.INVALID_DRAFT
            )
        }
        if (outcome !is DeviceRuntimeCommandOutcome.Success) {
            return@withLock failedMutation(address, outcome)
        }
        val detail = runCatching { channel(outcome.value) }.getOrElse {
            return@withLock DeviceDosingV1MutationResult.Malformed
        }
        val recorded = runCatching {
            stateOwner.recordMutation(token, outcome.generation, detail)
        }.getOrElse { return@withLock DeviceDosingV1MutationResult.Malformed }
        if (recorded == DeviceDosingV1CommitDisposition.STALE_CONNECTION) {
            refresh(address)
            return@withLock DeviceDosingV1MutationResult.RejectedStale
        }
        if (recorded == DeviceDosingV1CommitDisposition.MALFORMED) {
            return@withLock DeviceDosingV1MutationResult.Malformed
        }
        onAccepted()
        when (val refreshed = refresh(address)) {
            is DeviceDosingV1RefreshResult.Success -> DeviceDosingV1MutationResult.Success(
                value = outcome.value,
                state = refreshed.state
            )
            DeviceDosingV1RefreshResult.Malformed -> DeviceDosingV1MutationResult.Malformed
            is DeviceDosingV1RefreshResult.Failed,
            DeviceDosingV1RefreshResult.RejectedStale ->
                currentState(address)?.let { state ->
                    DeviceDosingV1MutationResult.Success(outcome.value, state)
                } ?: DeviceDosingV1MutationResult.RejectedStale
        }
    }

    private suspend fun authoritativeBaseline(
        address: DosingMutationAddress
    ): DeviceDosingV1AuthoritativeState? = currentState(address) ?: when (
        val refreshed = refresh(address)
    ) {
        is DeviceDosingV1RefreshResult.Success -> refreshed.state
        else -> null
    }

    private suspend fun <T> failedMutation(
        address: DosingMutationAddress,
        outcome: DeviceRuntimeCommandOutcome<T>
    ): DeviceDosingV1MutationResult<T> = if (outcome.isRevisionConflict()) {
        val generation = outcome.connectionGenerationOrNull()
        if (generation != null) {
            stateOwner.invalidate(
                deviceUid = address.deviceUid,
                channelKey = address.channelKey,
                connectionGeneration = generation,
                revisionHint = null
            )
        }
        refresh(address)
        DeviceDosingV1MutationResult.Conflict
    } else {
        DeviceDosingV1MutationResult.Failed(outcome)
    }

    private fun currentState(address: DosingMutationAddress): DeviceDosingV1AuthoritativeState? {
        val channel = stateOwner.currentChannel(address.deviceUid, address.channelKey) ?: return null
        val calibration = stateOwner.currentCalibration(address.deviceUid, address.channelKey)
            ?: return null
        return DeviceDosingV1AuthoritativeState(channel, calibration)
    }

    private fun mutationLock(address: DosingMutationAddress): Mutex =
        mutationLocks.computeIfAbsent(address) { Mutex() }

    private fun address(deviceUid: String, slotId: String): DosingMutationAddress =
        DosingMutationAddress(
            deviceUid = DeviceUid(deviceUid.trim()),
            channelKey = DeviceDosingV1SlotKeyMapper.channelKey(slotId.trim())
        )
}

internal class LocalDosingMutationRejection(
    val reason: DeviceDosingChannelRejection
) : IllegalStateException()

private data class DosingMutationAddress(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey
)

private fun DeviceDosingV1RefreshResult.isAuthoritative(): Boolean =
    this is DeviceDosingV1RefreshResult.Success

private fun sameConnectionGeneration(
    first: DeviceRuntimeCommandOutcome.Success<*>,
    second: DeviceRuntimeCommandOutcome.Success<*>,
    third: DeviceRuntimeCommandOutcome.Success<*>
): Boolean = first.generation == second.generation && second.generation == third.generation

private fun DeviceRuntimeCommandOutcome<*>.isRevisionConflict(): Boolean =
    this is DeviceRuntimeCommandOutcome.FirmwareError &&
        code == "INVALID_VALUE" &&
        field == "expectedRevision" &&
        message == "stale dosing channel revision"

private fun DeviceRuntimeCommandOutcome<*>.connectionGenerationOrNull():
    DeviceRuntimeConnectionGeneration? = when (this) {
        is DeviceRuntimeCommandOutcome.Success<*> -> generation
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> generation
        is DeviceRuntimeCommandOutcome.SendFailed -> generation
        is DeviceRuntimeCommandOutcome.Timeout -> generation
        is DeviceRuntimeCommandOutcome.FirmwareError -> generation
        is DeviceRuntimeCommandOutcome.ProtocolError -> generation
        is DeviceRuntimeCommandOutcome.Cancelled -> generation
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> null
    }
