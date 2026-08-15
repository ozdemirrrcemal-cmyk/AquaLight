package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

private data class OwnedDosingChannelState(
    val revision: Long,
    val invalidated: Boolean,
    val channel: DeviceDosingChannelSnapshot?,
    val calibration: DeviceDosingCalibrationSnapshot?
)

private data class OwnedDosingDeviceState(
    val connectionGeneration: DeviceRuntimeConnectionGeneration,
    val global: DeviceDosingV1GlobalStatus?,
    val channels: Map<DeviceDosingV1ChannelKey, OwnedDosingChannelState>
)

/**
 * The only device/channel-scoped Dosing state owner.
 *
 * Raw replies and events may enter through different paths, but application snapshots are emitted
 * only after generation, request ordering and revision coherence are all proven here.
 */
internal class DeviceDosingV1StateOwner {
    private val lock = Any()
    private val requestGenerations = HashMap<DosingStateAddress, Long>()
    private val lowLevelAlertIntents = HashMap<DosingStateAddress, Boolean>()
    private val states = MutableStateFlow<Map<DeviceUid, OwnedDosingDeviceState>>(emptyMap())

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
        val currentRevision = device.channels[token.channelKey]?.revision
        if (currentRevision != null && incomingRevision < currentRevision) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
        }
        val mapped = runCatching {
            DeviceDosingV1SnapshotMapper.map(
                deviceUid = token.deviceUid,
                slotId = DeviceDosingV1SlotKeyMapper.slotId(token.channelKey),
                global = global,
                channelStatus = channelStatus,
                progressStatus = progressStatus,
                lowLevelAlertEnabled = lowLevelAlertIntents.getOrDefault(
                    DosingStateAddress(token.deviceUid, token.channelKey),
                    false
                )
            )
        }.getOrElse {
            return@synchronized DeviceDosingV1CommitDisposition.MALFORMED
        }
        val updatedChannel = OwnedDosingChannelState(
            revision = incomingRevision,
            invalidated = false,
            channel = mapped.channel,
            calibration = mapped.calibration
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
        if (current != null && channel.revision < current.revision) {
            return@synchronized DeviceDosingV1CommitDisposition.STALE_REVISION
        }
        publish(
            token.deviceUid,
            device.copy(
                channels = device.channels + (
                    token.channelKey to OwnedDosingChannelState(
                        revision = channel.revision,
                        invalidated = true,
                        channel = current?.channel,
                        calibration = current?.calibration
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
        val device = when {
            existingDevice == null -> emptyDevice(connectionGeneration)
            connectionGeneration.value > existingDevice.connectionGeneration.value ->
                emptyDevice(connectionGeneration)
            else -> existingDevice
        }
        val current = device.channels[channelKey]
        if (revisionHint != null && current != null && revisionHint < current.revision) {
            return@synchronized DeviceDosingV1InvalidationDisposition.STALE_REVISION
        }
        val revisionFloor = maxOf(revisionHint ?: 0L, current?.revision ?: 0L)
        publish(
            deviceUid,
            device.copy(
                channels = device.channels + (
                    channelKey to OwnedDosingChannelState(
                        revision = revisionFloor,
                        invalidated = true,
                        channel = null,
                        calibration = null
                    )
                    )
            )
        )
        DeviceDosingV1InvalidationDisposition.APPLIED
    }

    fun observeChannel(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        Flow<DeviceDosingChannelSnapshot?> = states
        .map { allStates -> allStates[deviceUid]?.channels?.get(channelKey)?.authoritativeChannel() }
        .distinctUntilChanged()

    fun observeCalibration(deviceUid: DeviceUid, channelKey: DeviceDosingV1ChannelKey):
        Flow<DeviceDosingCalibrationSnapshot?> = states
        .map { allStates ->
            allStates[deviceUid]?.channels?.get(channelKey)?.authoritativeCalibration()
        }
        .distinctUntilChanged()

    fun observeAll(deviceUid: DeviceUid): Flow<List<DeviceDosingChannelSnapshot>> = states
        .map { allStates ->
            allStates[deviceUid]
                ?.channels
                .values
                .mapNotNull(OwnedDosingChannelState::authoritativeChannel)
                ?.sortedBy(DeviceDosingChannelSnapshot::channelNumber)
                .orEmpty()
        }
        .distinctUntilChanged()

    fun currentChannel(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingChannelSnapshot? = synchronized(lock) {
        states.value[deviceUid]?.channels?.get(channelKey)?.authoritativeChannel()
    }

    fun currentCalibration(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): DeviceDosingCalibrationSnapshot? = synchronized(lock) {
        states.value[deviceUid]?.channels?.get(channelKey)?.authoritativeCalibration()
    }

    fun authoritativeRevision(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey
    ): Long? = synchronized(lock) {
        states.value[deviceUid]
            ?.channels
            ?.get(channelKey)
            ?.takeUnless(OwnedDosingChannelState::invalidated)
            ?.revision
    }

    fun setLowLevelAlertIntent(
        deviceUid: DeviceUid,
        channelKey: DeviceDosingV1ChannelKey,
        enabled: Boolean
    ) = synchronized(lock) {
        lowLevelAlertIntents[DosingStateAddress(deviceUid, channelKey)] = enabled
    }

    fun clear(deviceUid: DeviceUid) = synchronized(lock) {
        if (deviceUid in states.value) {
            states.value = states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
    }

    private fun prepareDevice(
        token: DeviceDosingV1RequestToken,
        connectionGeneration: DeviceRuntimeConnectionGeneration
    ): PreparedDevice {
        val existing = states.value[token.deviceUid]
        if (
            existing != null &&
            connectionGeneration.value < existing.connectionGeneration.value
        ) {
            return PreparedDevice(DeviceDosingV1CommitDisposition.STALE_CONNECTION, null)
        }
        val newerConnection = existing != null &&
            connectionGeneration.value > existing.connectionGeneration.value
        val latestRequest = requestGenerations[DosingStateAddress(
            token.deviceUid,
            token.channelKey
        )]
        if (!newerConnection && latestRequest != token.requestGeneration) {
            return PreparedDevice(DeviceDosingV1CommitDisposition.STALE_REQUEST, null)
        }
        val device = when {
            existing == null -> emptyDevice(connectionGeneration)
            newerConnection -> emptyDevice(connectionGeneration)
            else -> existing
        }
        return PreparedDevice(null, device)
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

    private data class PreparedDevice(
        val disposition: DeviceDosingV1CommitDisposition?,
        val device: OwnedDosingDeviceState?
    )
}

private data class DosingStateAddress(
    val deviceUid: DeviceUid,
    val channelKey: DeviceDosingV1ChannelKey
)

private fun OwnedDosingChannelState.authoritativeChannel(): DeviceDosingChannelSnapshot? =
    channel.takeUnless { invalidated }

private fun OwnedDosingChannelState.authoritativeCalibration(): DeviceDosingCalibrationSnapshot? =
    calibration.takeUnless { invalidated }
