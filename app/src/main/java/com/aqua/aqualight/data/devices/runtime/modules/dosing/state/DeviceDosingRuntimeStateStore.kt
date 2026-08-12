package com.aqua.aqualight.data.devices.runtime.modules.dosing

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceDosingRuntimeState(
    val status: DeviceDosingStatus? = null,
    val config: DeviceDosingConfigSnapshot? = null,
    val lastMutation: DeviceDosingMutationResult? = null,
    /** Mutation replies omit some runtime fields; a fresh status snapshot clears this flag. */
    val requiresStatusRefresh: Boolean = false
)

/** Device-isolated Dosing state reduced from correlated replies and typed events. */
internal class DeviceDosingRuntimeStateStore {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceDosingRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceDosingRuntimeState>> = _states.asStateFlow()

    fun recordStatus(deviceUid: DeviceUid, status: DeviceDosingStatus): Boolean =
        synchronized(lock) {
            val currentStatus = _states.value[deviceUid]?.status
            if (
                currentStatus != null &&
                !isNewerDosingSample(status.uptimeMs, currentStatus.uptimeMs)
            ) {
                return@synchronized false
            }
            _states.value = _states.value + (
                deviceUid to DeviceDosingRuntimeState(
                    status = status,
                    config = status.toConfigSnapshot(),
                    lastMutation = null,
                    requiresStatusRefresh = false
                )
                )
            true
        }

    fun recordMutation(
        deviceUid: DeviceUid,
        result: DeviceDosingMutationResult
    ): Boolean = synchronized(lock) {
        val current = _states.value[deviceUid] ?: DeviceDosingRuntimeState()
        val updated = when (result) {
            is DeviceDosingConfigApplyResult -> current.withConfig(result)
            is DeviceDosingCalibrationStartResult -> current.withMutationOnly(result)
            is DeviceDosingPumpCommandResult -> current.withChannel(result, result.channel)
            is DeviceDosingDoseNowResult -> current.withChannel(result, result.channel)
            is DeviceDosingCalibrationFinishResult -> current.withChannel(result, result.channel)
            is DeviceDosingCalibrationConfirmResult -> current.withConfirmedCalibration(result)
            is DeviceDosingCalibrationCancelResult -> current.withChannel(result, result.channel)
            is DeviceDosingReservoirRefillResult -> current.withChannel(result, result.channel)
        } ?: return@synchronized false
        _states.value = _states.value + (deviceUid to updated)
        true
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            if (deviceUid !in _states.value) return
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
    }
}

private fun DeviceDosingRuntimeState.withConfig(
    result: DeviceDosingConfigApplyResult
): DeviceDosingRuntimeState? {
    val updatedStatus = status?.applyConfig(result.config)
    if (status != null && updatedStatus == null) return null
    return copy(
        status = updatedStatus,
        config = result.config,
        lastMutation = result,
        requiresStatusRefresh = true
    )
}

private fun DeviceDosingRuntimeState.withMutationOnly(
    result: DeviceDosingCalibrationStartResult
): DeviceDosingRuntimeState? {
    val knownChannel = status?.channels?.any { channel -> channel.key == result.channelKey } == true
    return if (knownChannel) {
        copy(lastMutation = result, requiresStatusRefresh = true)
    } else {
        null
    }
}

private fun DeviceDosingRuntimeState.withChannel(
    result: DeviceDosingMutationResult,
    replacement: DeviceDosingChannelStatusSnapshot
): DeviceDosingRuntimeState? {
    val updatedStatus = status?.replaceChannel(replacement) ?: return null
    return copy(
        status = updatedStatus,
        lastMutation = result,
        requiresStatusRefresh = true
    )
}

private fun DeviceDosingRuntimeState.withConfirmedCalibration(
    result: DeviceDosingCalibrationConfirmResult
): DeviceDosingRuntimeState? {
    val channelUpdated = withChannel(result, result.channel) ?: return null
    val baselineConfig = config ?: status?.toConfigSnapshot()
    return baselineConfig
        ?.replaceCalibration(result.channel)
        ?.let { updatedConfig -> channelUpdated.copy(config = updatedConfig) }
        ?: channelUpdated.takeIf { baselineConfig == null }
}

private fun DeviceDosingStatus.toConfigSnapshot(): DeviceDosingConfigSnapshot =
    DeviceDosingConfigSnapshot(
        channels = channels.mapIndexed { index, channel ->
            DeviceDosingChannelConfigSnapshot(
                listIndex = index,
                channelKey = channel.key,
                displayNameOverride = channel.displayName.takeUnless { name -> name == channel.name },
                regime = channel.regime,
                dosing = DeviceDosingChannelDosingConfigSnapshot(
                    doseMsPerMl = channel.dosing.doseMsPerMl,
                    lastCalibratedAt = channel.dosing.lastCalibratedAt,
                    reservoirTrackingEnabled = channel.dosing.reservoirTrackingEnabled,
                    reservoirCapacityMl = channel.dosing.reservoirCapacityMl
                )
            )
        },
        schedules = schedules.mapIndexed { index, schedule ->
            DeviceDosingScheduleConfigSnapshot(
                listIndex = index,
                enabled = schedule.enabled,
                name = schedule.name,
                channelKey = schedule.channelKey,
                weekdays = schedule.weekdays.toList(),
                startTimeMs = schedule.startTimeMs,
                intervalOnMs = schedule.intervalOnMs,
                intervalOffMs = schedule.intervalOffMs,
                repeatCount = schedule.repeatCount,
                amountMl = schedule.amountMl
            )
        }
    )

