package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceTimerRuntimeState(
    val connectionGeneration: DeviceRuntimeConnectionGeneration? = null,
    val authoritative: Boolean = false,
    val status: DeviceTimerStatus? = null,
    val channelDetails: Map<String, DeviceTimerStatus> = emptyMap(),
    val lastEventSequence: Long? = null,
    val requiresStatusRefresh: Boolean = false
)

/** Single generation-authoritative owner for global status, channel detail and runtime events. */
internal class DeviceTimerRuntimeStateStore {
    private val lock = Any()
    private val authority = DeviceRuntimeGenerationAuthority()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceTimerRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceTimerRuntimeState>> = _states.asStateFlow()

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = synchronized(lock) {
        val accepted = authority.beginGeneration(deviceUid, generation)
        if (accepted) {
            val current = _states.value[deviceUid]
            publish(
                deviceUid,
                (current ?: DeviceTimerRuntimeState()).copy(
                    connectionGeneration = generation,
                    authoritative = false,
                    channelDetails = emptyMap(),
                    lastEventSequence = null,
                    requiresStatusRefresh = false
                )
            )
        }
        accepted
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) = synchronized(lock) {
        authority.invalidate(deviceUid, generation)
        val current = _states.value[deviceUid] ?: return@synchronized
        if (generation == null || current.connectionGeneration == generation) {
            publish(deviceUid, current.copy(authoritative = false))
        }
    }

    fun isAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = authority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeState(deviceUid: DeviceUid): DeviceTimerRuntimeState? =
        synchronized(lock) {
            _states.value[deviceUid]?.takeIf { state ->
                state.authoritative &&
                    !state.requiresStatusRefresh &&
                    state.connectionGeneration?.let { generation ->
                        authority.isAuthoritative(deviceUid, generation)
                    } == true
            }
        }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceTimerStatus
    ): Boolean = synchronized(lock) {
        val current = _states.value[deviceUid]
        val currentStatus = current?.status
        if (authority.isAuthoritative(deviceUid, generation) && currentStatus != null) {
            val staleUptime = status.uptimeMs != currentStatus.uptimeMs &&
                !isNewerTimerCounter(status.uptimeMs, currentStatus.uptimeMs)
            val staleRevision = status.revision != currentStatus.revision &&
                !isNewerTimerCounter(status.revision, currentStatus.revision)
            if (staleUptime || staleRevision) return@synchronized false
        }
        if (!authority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }

        val baseline = current ?: DeviceTimerRuntimeState()
        val updated = if (status.channelScoped) {
            mergeChannelStatus(baseline, status)
        } else {
            replaceGlobalStatus(baseline, status)
        }
        publish(
            deviceUid,
            updated.copy(
                connectionGeneration = generation,
                authoritative = true,
                requiresStatusRefresh = false
            )
        )
        true
    }

    fun recordMutationChannel(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        channel: DeviceTimerChannelStatus,
        revision: Long
    ): Boolean = synchronized(lock) {
        if (!authority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _states.value[deviceUid] ?: return@synchronized false
        if (!current.authoritative || current.connectionGeneration != generation) {
            return@synchronized false
        }
        val status = current.status ?: return@synchronized false
        val previous = status.channels.singleOrNull { it.key == channel.key }
            ?: return@synchronized false
        if (!previous.sameTimerChannelIdentity(channel)) return@synchronized false
        val scheduleCount = status.scheduleCount - previous.scheduleCount + channel.scheduleCount
        val patchedStatus = status.copy(
            scheduleCount = scheduleCount,
            revision = revision,
            channels = status.channels.map { existing ->
                if (existing.key == channel.key) channel else existing
            }
        )
        publish(
            deviceUid,
            current.copy(
                status = patchedStatus,
                channelDetails = current.channelDetails - channel.key,
                requiresStatusRefresh = true
            )
        )
        true
    }

    @Suppress("LongMethod", "ReturnCount")
    fun recordRuntimeEvent(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        event: DeviceTimerStatusChangedEvent
    ): DeviceTimerStateEventResult = synchronized(lock) {
        if (!authority.acceptsPatch(deviceUid, generation)) {
            return@synchronized DeviceTimerStateEventResult.RefreshRequired(event.channelKey)
        }
        val current = _states.value[deviceUid]
            ?: return@synchronized DeviceTimerStateEventResult.RefreshRequired(event.channelKey)
        if (!current.authoritative || current.connectionGeneration != generation) {
            return@synchronized DeviceTimerStateEventResult.RefreshRequired(event.channelKey)
        }
        val previousSequence = current.lastEventSequence
        if (previousSequence != null) {
            if (!isNewerTimerCounter(event.change.sequence, previousSequence)) {
                return@synchronized DeviceTimerStateEventResult.Ignored
            }
            if (event.change.sequence != nextTimerSequence(previousSequence)) {
                publish(
                    deviceUid,
                    current.copy(
                        lastEventSequence = event.change.sequence,
                        requiresStatusRefresh = true
                    )
                )
                return@synchronized DeviceTimerStateEventResult.RefreshRequired(event.channelKey)
            }
        }

        val status = current.status
        val channel = status?.channels?.singleOrNull { it.key == event.channelKey }
        if (status == null || channel == null || status.revision != event.revision) {
            publish(
                deviceUid,
                current.copy(
                    lastEventSequence = event.change.sequence,
                    requiresStatusRefresh = true
                )
            )
            return@synchronized DeviceTimerStateEventResult.RefreshRequired(event.channelKey)
        }

        val patchedChannel = channel.withRuntime(event.change)
        val patchedStatus = status.copy(
            uptimeMs = newerTimerUptime(status.uptimeMs, event.publishedAtMs),
            channels = status.channels.map { existing ->
                if (existing.key == event.channelKey) patchedChannel else existing
            }
        )
        val detail = current.channelDetails[event.channelKey]
        val details = if (detail?.revision == event.revision) {
            current.channelDetails + (
                event.channelKey to detail.copy(
                    uptimeMs = newerTimerUptime(detail.uptimeMs, event.publishedAtMs),
                    channels = listOf(patchedChannel)
                )
            )
        } else {
            current.channelDetails
        }
        publish(
            deviceUid,
            current.copy(
                status = patchedStatus,
                channelDetails = details,
                lastEventSequence = event.change.sequence,
                requiresStatusRefresh = false
            )
        )
        DeviceTimerStateEventResult.Applied
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            if (deviceUid in _states.value) {
                _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
            }
            authority.clear(deviceUid)
        }
    }

    private fun publish(deviceUid: DeviceUid, state: DeviceTimerRuntimeState) {
        _states.value = _states.value + (deviceUid to state)
    }
}

