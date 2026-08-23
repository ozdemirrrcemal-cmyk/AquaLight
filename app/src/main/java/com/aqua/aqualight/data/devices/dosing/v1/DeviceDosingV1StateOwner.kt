package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.dosing.DeviceDosingLowLevelAlertLedger
import com.aqua.aqualight.data.devices.dosing.InMemoryDeviceDosingLowLevelAlertLedger
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class DeviceDosingV1RequestToken(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey,
    val requestGeneration: Long
)

internal enum class DeviceDosingV1CommitDisposition {
    APPLIED,
    STALE_CONNECTION,
    STALE_REQUEST,
    STALE_REVISION,
    STALE_RUNTIME_EVENT,
    MALFORMED
}

internal data class DeviceDosingV1RefreshCommitResult(
    val disposition: DeviceDosingV1CommitDisposition,
    val state: DeviceDosingV1AuthoritativeState? = null
)

internal enum class DeviceDosingV1InvalidationDisposition {
    APPLIED,
    STALE_CONNECTION,
    STALE_REVISION,
    DUPLICATE_EVENT
}

/** Controls whether a mutation ACK is complete enough to become stable presentation state. */
internal enum class DeviceDosingV1MutationVisibility {
    PERSISTED_ACK,
    RUNTIME_ACK
}

internal interface DeviceDosingV1StateReadAccess {
    fun observeChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Flow<DeviceDosingChannelSnapshot?>

    fun observeCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Flow<DeviceDosingCalibrationSnapshot?>

    fun observeAll(deviceUid: DeviceUid): Flow<List<DeviceDosingChannelSnapshot>>

    fun currentChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot?

    fun currentCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingCalibrationSnapshot?

    /** Coherent status or a durable persisted ACK from the current connection generation. */
    fun currentValidatedPresentationChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot?

    /** Same-revision durable continuation safe only for destination selection. */
    fun currentNavigationChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot?

