package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Device-isolated current Light state reduced from correlated replies and typed events. */
internal class DeviceLightRuntimeStateStore {
    private val lock = Any()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val temperatureProtection: StateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        > = _temperatureProtection.asStateFlow()

    fun recordStatus(deviceUid: DeviceUid, status: DeviceLightStatus) {
        synchronized(lock) {
            _statuses.value = _statuses.value + (deviceUid to status)
        }
    }

    fun recordManual(
        deviceUid: DeviceUid,
        result: DeviceLightManualMutationResult
    ): Boolean = updateStatus(deviceUid) { current ->
        val replacements = result.channels.associateBy { item -> item.channel.key }
        if (!current.channels.allKnown(replacements.keys)) return@updateStatus null
        current.copy(
            channels = current.channels.map { channel ->
                replacements[channel.key]?.channel ?: channel
            }
        )
    }

    fun recordChannelRegime(
        deviceUid: DeviceUid,
        result: DeviceLightChannelRegimeMutationResult
    ): Boolean = updateStatus(deviceUid) { current ->
        if (current.channels.none { channel -> channel.key == result.channelKey }) {
            return@updateStatus null
        }
        current.copy(
            channels = current.channels.map { channel ->
                if (channel.key == result.channelKey) result.channel.channel else channel
            }
        )
    }

    fun recordProgramApply(
        deviceUid: DeviceUid,
        result: DeviceLightProgramApplyResult
    ): Boolean = updateStatus(deviceUid) { current ->
        val nextPrograms = (
            current.programs.filterNot { program -> program.index == result.programIndex } +
                result.program
            ).sortedBy(DeviceLightProgramStatus::listIndex)
        current.copy(
            programCount = nextPrograms.size,
            programs = nextPrograms
        )
    }

    fun recordProgramDelete(
        deviceUid: DeviceUid,
        result: DeviceLightProgramDeleteResult
    ): Boolean = updateStatus(deviceUid) { current ->
        val remaining = current.programs
            .filterNot { program -> program.index == result.programIndex }
            .sortedBy(DeviceLightProgramStatus::listIndex)
        if (remaining.size != result.programCount) return@updateStatus null
        val reindexed = remaining.mapIndexed { index, program ->
            program.copy(listIndex = index, index = index)
        }
        current.copy(programCount = reindexed.size, programs = reindexed)
    }

    fun recordTemperatureProtection(
        deviceUid: DeviceUid,
        status: DeviceLightTemperatureProtectionStatus
    ) {
        synchronized(lock) {
            _temperatureProtection.value = _temperatureProtection.value + (deviceUid to status)
        }
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
        }
    }

    private fun updateStatus(
        deviceUid: DeviceUid,
        transform: (DeviceLightStatus) -> DeviceLightStatus?
    ): Boolean = synchronized(lock) {
        val current = _statuses.value[deviceUid] ?: return@synchronized false
        val updated = transform(current) ?: return@synchronized false
        _statuses.value = _statuses.value + (deviceUid to updated)
        true
    }
}

private fun List<DeviceLightChannelStatus>.allKnown(keys: Set<String>): Boolean =
    keys.all { key -> any { channel -> channel.key == key } }

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