private fun replaceGlobalStatus(
    current: DeviceTimerRuntimeState,
    status: DeviceTimerStatus
): DeviceTimerRuntimeState {
    val retainedDetails = current.channelDetails.filter { (channelKey, detail) ->
        val channel = status.channels.singleOrNull { it.key == channelKey }
        detail.revision == status.revision &&
            channel != null &&
            channel.scheduleCount == detail.schedules.size
    }
    return current.copy(status = status, channelDetails = retainedDetails)
}

private fun mergeChannelStatus(
    current: DeviceTimerRuntimeState,
    detail: DeviceTimerStatus
): DeviceTimerRuntimeState {
    val channelKey = requireNotNull(detail.selectedChannelKey)
    val selectedChannel = detail.channels.single()
    val currentStatus = current.status
    val mergedStatus = if (currentStatus != null && !currentStatus.channelScoped) {
        require(currentStatus.channelCount == detail.channelCount)
        require(currentStatus.maxScheduleCount == detail.maxScheduleCount)
        val previous = currentStatus.channels.singleOrNull { it.key == channelKey }
            ?: error("Timer channel-scoped status does not belong to the global snapshot.")
        require(previous.sameTimerChannelIdentity(selectedChannel))
        currentStatus.copy(
            scheduleCount = detail.scheduleCount,
            revision = detail.revision,
            lockLoop = detail.lockLoop,
            uptimeMs = detail.uptimeMs,
            channels = currentStatus.channels.map { channel ->
                if (channel.key == channelKey) selectedChannel else channel
            },
            runtime = detail.runtime
        )
    } else {
        detail
    }
    val retainedDetails = if (currentStatus?.revision == detail.revision) {
        current.channelDetails
    } else {
        emptyMap()
    }
    return current.copy(
        status = mergedStatus,
        channelDetails = retainedDetails + (channelKey to detail)
    )
}

internal sealed interface DeviceTimerStateEventResult {
    data object Applied : DeviceTimerStateEventResult
    data object Ignored : DeviceTimerStateEventResult
    data class RefreshRequired(val channelKey: String) : DeviceTimerStateEventResult
}

private fun DeviceTimerChannelStatus.withRuntime(
    change: DeviceTimerStatusChange
): DeviceTimerChannelStatus = copy(
    operatingState = change.operatingState,
    activeSlotId = change.activeSlotId,
    activeSlotName = change.activeSlotName,
    nextTransitionType = change.nextTransitionType,
    nextTransitionAt = change.nextTransitionAt,
    runtimeReason = change.runtimeReason,
    clockReady = change.clockReady,
    temporaryOverrideActive = change.temporaryOverrideActive,
    temporaryOverrideRemainingMs = change.temporaryOverrideRemainingMs
)

private fun newerTimerUptime(current: Long, candidate: Long): Long =
    if (candidate == current || isNewerTimerCounter(candidate, current)) candidate else current