    fun authoritativeRevision(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Long?

    /**
     * Full channel projection returned by the latest durable mutation ACK.
     *
     * This is deliberately unavailable to presentation and general runtime reads. It exists only
     * so the central mutation coordinator can continue a replay-safe persisted assignment at the
     * ACK revision without waiting for an unrelated progress readback.
     */
    fun committedMutationContinuation(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingV1CommittedMutationContinuation?

    fun currentAuthoritativeStateAtLeast(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        connectionGeneration: DeviceRuntimeConnectionGeneration? = null,
        revisionHint: Long? = null,
        runtimeEventSequenceHint: Long? = null
    ): DeviceDosingV1AuthoritativeState?
}

internal data class DeviceDosingV1CommittedMutationContinuation(
    val channel: DeviceDosingChannelSnapshot,
    val calibration: DeviceDosingCalibrationSnapshot
)

private enum class OwnedDosingChannelAuthority {
    AUTHORITATIVE,
    COMMITTED_MUTATION,
    INVALIDATED
}

private data class OwnedDosingChannelState(
    val revision: Long,
    val runtimeEventSequence: Long?,
    val authority: OwnedDosingChannelAuthority,
    val channel: DeviceDosingChannelSnapshot?,
    val calibration: DeviceDosingCalibrationSnapshot?,
    /** Durable persisted-ACK continuation; independent from presentation freshness. */
    val committedMutationContinuation: DeviceDosingV1CommittedMutationContinuation?
)

private data class OwnedDosingDeviceState(
    val connectionGeneration: DeviceRuntimeConnectionGeneration,
    val global: DeviceDosingV1GlobalStatus?,
    val channels: Map<DeviceDosingV1ChannelKey, OwnedDosingChannelState>
)

private object DeviceDosingV1MutationStateFactory {
    fun create(
        current: OwnedDosingChannelState?,
        token: DeviceDosingV1RequestToken,
        detail: DeviceDosingV1ChannelDetail,
        visibility: DeviceDosingV1MutationVisibility,
        lowLevelAlertLedger: DeviceDosingLowLevelAlertLedger
    ): OwnedDosingChannelState {
        // Runtime ACKs omit envelope uptime and coherent progress. Publishing them would create a
        // torn calibration snapshot; persisted ACKs contain a complete durable config projection.
        val incomingSequence = detail.lastRuntimeEvent.validSequenceOrNull()
        val persistedProjection = if (
            visibility == DeviceDosingV1MutationVisibility.PERSISTED_ACK
        ) {
            mutationProjection(
                current = current,
                deviceUid = token.deviceUid,
                channelKey = token.channelKey,
                detail = detail,
                lowLevelAlertLedger = lowLevelAlertLedger
            )
        } else {
            null
        }
        val presentation = persistedProjection?.takeIf {
            current?.runtimeEventSequence == null ||
                incomingSequence.isSameOrNewerRuntimeEventThan(current.runtimeEventSequence)
        }
        return OwnedDosingChannelState(
            revision = detail.revision,
            runtimeEventSequence = incomingSequence
                .mergeRuntimeEventSequence(current?.runtimeEventSequence),
            authority = if (presentation == null) {
                OwnedDosingChannelAuthority.INVALIDATED
            } else {
                OwnedDosingChannelAuthority.COMMITTED_MUTATION
            },
            channel = presentation?.channel ?: current?.channel,
            calibration = presentation?.calibration ?: current?.calibration,
            committedMutationContinuation = continuation(
                current = current,
                detail = detail,
                visibility = visibility,
                persistedProjection = persistedProjection
            )
        )
    }

    private fun continuation(
        current: OwnedDosingChannelState?,
        detail: DeviceDosingV1ChannelDetail,
        visibility: DeviceDosingV1MutationVisibility,
        persistedProjection: DeviceDosingV1MappedSnapshots?
    ): DeviceDosingV1CommittedMutationContinuation? = when {
        persistedProjection != null -> DeviceDosingV1CommittedMutationContinuation(
            channel = persistedProjection.channel,
            calibration = persistedProjection.calibration
        )
        visibility == DeviceDosingV1MutationVisibility.RUNTIME_ACK &&
            detail.revision == current?.revision -> current?.committedMutationContinuation
        else -> null
    }

    private fun mutationProjection(
        current: OwnedDosingChannelState?,
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        detail: DeviceDosingV1ChannelDetail,
        lowLevelAlertLedger: DeviceDosingLowLevelAlertLedger
    ): DeviceDosingV1MappedSnapshots? = current?.let { state ->
        val channel = state.channel
        val calibration = state.calibration
        if (channel == null || calibration == null) {
            null
        } else {
            runCatching {
                DeviceDosingV1SnapshotMapper.projectMutation(
                    current = DeviceDosingV1MappedSnapshots(channel, calibration),
                    detail = detail,
                    lowLevelAlertEnabled = lowLevelAlertLedger.isEnabled(
                        deviceUid.value,
                        DeviceDosingV1SlotKeyMapper.slotId(channelKey)
                    )
                )
            }.getOrNull()
        }
    }
}

private object DeviceDosingV1InvalidationPolicy {
    fun isStaleConnection(
        existing: OwnedDosingDeviceState?,
        incoming: DeviceRuntimeConnectionGeneration
    ): Boolean = existing != null && incoming.value < existing.connectionGeneration.value

    fun resolveDevice(
        existing: OwnedDosingDeviceState?,
        incoming: DeviceRuntimeConnectionGeneration
    ): OwnedDosingDeviceState = when {
        existing == null -> OwnedDosingDeviceState(incoming, null, emptyMap())
        incoming.value > existing.connectionGeneration.value -> existing.copy(
            connectionGeneration = incoming,
            global = null,
            channels = existing.channels.mapValues { (_, channel) ->
                channel.copy(
                    revision = 0L,
                    runtimeEventSequence = null,
                    authority = OwnedDosingChannelAuthority.INVALIDATED,
                    committedMutationContinuation = null
                )
            }
        )
        else -> existing
    }

    fun rejection(
        current: OwnedDosingChannelState?,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long?
    ): DeviceDosingV1InvalidationDisposition? = when {
        revisionHint != null && current != null && revisionHint < current.revision ->
            DeviceDosingV1InvalidationDisposition.STALE_REVISION
        isDuplicateEvent(current, revisionHint, runtimeEventSequenceHint) ->
            DeviceDosingV1InvalidationDisposition.DUPLICATE_EVENT
        else -> null
    }

    private fun isDuplicateEvent(
        current: OwnedDosingChannelState?,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long?
    ): Boolean = when {
        current == null -> false
        current.authority == OwnedDosingChannelAuthority.INVALIDATED -> false
        revisionHint != current.revision -> false
        runtimeEventSequenceHint == null -> false
        current.runtimeEventSequence == null -> false
        else -> !runtimeEventSequenceHint.isNewerRuntimeEventThan(current.runtimeEventSequence)
    }

    fun retainedContinuation(
        current: OwnedDosingChannelState?,
        revisionHint: Long?
    ): DeviceDosingV1CommittedMutationContinuation? = current
        ?.committedMutationContinuation
        ?.takeIf { revisionHint != null && revisionHint == current.revision }
}

/**
 * The only device/channel-scoped Dosing state owner.
 *
 * Raw replies and events may enter through different paths, but snapshots enter this owner only
 * after generation, request ordering and revision coherence are proven. Short mutation/event
 * invalidations retain the last validated snapshot for presentation stability while authoritative
 * current reads remain fail-closed until refresh. Channel alert intent is projected from the
 * dedicated owner-scoped ledger; it never becomes a second firmware state owner.
 */
internal class DeviceDosingV1StateOwner(
    private val lowLevelAlertLedger: DeviceDosingLowLevelAlertLedger =
        InMemoryDeviceDosingLowLevelAlertLedger()
) {
    private val lock = Any()
    private val requestGenerations = HashMap<DosingStateAddress, Long>()
    private val states = MutableStateFlow<Map<DeviceUid, OwnedDosingDeviceState>>(emptyMap())

    val reads: DeviceDosingV1StateReadAccess = DefaultDeviceDosingV1StateReadAccess(
        states = states,
        currentStates = { synchronized(lock) { states.value } }
    )

    fun beginRequest(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingV1RequestToken = synchronized(lock) {
        val address = DosingStateAddress(deviceUid, channelKey)
        val generation = requestGenerations.getOrDefault(address, 0L) + 1L
        requestGenerations[address] = generation
        DeviceDosingV1RequestToken(deviceUid, channelKey, generation)
    }

    fun commitRefresh(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        global: DeviceDosingV1GlobalStatus,
        channelStatus: DeviceDosingV1ChannelStatus,
        progressStatus: DeviceDosingV1ProgressStatus
    ): DeviceDosingV1CommitDisposition = commitRefreshResult(
        token = token,
        connectionGeneration = connectionGeneration,
        global = global,
        channelStatus = channelStatus,
        progressStatus = progressStatus
    ).disposition

    fun commitRefreshResult(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        global: DeviceDosingV1GlobalStatus,
        channelStatus: DeviceDosingV1ChannelStatus,
        progressStatus: DeviceDosingV1ProgressStatus
    ): DeviceDosingV1RefreshCommitResult = synchronized(lock) {
        val prepared = prepareDevice(token, connectionGeneration)
        if (prepared.disposition != null) {
            return@synchronized DeviceDosingV1RefreshCommitResult(prepared.disposition)
        }
        val device = checkNotNull(prepared.device)
        val incomingRevision = channelStatus.channel.revision
        val current = device.channels[token.channelKey]
        val currentRevision = current?.revision
        if (currentRevision != null && incomingRevision < currentRevision) {
            return@synchronized DeviceDosingV1RefreshCommitResult(
                DeviceDosingV1CommitDisposition.STALE_REVISION
            )
        }
        val incomingRuntimeEventSequence = channelStatus.channel.lastRuntimeEvent
            .validSequenceOrNull()
        if (
            current?.runtimeEventSequence != null &&
            !incomingRuntimeEventSequence.isSameOrNewerRuntimeEventThan(
                current.runtimeEventSequence
            )
        ) {
            // An event can invalidate the owner after CHANNEL_STATUS_GET was captured but before
            // PROGRESS_GET completes. Never stamp that older triplet authoritative by merely
            // merging the newer sequence floor into it.
            return@synchronized DeviceDosingV1RefreshCommitResult(
                DeviceDosingV1CommitDisposition.STALE_RUNTIME_EVENT
            )
        }
        val slotId = DeviceDosingV1SlotKeyMapper.slotId(token.channelKey)
        val mapped = runCatching {
            DeviceDosingV1SnapshotMapper.map(
                DeviceDosingV1SnapshotDocuments(
                    deviceUid = token.deviceUid,
                    slotId = slotId,
                    global = global,
                    channelStatus = channelStatus,
                    progressStatus = progressStatus,
                    lowLevelAlertEnabled = lowLevelAlertLedger.isEnabled(
                        token.deviceUid.value,
                        slotId
                    )
                )
            )
        }.getOrElse {
            return@synchronized DeviceDosingV1RefreshCommitResult(
                DeviceDosingV1CommitDisposition.MALFORMED
            )
        }
        val authoritativeState = DeviceDosingV1AuthoritativeState(
            channel = mapped.channel,
            calibration = mapped.calibration
        )
        val updatedChannel = OwnedDosingChannelState(
            revision = incomingRevision,
            runtimeEventSequence = incomingRuntimeEventSequence
                .mergeRuntimeEventSequence(current?.runtimeEventSequence),
            authority = OwnedDosingChannelAuthority.AUTHORITATIVE,
            channel = mapped.channel,
            calibration = mapped.calibration,
            committedMutationContinuation = null
        )
        publish(
            token.deviceUid,
            device.copy(
                global = global,
                channels = device.channels + (token.channelKey to updatedChannel)
            )
        )
        // Return the snapshot linearized by this commit. A StateFlow observer can synchronously
        // process a newer runtime event during publish(), but that later invalidation must not
        // retroactively turn this already-applied readback into a UI failure.
        DeviceDosingV1RefreshCommitResult(
            disposition = DeviceDosingV1CommitDisposition.APPLIED,
            state = authoritativeState
        )
    }

    fun recordMutation(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        channel: DeviceDosingV1ChannelDetail,
        visibility: DeviceDosingV1MutationVisibility
    ): DeviceDosingV1CommitDisposition = synchronized(lock) {
        require(channel.channelKey == token.channelKey)
        val prepared = prepareDevice(token, connectionGeneration)
        if (prepared.disposition != null) return@synchronized prepared.disposition
        val device = checkNotNull(prepared.device)
        val current = device.channels[token.channelKey]
        if (current != null && channel.revision < current.revision) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
        }
        val updatedChannel = DeviceDosingV1MutationStateFactory.create(
            current = current,
            token = token,
            detail = channel,
            visibility = visibility,
            lowLevelAlertLedger = lowLevelAlertLedger
        )
        publish(
            token.deviceUid,
            device.copy(
                channels = device.channels + (token.channelKey to updatedChannel)
            )
        )
        DeviceDosingV1CommitDisposition.APPLIED
    }

    fun invalidate(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long? = null
    ): DeviceDosingV1InvalidationDisposition = synchronized(lock) {
        val existingDevice = states.value[deviceUid]
        if (
            DeviceDosingV1InvalidationPolicy.isStaleConnection(
                existingDevice,
                connectionGeneration
            )
        ) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_CONNECTION
        }
        val device = DeviceDosingV1InvalidationPolicy.resolveDevice(
            existingDevice,
            connectionGeneration
        )
        val current = device.channels[channelKey]
        val rejected = DeviceDosingV1InvalidationPolicy.rejection(
            current,
            revisionHint,
            runtimeEventSequenceHint
        )
        if (rejected != null) {
            return@synchronized rejected
        }
        val revisionFloor = maxOf(revisionHint ?: 0L, current?.revision ?: 0L)
        val retainedContinuation = DeviceDosingV1InvalidationPolicy.retainedContinuation(
            current,
            revisionHint
        )
        // A valid RFC-1982 event sequence is a stronger freshness floor than a blind request-token
        // bump: a reply containing that sequence is safe even if its request began first. Legacy
        // events without a sequence still invalidate the token and force one bounded fresh read.
        if (runtimeEventSequenceHint == null) {
            val address = DosingStateAddress(deviceUid, channelKey)
            requestGenerations[address] = requestGenerations.getOrDefault(address, 0L) + 1L
        }
        publish(
            deviceUid,
            device.copy(
                channels = device.channels + (
                    channelKey to OwnedDosingChannelState(
                        revision = revisionFloor,
                        runtimeEventSequence = runtimeEventSequenceHint
                            .mergeRuntimeEventSequence(current?.runtimeEventSequence),
                        authority = OwnedDosingChannelAuthority.INVALIDATED,
                        channel = current?.channel,
                        calibration = current?.calibration,
                        committedMutationContinuation = retainedContinuation
                    )
                )
            )
        )
        DeviceDosingV1InvalidationDisposition.APPLIED
    }

    fun setLowLevelAlertIntent(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        enabled: Boolean
    ) = synchronized(lock) {
        val slotId = DeviceDosingV1SlotKeyMapper.slotId(channelKey)
        lowLevelAlertLedger.setEnabled(deviceUid.value, slotId, enabled)
        val device = states.value[deviceUid] ?: return@synchronized
        val current = device.channels[channelKey] ?: return@synchronized
        val channel = current.channel ?: return@synchronized
        publish(
            deviceUid,
            device.copy(
                channels = device.channels + (
                    channelKey to current.copy(
                        channel = channel.copy(
                            reservoir = channel.reservoir.copy(lowLevelAlertEnabled = enabled)
                        )
                    )
                )
            )
        )
    }

    /**
     * A socket lifecycle transition revokes write authority but is not a domain-state reset.
     * Presentation keeps the last fully validated firmware projection until the authenticated
     * generation refreshes it, preventing transport churn from becoming false switch/card state.
    */
    fun invalidateAll(deviceUid: DeviceUid) = synchronized(lock) {
        requestGenerations.keys
            .filter { address -> address.deviceUid == deviceUid }
            .forEach { address ->
                requestGenerations[address] = requestGenerations.getValue(address) + 1L
            }
        val device = states.value[deviceUid] ?: return@synchronized
        publish(
            deviceUid,
            device.copy(
                channels = device.channels.mapValues { (_, channel) ->
                    channel.copy(
                        authority = OwnedDosingChannelAuthority.INVALIDATED,
                        committedMutationContinuation = null
                    )
                }
            )
        )
    }

    private fun prepareDevice(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration
    ): PreparedDevice {
        val existing = states.value[token.deviceUid]
        val staleConnection = existing != null &&
            connectionGeneration.value < existing.connectionGeneration.value
        val newerConnection = existing != null &&
            connectionGeneration.value > existing.connectionGeneration.value
        val latestRequest = requestGenerations[DosingStateAddress(
            token.deviceUid,
            token.channelKey
        )]
        return when {
            staleConnection -> PreparedDevice(
                DeviceDosingV1CommitDisposition.STALE_CONNECTION,
                null
            )
            !newerConnection && latestRequest != token.requestGeneration -> PreparedDevice(
                DeviceDosingV1CommitDisposition.STALE_REQUEST,
                null
            )
            else -> PreparedDevice(
                disposition = null,
                device = when {
                    existing == null -> OwnedDosingDeviceState(
                        connectionGeneration = connectionGeneration,
                        global = null,
                        channels = emptyMap()
                    )
                    newerConnection -> existing.advanceConnectionGeneration(connectionGeneration)
                    else -> existing
                }
            )
        }
    }

    private fun publish(deviceUid: DeviceUid, state: OwnedDosingDeviceState) {
        states.value = states.value + (deviceUid to state)
    }

    /**
     * Cross a runtime connection boundary without turning a transport transition into fake UI
     * state. Old revisions and global data are not authoritative in the new session, but the last
     * validated channel/calibration projections remain safe for presentation until refresh wins.
     */
    private fun OwnedDosingDeviceState.advanceConnectionGeneration(
        generation: DeviceRuntimeConnectionGeneration
    ): OwnedDosingDeviceState = copy(
        connectionGeneration = generation,
        global = null,
        channels = channels.mapValues { (_, channel) ->
            channel.copy(
                revision = 0L,
                runtimeEventSequence = null,
                authority = OwnedDosingChannelAuthority.INVALIDATED,
                committedMutationContinuation = null
            )
        }
    )

    private data class PreparedDevice(
        val disposition: DeviceDosingV1CommitDisposition?,
        val device: OwnedDosingDeviceState?
    )
}

private class DefaultDeviceDosingV1StateReadAccess(
    private val states: StateFlow<Map<DeviceUid, OwnedDosingDeviceState>>,
    private val currentStates: () -> Map<DeviceUid, OwnedDosingDeviceState>
) : DeviceDosingV1StateReadAccess {

    override fun observeChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Flow<DeviceDosingChannelSnapshot?> = states
        .map { allStates -> allStates[deviceUid]?.channels?.get(channelKey)?.presentationChannel() }
        .distinctUntilChanged()

    override fun observeCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Flow<DeviceDosingCalibrationSnapshot?> = states
        .map { allStates ->
            allStates[deviceUid]?.channels?.get(channelKey)?.presentationCalibration()
        }
        .distinctUntilChanged()

    override fun observeAll(deviceUid: DeviceUid): Flow<List<DeviceDosingChannelSnapshot>> = states
        .map { allStates ->
            allStates[deviceUid]
                ?.channels
                ?.values
                ?.mapNotNull(OwnedDosingChannelState::presentationChannel)
                ?.sortedBy(DeviceDosingChannelSnapshot::channelNumber)
                .orEmpty()
        }
        .distinctUntilChanged()

    override fun currentChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.authoritativeChannel()

    override fun currentCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingCalibrationSnapshot? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.authoritativeCalibration()

    override fun currentValidatedPresentationChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.validatedPresentationChannel()

    override fun currentNavigationChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.navigationChannel()

    override fun authoritativeRevision(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Long? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.takeIf { state -> state.authority == OwnedDosingChannelAuthority.AUTHORITATIVE }
        ?.revision

    override fun committedMutationContinuation(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingV1CommittedMutationContinuation? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.committedMutationContinuation

    override fun currentAuthoritativeStateAtLeast(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        connectionGeneration: DeviceRuntimeConnectionGeneration?,
        revisionHint: Long?,
        runtimeEventSequenceHint: Long?
    ): DeviceDosingV1AuthoritativeState? = currentStates()[deviceUid]
        ?.takeIf { device ->
            connectionGeneration == null || device.connectionGeneration == connectionGeneration
        }
        ?.channels
        ?.get(channelKey)
        ?.takeIf { current ->
            val sequenceSatisfied = runtimeEventSequenceHint == null ||
                current.runtimeEventSequence.isSameOrNewerRuntimeEventThan(
                    runtimeEventSequenceHint
                )
            listOf(
                current.authority == OwnedDosingChannelAuthority.AUTHORITATIVE,
                current.revision >= (revisionHint ?: 0L),
                sequenceSatisfied
            ).all { satisfied -> satisfied }
        }
        ?.let { current ->
            val channel = current.channel
            val calibration = current.calibration
            if (channel == null || calibration == null) {
                null
            } else {
                DeviceDosingV1AuthoritativeState(channel, calibration)
            }
        }
}

private data class DosingStateAddress(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey
)

private fun OwnedDosingChannelState.presentationChannel(): DeviceDosingChannelSnapshot? = channel

private fun OwnedDosingChannelState.presentationCalibration(): DeviceDosingCalibrationSnapshot? = calibration

private fun OwnedDosingChannelState.authoritativeChannel(): DeviceDosingChannelSnapshot? =
    channel.takeIf { authority == OwnedDosingChannelAuthority.AUTHORITATIVE }

private fun OwnedDosingChannelState.authoritativeCalibration(): DeviceDosingCalibrationSnapshot? =
    calibration.takeIf { authority == OwnedDosingChannelAuthority.AUTHORITATIVE }

private fun OwnedDosingChannelState.validatedPresentationChannel(): DeviceDosingChannelSnapshot? =
    channel.takeIf { authority != OwnedDosingChannelAuthority.INVALIDATED }

private fun OwnedDosingChannelState.navigationChannel(): DeviceDosingChannelSnapshot? =
    validatedPresentationChannel() ?: committedMutationContinuation
        ?.channel
        ?.takeIf { continuation -> continuation.revision == revision }

private fun DeviceDosingV1RuntimeEventSnapshot.validSequenceOrNull(): Long? =
    sequence.takeIf { valid }

private fun Long?.mergeRuntimeEventSequence(previous: Long?): Long? = when {
    this == null -> previous
    previous == null || this == previous || this.isNewerRuntimeEventThan(previous) -> this
    else -> previous
}

private fun Long?.isSameOrNewerRuntimeEventThan(previous: Long): Boolean =
    this != null && (this == previous || this.isNewerRuntimeEventThan(previous))

/** RFC-1982 style comparison for the firmware's uint32 runtime-event counter. */
private fun Long.isNewerRuntimeEventThan(previous: Long): Boolean {
    val distance = (this - previous).and(UINT32_MASK)
    return distance in 1L until UINT32_HALF_RANGE
}

private const val UINT32_MASK = 0xFFFF_FFFFL
private const val UINT32_HALF_RANGE = 0x8000_0000L