private fun DeviceDosingStatus.replaceChannel(
    replacement: DeviceDosingChannelStatusSnapshot
): DeviceDosingStatus? {
    val current = channels.getOrNull(replacement.listIndex)
    if (current?.key != replacement.channel.key) return null
    return copy(
        channels = channels.mapIndexed { index, channel ->
            if (index == replacement.listIndex) replacement.channel else channel
        }
    )
}

private fun DeviceDosingConfigSnapshot.replaceCalibration(
    replacement: DeviceDosingChannelStatusSnapshot
): DeviceDosingConfigSnapshot? {
    val current = channels.getOrNull(replacement.listIndex)
    if (current?.channelKey != replacement.channel.key) return null
    return copy(
        channels = channels.mapIndexed { index, channel ->
            if (index == replacement.listIndex) {
                channel.copy(
                    dosing = channel.dosing.copy(
                        doseMsPerMl = replacement.channel.dosing.doseMsPerMl,
                        lastCalibratedAt = replacement.channel.dosing.lastCalibratedAt
                    )
                )
            } else {
                channel
            }
        }
    )
}

private fun DeviceDosingStatus.applyConfig(
    replacement: DeviceDosingConfigSnapshot
): DeviceDosingStatus? {
    val currentByKey = channels.associateBy(DeviceDosingChannelStatus::key)
    val replacedChannels = replacement.channels.mapNotNull { config ->
        currentByKey[config.channelKey]?.applyConfig(config)
    }
    if (
        replacedChannels.size != replacement.channels.size ||
        replacedChannels.size != channels.size
    ) return null
    val channelsByKey = replacedChannels.associateBy(DeviceDosingChannelStatus::key)
    val replacedSchedules = replacement.schedules.map { config ->
        config.toStatus(channelsByKey)
    }
    return copy(
        channelCount = replacedChannels.size,
        scheduleCount = replacedSchedules.size,
        lockLoop = false,
        channels = replacedChannels,
        schedules = replacedSchedules
    )
}

private fun DeviceDosingChannelStatus.applyConfig(
    config: DeviceDosingChannelConfigSnapshot
): DeviceDosingChannelStatus {
    val remainingMl = when {
        !config.dosing.reservoirTrackingEnabled -> dosing.reservoirRemainingMl
        !dosing.reservoirTrackingEnabled || dosing.reservoirRemainingMl < 0.0 ->
            config.dosing.reservoirCapacityMl
        else -> dosing.reservoirRemainingMl.coerceAtMost(config.dosing.reservoirCapacityMl)
    }
    val remainingPercent = if (
        config.dosing.reservoirTrackingEnabled &&
        config.dosing.reservoirCapacityMl > 0.0 &&
        remainingMl >= 0.0
    ) {
        remainingMl / config.dosing.reservoirCapacityMl * DOSING_PERCENT_MAX
    } else {
        DOSING_UNSET_RESERVOIR
    }
    return copy(
        displayName = config.displayNameOverride ?: name,
        regime = config.regime,
        dosing = dosing.copy(
            doseMsPerMl = config.dosing.doseMsPerMl,
            lastCalibratedAt = config.dosing.lastCalibratedAt,
            calibrated = config.dosing.doseMsPerMl > 0L &&
                config.dosing.lastCalibratedAt > 0L,
            reservoirTrackingEnabled = config.dosing.reservoirTrackingEnabled,
            reservoirCapacityMl = config.dosing.reservoirCapacityMl,
            reservoirRemainingMl = remainingMl,
            reservoirRemainingPercent = remainingPercent
        )
    )
}

private fun DeviceDosingScheduleConfigSnapshot.toStatus(
    channelsByKey: Map<String, DeviceDosingChannelStatus>
): DeviceDosingScheduleStatus {
    val channel = channelsByKey[channelKey]
    val isBound = channel != null
    val durationReady = channel?.let { item ->
        !(item.dosing.doseMsPerMl <= 1L && intervalOnMs < 1L) &&
            !(item.dosing.doseMsPerMl >= 1L && amountMl <= 0.0)
    } ?: false
    return DeviceDosingScheduleStatus(
        index = listIndex,
        enabled = enabled,
        runtimeEnabled = enabled &&
            isBound &&
            channel?.dosing?.calibrated == true &&
            weekdays.any { selected -> selected } &&
            repeatCount > 0 &&
            durationReady,
        name = name,
        channelKey = channelKey,
        bound = isBound,
        group = DOSING_UNAVAILABLE_INDEX,
        weekdays = weekdays.toList(),
        startTimeMs = startTimeMs,
        startTime = dosingTimeText(startTimeMs),
        intervalOnMs = intervalOnMs,
        intervalOn = dosingTimeText(intervalOnMs),
        intervalOffMs = intervalOffMs,
        intervalOff = dosingTimeText(intervalOffMs),
        repeatCount = repeatCount,
        amountMl = amountMl,
        pulseCountRuntime = DOSING_UNAVAILABLE_INDEX,
        pulseOffPending = false,
        pulseRemainingMs = DOSING_NON_NEGATIVE_LONG
    )
}
