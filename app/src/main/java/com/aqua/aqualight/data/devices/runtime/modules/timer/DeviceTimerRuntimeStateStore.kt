package com.aqua.aqualight.data.devices.runtime.modules.timer

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceTimerRuntimeState(
    val status: DeviceTimerStatus? = null,
    val config: DeviceTimerConfigSnapshot? = null,
    /** Mutation replies omit schedule runtime fields; a fresh status snapshot clears this flag. */
    val requiresStatusRefresh: Boolean = false
)

/** Device-isolated Timer state reduced from correlated replies and typed events. */
internal class DeviceTimerRuntimeStateStore {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceTimerRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceTimerRuntimeState>> = _states.asStateFlow()

    fun recordStatus(deviceUid: DeviceUid, status: DeviceTimerStatus): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid]
            val currentStatus = current?.status
            if (
                currentStatus != null &&
                !isNewerTimerSample(status.uptimeMs, currentStatus.uptimeMs)
            ) {
                return@synchronized false
            }
            _states.value = _states.value + (
                deviceUid to DeviceTimerRuntimeState(
                    status = status,
                    config = status.toConfigSnapshot(),
                    requiresStatusRefresh = false
                )
                )
            true
        }

    fun recordConfig(deviceUid: DeviceUid, result: DeviceTimerConfigApplyResult) {
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceTimerRuntimeState()
            val updatedStatus = current.status?.applyConfig(result.config)
            require(current.status == null || updatedStatus != null) {
                "Timer config snapshot cannot be reconciled with current status."
            }
            _states.value = _states.value + (
                deviceUid to current.copy(
                    status = updatedStatus,
                    config = result.config,
                    requiresStatusRefresh = true
                )
                )
        }
    }

    fun recordChannel(deviceUid: DeviceUid, result: DeviceTimerChannelSetResult): Boolean =
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: return@synchronized false
            val currentStatus = current.status ?: return@synchronized false
            val updatedStatus = currentStatus.replaceChannel(result.channel)
                ?: return@synchronized false
            val baselineConfig = current.config ?: currentStatus.toConfigSnapshot()
            val updatedConfig = baselineConfig.replaceChannelRegime(result.channel)
                ?: return@synchronized false
            _states.value = _states.value + (
                deviceUid to current.copy(
                    status = updatedStatus,
                    config = updatedConfig,
                    requiresStatusRefresh = true
                )
                )
            true
        }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            if (deviceUid !in _states.value) return
            _states.value = _states.value.toMutableMap().apply { remove(deviceUid) }.toMap()
        }
    }
}

private fun DeviceTimerStatus.toConfigSnapshot(): DeviceTimerConfigSnapshot =
    DeviceTimerConfigSnapshot(
        channels = channels.mapIndexed { index, channel ->
            DeviceTimerChannelConfigSnapshot(
                listIndex = index,
                channelKey = channel.key,
                displayNameOverride = channel.displayName.takeUnless { name -> name == channel.name },
                regime = channel.regime
            )
        },
        schedules = schedules.mapIndexed { index, schedule ->
            DeviceTimerScheduleConfigSnapshot(
                listIndex = index,
                enabled = schedule.enabled,
                name = schedule.name,
                channelKey = schedule.channelKey,
                weekdays = schedule.weekdays.toList(),
                startTimeMs = schedule.startTimeMs,
                intervalOnMs = schedule.intervalOnMs,
                intervalOffMs = schedule.intervalOffMs,
                repeatCount = schedule.repeatCount
            )
        }
    )

private fun DeviceTimerStatus.replaceChannel(
    replacement: DeviceTimerChannelStatusSnapshot
): DeviceTimerStatus? {
    val current = channels.getOrNull(replacement.listIndex)
    if (current?.key != replacement.channel.key) return null
    return copy(
        channels = channels.mapIndexed { index, channel ->
            if (index == replacement.listIndex) replacement.channel else channel
        }
    )
}

private fun DeviceTimerConfigSnapshot.replaceChannelRegime(
    replacement: DeviceTimerChannelStatusSnapshot
): DeviceTimerConfigSnapshot? {
    val current = channels.getOrNull(replacement.listIndex)
    if (current?.channelKey != replacement.channel.key) return null
    return copy(
        channels = channels.mapIndexed { index, channel ->
            if (index == replacement.listIndex) {
                channel.copy(regime = replacement.channel.regime)
            } else {
                channel
            }
        }
    )
}

private fun DeviceTimerStatus.applyConfig(
    replacement: DeviceTimerConfigSnapshot
): DeviceTimerStatus? {
    val currentByKey = channels.associateBy(DeviceTimerChannelStatus::key)
    val replacedChannels = replacement.channels.mapNotNull { config ->
        currentByKey[config.channelKey]?.let { current ->
            current.copy(
                displayName = config.displayNameOverride ?: current.name,
                regime = config.regime
            )
        }
    }
    if (
        replacedChannels.size != replacement.channels.size ||
        replacedChannels.size != channels.size
    ) return null
    val channelKeys = replacedChannels.mapTo(linkedSetOf(), DeviceTimerChannelStatus::key)
    val replacedSchedules = replacement.schedules.map { config ->
        config.toStatus(channelKeys)
    }
    return copy(
        channelCount = replacedChannels.size,
        scheduleCount = replacedSchedules.size,
        lockLoop = false,
        channels = replacedChannels,
        schedules = replacedSchedules
    )
}

private fun DeviceTimerScheduleConfigSnapshot.toStatus(
    channelKeys: Set<String>
): DeviceTimerScheduleStatus {
    val isBound = channelKey in channelKeys
    return DeviceTimerScheduleStatus(
        index = listIndex,
        enabled = enabled,
        runtimeEnabled = enabled &&
            isBound &&
            weekdays.any { selected -> selected } &&
            intervalOnMs > TIMER_NON_NEGATIVE_LONG &&
            repeatCount > TIMER_MIN_COUNT,
        name = name,
        channelKey = channelKey,
        bound = isBound,
        group = TIMER_UNAVAILABLE_INDEX,
        weekdays = weekdays.toList(),
        startTimeMs = startTimeMs,
        startTime = timerTimeText(startTimeMs),
        intervalOnMs = intervalOnMs,
        intervalOn = timerTimeText(intervalOnMs),
        intervalOffMs = intervalOffMs,
        intervalOff = timerTimeText(intervalOffMs),
        repeatCount = repeatCount,
        pulseCountRuntime = TIMER_UNAVAILABLE_INDEX,
        pulseOffPending = false,
        pulseRemainingMs = TIMER_NON_NEGATIVE_LONG
    )
}
