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
    MALFORMED
}

internal enum class DeviceDosingV1InvalidationDisposition {
    APPLIED,
    STALE_CONNECTION,
    STALE_REVISION
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

    fun currentAll(deviceUid: DeviceUid): List<DeviceDosingChannelSnapshot>

    fun currentChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot?

    fun currentCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingCalibrationSnapshot?

    fun authoritativeRevision(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Long?

    /**
     * Mutation-only projection returned by the latest durable ACK.
     *
     * This continuation is deliberately separate from the last coherent presentation snapshot.
     * It may seed only replay-safe persisted assignments and is never exposed to UI reads.
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

/** Write/read authority is independent from the last coherent presentation snapshot. */
private enum class OwnedDosingChannelAuthority {
    AUTHORITATIVE,
    RECONCILING,
    CONNECTION_STALE
}

/**
 * Single-owner channel state.
 *
 * [coherentChannel]/[coherentCalibration] are produced only by a complete
 * global+channel+progress read. [committedMutation] is an internal ACK continuation and can never
 * replace those presentation snapshots. [revisionFloor] prevents stale events/readbacks from
 * moving the owner behind a durable mutation or a newer firmware publication.
 */
private data class OwnedDosingChannelState(
    val revisionFloor: Long,
    val authority: OwnedDosingChannelAuthority,
    val coherentChannel: DeviceDosingChannelSnapshot?,
    val coherentCalibration: DeviceDosingCalibrationSnapshot?,
    val committedMutation: DeviceDosingV1CommittedMutationContinuation? = null
)

private data class OwnedDosingDeviceState(
    val connectionGeneration: DeviceRuntimeConnectionGeneration,
    val global: DeviceDosingV1GlobalStatus?,
    val channels: Map<DeviceDosingV1ChannelKey, OwnedDosingChannelState>
)

/**
 * The only device/channel-scoped Dosing state owner.
 *
 * Raw replies and events may enter through different paths, but coherent snapshots enter this
 * owner only after connection generation, request ordering and revision coherence are proven.
 * Durable mutation ACKs advance the internal revision floor and continuation without projecting
 * partially coherent uptime/progress/calibration data into presentation. Same-connection event
 * reconciliation retains both the last coherent presentation and an equal/newer-safe continuation.
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
    ): DeviceDosingV1CommitDisposition = synchronized(lock) {
        val prepared = prepareDevice(token, connectionGeneration)
        if (prepared.disposition != null) return@synchronized prepared.disposition
        val device = checkNotNull(prepared.device)
        val incomingRevision = channelStatus.channel.revision
        val revisionFloor = device.channels[token.channelKey]?.revisionFloor
        if (revisionFloor != null && incomingRevision < revisionFloor) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
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
            return@synchronized DeviceDosingV1CommitDisposition.MALFORMED
        }
        val updatedChannel = OwnedDosingChannelState(
            revisionFloor = incomingRevision,
            authority = OwnedDosingChannelAuthority.AUTHORITATIVE,
            coherentChannel = mapped.channel,
            coherentCalibration = mapped.calibration,
            committedMutation = null
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
        channel: DeviceDosingV1ChannelDetail
    ): DeviceDosingV1CommitDisposition = synchronized(lock) {
        require(channel.channelKey == token.channelKey)
        val prepared = prepareDevice(token, connectionGeneration)
        if (prepared.disposition != null) return@synchronized prepared.disposition
        val device = checkNotNull(prepared.device)
        val current = device.channels[token.channelKey]
        if (current != null && channel.revision < current.revisionFloor) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
        }
        val projection = mutationProjection(
            current = current,
            deviceUid = token.deviceUid,
            channelKey = token.channelKey,
            detail = channel,
            lowLevelAlertLedger = lowLevelAlertLedger
        )
        publish(
            token.deviceUid,
            device.copy(
                channels = device.channels + (
                    token.channelKey to OwnedDosingChannelState(
                        revisionFloor = channel.revision,
                        authority = OwnedDosingChannelAuthority.RECONCILING,
                        coherentChannel = current?.coherentChannel,
                        coherentCalibration = current?.coherentCalibration,
                        committedMutation = projection?.let { projected ->
                            DeviceDosingV1CommittedMutationContinuation(
                                channel = projected.channel,
                                calibration = projected.calibration
                            )
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
        revisionHint: Long?
    ): DeviceDosingV1InvalidationDisposition = synchronized(lock) {
        val existingDevice = states.value[deviceUid]
        if (
            existingDevice != null &&
            connectionGeneration.value < existingDevice.connectionGeneration.value
        ) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_CONNECTION
        }
        val connectionAdvanced = existingDevice != null &&
            connectionGeneration.value > existingDevice.connectionGeneration.value
        val device = when {
            existingDevice == null -> emptyDevice(connectionGeneration)
            connectionAdvanced -> existingDevice.advanceConnectionGeneration(connectionGeneration)
            else -> existingDevice
        }
        val current = device.channels[channelKey]
        if (revisionHint != null && current != null && revisionHint < current.revisionFloor) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_REVISION
        }
        val nextRevisionFloor = maxOf(revisionHint ?: 0L, current?.revisionFloor ?: 0L)
        val connectionStale = connectionAdvanced ||
            current?.authority == OwnedDosingChannelAuthority.CONNECTION_STALE
        val preservedContinuation = if (connectionStale) {
            null
        } else {
            current?.committedMutation?.takeIf { continuation ->
                revisionHint == null || revisionHint <= continuation.channel.revision
            }
        }
        publish(
            deviceUid,
            device.copy(
                channels = device.channels + (
                    channelKey to OwnedDosingChannelState(
                        revisionFloor = nextRevisionFloor,
                        authority = if (connectionStale) {
                            OwnedDosingChannelAuthority.CONNECTION_STALE
                        } else {
                            OwnedDosingChannelAuthority.RECONCILING
                        },
                        coherentChannel = current?.coherentChannel,
                        coherentCalibration = current?.coherentCalibration,
                        committedMutation = preservedContinuation
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
        val updatedCoherent = current.coherentChannel?.copy(
            reservoir = current.coherentChannel.reservoir.copy(lowLevelAlertEnabled = enabled)
        )
        val updatedContinuation = current.committedMutation?.let { continuation ->
            continuation.copy(
                channel = continuation.channel.copy(
                    reservoir = continuation.channel.reservoir.copy(
                        lowLevelAlertEnabled = enabled
                    )
                )
            )
        }
        if (updatedCoherent == null && updatedContinuation == null) return@synchronized
        publish(
            deviceUid,
            device.copy(
                channels = device.channels + (
                    channelKey to current.copy(
                        coherentChannel = updatedCoherent,
                        committedMutation = updatedContinuation
                    )
                )
            )
        )
    }

    /** A socket lifecycle transition revokes current-session authority and ACK continuations. */
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
                        authority = OwnedDosingChannelAuthority.CONNECTION_STALE,
                        committedMutation = null
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

    private fun OwnedDosingDeviceState.advanceConnectionGeneration(
        generation: DeviceRuntimeConnectionGeneration
    ): OwnedDosingDeviceState = copy(
        connectionGeneration = generation,
        global = null,
        channels = channels.mapValues { (_, channel) ->
            channel.copy(
                revisionFloor = 0L,
                authority = OwnedDosingChannelAuthority.CONNECTION_STALE,
                committedMutation = null
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

    override fun currentAll(deviceUid: DeviceUid): List<DeviceDosingChannelSnapshot> =
        currentStates()[deviceUid]
            ?.channels
            ?.values
            ?.mapNotNull(OwnedDosingChannelState::authoritativeChannel)
            ?.sortedBy(DeviceDosingChannelSnapshot::channelNumber)
            .orEmpty()

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

    override fun authoritativeRevision(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Long? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.takeIf { state -> state.authority == OwnedDosingChannelAuthority.AUTHORITATIVE }
        ?.revisionFloor

    override fun committedMutationContinuation(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingV1CommittedMutationContinuation? = currentStates()[deviceUid]
        ?.channels
        ?.get(channelKey)
        ?.takeUnless { state -> state.authority == OwnedDosingChannelAuthority.CONNECTION_STALE }
        ?.committedMutation
}

private data class DosingStateAddress(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey
)

private fun OwnedDosingChannelState.presentationChannel(): DeviceDosingChannelSnapshot? =
    coherentChannel.takeUnless { authority == OwnedDosingChannelAuthority.CONNECTION_STALE }

private fun OwnedDosingChannelState.presentationCalibration(): DeviceDosingCalibrationSnapshot? =
    coherentCalibration.takeUnless { authority == OwnedDosingChannelAuthority.CONNECTION_STALE }

private fun OwnedDosingChannelState.authoritativeChannel(): DeviceDosingChannelSnapshot? =
    coherentChannel.takeIf { authority == OwnedDosingChannelAuthority.AUTHORITATIVE }

private fun OwnedDosingChannelState.authoritativeCalibration(): DeviceDosingCalibrationSnapshot? =
    coherentCalibration.takeIf { authority == OwnedDosingChannelAuthority.AUTHORITATIVE }

private fun mutationProjection(
    current: OwnedDosingChannelState?,
    deviceUid: DeviceUid,
    channelKey: DeviceDosingV1ChannelKey,
    detail: DeviceDosingV1ChannelDetail,
    lowLevelAlertLedger: DeviceDosingLowLevelAlertLedger
): DeviceDosingV1MappedSnapshots? = current?.let { state ->
    val channel = state.committedMutation?.channel ?: state.coherentChannel
    val calibration = state.committedMutation?.calibration ?: state.coherentCalibration
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
