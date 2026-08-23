package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.base.diagnostics.AppDiagnosticTrace
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
        AppDiagnosticTrace.event(
            DOSING_STATE_CATEGORY,
            "request_started",
            "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
            "slot" to channelKey.value,
            "requestGeneration" to generation
        )
        DeviceDosingV1RequestToken(deviceUid, channelKey, generation)
    }

    fun commitRefresh(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration,
        global: DeviceDosingV1GlobalStatus,
        channelStatus: DeviceDosingV1ChannelStatus,
        progressStatus: DeviceDosingV1ProgressStatus
    ): DeviceDosingV1CommitDisposition = synchronized(lock) {
        val prepared = prepareDevice(token, connectionGeneration)
        if (prepared.disposition != null) return@synchronized prepared.disposition
        val device = checkNotNull(prepared.device)
        val incomingRevision = channelStatus.channel.revision
        val current = device.channels[token.channelKey]
        val currentRevision = current?.revision
        if (currentRevision != null && incomingRevision < currentRevision) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
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
            return@synchronized DeviceDosingV1CommitDisposition.STALE_RUNTIME_EVENT
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
        }.getOrElse { return@synchronized DeviceDosingV1CommitDisposition.MALFORMED }
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
        DeviceDosingV1CommitDisposition.APPLIED
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
        // Runtime ACKs do not carry the envelope uptime or a coherent progress document. Publishing
        // them would create a torn calibration/runtime snapshot. Persisted ACKs contain the durable
        // configuration domain and may be presented while their progress readback catches up.
        val incomingRuntimeEventSequence = channel.lastRuntimeEvent.validSequenceOrNull()
        val persistedProjection = if (
            visibility == DeviceDosingV1MutationVisibility.PERSISTED_ACK
        ) {
            mutationProjection(
                current = current,
                deviceUid = token.deviceUid,
                channelKey = token.channelKey,
                detail = channel,
                lowLevelAlertLedger = lowLevelAlertLedger
            )
        } else {
            null
        }
        val presentationProjection = persistedProjection?.takeIf {
            current?.runtimeEventSequence == null ||
                incomingRuntimeEventSequence.isSameOrNewerRuntimeEventThan(
                    current.runtimeEventSequence
                )
        }
        publish(
            token.deviceUid,
            device.copy(
                channels = device.channels + (
                    token.channelKey to OwnedDosingChannelState(
                        revision = channel.revision,
                        runtimeEventSequence = incomingRuntimeEventSequence
                            .mergeRuntimeEventSequence(current?.runtimeEventSequence),
                        authority = if (presentationProjection == null) {
                            OwnedDosingChannelAuthority.INVALIDATED
                        } else {
                            OwnedDosingChannelAuthority.COMMITTED_MUTATION
                        },
                        channel = presentationProjection?.channel ?: current?.channel,
                        calibration = presentationProjection?.calibration ?: current?.calibration,
                        committedMutationContinuation = when {
                            persistedProjection != null -> DeviceDosingV1CommittedMutationContinuation(
                                channel = persistedProjection.channel,
                                calibration = persistedProjection.calibration
                            )
                            visibility == DeviceDosingV1MutationVisibility.RUNTIME_ACK &&
                                channel.revision == current?.revision ->
                                current?.committedMutationContinuation
                            else -> null
                        }
                    )
                )
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
            existingDevice != null &&
            connectionGeneration.value < existingDevice.connectionGeneration.value
        ) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_CONNECTION
        }
        val device = when {
            existingDevice == null -> emptyDevice(connectionGeneration)
            connectionGeneration.value > existingDevice.connectionGeneration.value ->
                existingDevice.advanceConnectionGeneration(connectionGeneration)
            else -> existingDevice
        }
        val current = device.channels[channelKey]
        if (revisionHint != null && current != null && revisionHint < current.revision) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_REVISION
        }
        if (
            current != null &&
            current.authority != OwnedDosingChannelAuthority.INVALIDATED &&
            revisionHint == current.revision &&
            runtimeEventSequenceHint != null &&
            current.runtimeEventSequence != null &&
            !runtimeEventSequenceHint.isNewerRuntimeEventThan(current.runtimeEventSequence)
        ) {
            // The ACK/status already contains this exact firmware event. Do not downgrade a usable
            // current-generation snapshot or erase persisted mutation continuation.
            return@synchronized DeviceDosingV1InvalidationDisposition.DUPLICATE_EVENT
        }
        val revisionFloor = maxOf(revisionHint ?: 0L, current?.revision ?: 0L)
        val retainedContinuation = current?.committedMutationContinuation?.takeIf {
            revisionHint != null && revisionHint == current.revision
        }
        val address = DosingStateAddress(deviceUid, channelKey)
        requestGenerations[address] = requestGenerations.getOrDefault(address, 0L) + 1L
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
        val currentDevice = states.value[deviceUid]
        AppDiagnosticTrace.event(
            DOSING_STATE_CATEGORY,
            "invalidate_all_started",
            "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
            "generation" to currentDevice?.connectionGeneration?.value,
            "slotCount" to currentDevice?.channels?.size
        )
        requestGenerations.keys
            .filter { address -> address.deviceUid == deviceUid }
            .forEach { address ->
                requestGenerations[address] = requestGenerations.getValue(address) + 1L
            }
        val device = currentDevice ?: return@synchronized
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
        AppDiagnosticTrace.event(
            DOSING_STATE_CATEGORY,
            "invalidate_all_applied",
            "device" to AppDiagnosticTrace.deviceRef(deviceUid.value),
            "generation" to device.connectionGeneration.value,
            "slotCount" to device.channels.size
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
                    existing == null -> emptyDevice(connectionGeneration)
                    newerConnection -> existing.advanceConnectionGeneration(connectionGeneration)
                    else -> existing
                }
            )
        }
    }

    private fun publish(deviceUid: DeviceUid, state: OwnedDosingDeviceState) {
        states.value = states.value + (deviceUid to state)
    }

    private fun emptyDevice(
        generation: DeviceRuntimeConnectionGeneration
    ): OwnedDosingDeviceState = OwnedDosingDeviceState(
        connectionGeneration = generation,
        global = null,
        channels = emptyMap()
    )

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

private const val UINT32_MASK = 0xFFFF_FFFFL
private const val UINT32_HALF_RANGE = 0x8000_0000L
private const val DOSING_STATE_CATEGORY = "dosing_state"
