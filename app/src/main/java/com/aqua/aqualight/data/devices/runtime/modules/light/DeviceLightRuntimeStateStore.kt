package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeEventPayload
import com.aqua.aqualight.data.devices.runtime.events.DeviceRuntimeTypedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

    fun observeStatus(deviceUid: DeviceUid): Flow<DeviceLightStatus?> =
        statuses.map { states -> states[deviceUid] }.distinctUntilChanged()

    fun currentStatus(deviceUid: DeviceUid): DeviceLightStatus? = statuses.value[deviceUid]

    fun observeTemperatureProtection(
        deviceUid: DeviceUid
    ): Flow<DeviceLightTemperatureProtectionStatus?> =
        temperatureProtection.map { states -> states[deviceUid] }.distinctUntilChanged()

    fun currentTemperatureProtection(
        deviceUid: DeviceUid
    ): DeviceLightTemperatureProtectionStatus? = temperatureProtection.value[deviceUid]

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

    fun applyTypedEvent(event: DeviceRuntimeTypedEvent): DeviceLightEventApplyResult {
        if (event.type != DeviceRuntimeTypedEvent.Type.LIGHT_STATUS_CHANGED) {
            return DeviceLightEventApplyResult.Ignored
        }
        return runCatching { applyLightPayload(event.deviceUid, event.payload) }.fold(
            onSuccess = { applied ->
                if (applied) DeviceLightEventApplyResult.Applied
                else DeviceLightEventApplyResult.Ignored
            },
            onFailure = { error ->
                DeviceLightEventApplyResult.Malformed(error.message.orEmpty())
            }
        )
    }

    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
        }
    }

    private fun applyLightPayload(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload
    ): Boolean = when (payload) {
        is DeviceRuntimeEventPayload.Snapshot -> {
            recordStatus(deviceUid, DeviceLightStatusParser.parse(payload.data))
            true
        }
        is DeviceRuntimeEventPayload.CommandResult -> applyCommandResult(deviceUid, payload)
    }

    private fun applyCommandResult(
        deviceUid: DeviceUid,
        payload: DeviceRuntimeEventPayload.CommandResult
    ): Boolean {
        require(payload.commandModule == DeviceLightRuntimeContract.MODULE) {
            "Light event command module differs from the event module."
        }
        return when (payload.commandAction) {
            DeviceLightRuntimeContract.Action.MANUAL_SET -> recordManual(
                deviceUid,
                DeviceLightMutationParser.parseManual(payload.result)
            )
            DeviceLightRuntimeContract.Action.CHANNEL_REGIME_SET -> recordChannelRegime(
                deviceUid,
                DeviceLightMutationParser.parseChannelRegime(payload.result)
            )
            DeviceLightRuntimeContract.Action.PROGRAM_APPLY -> recordProgramApply(
                deviceUid,
                DeviceLightMutationParser.parseProgramApply(payload.result)
            )
            DeviceLightRuntimeContract.Action.PROGRAM_DELETE -> recordProgramDelete(
                deviceUid,
                DeviceLightMutationParser.parseProgramDelete(payload.result)
            )
            DeviceLightRuntimeContract.Action.TEMPERATURE_PROTECTION_SET -> {
                val parsed = DeviceLightTemperatureProtectionParser
                    .parseSetResult(payload.result)
                    .getOrThrow()
                recordTemperatureProtection(deviceUid, parsed.status)
                true
            }
            else -> false
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

internal sealed interface DeviceLightEventApplyResult {
    data object Applied : DeviceLightEventApplyResult
    data object Ignored : DeviceLightEventApplyResult
    data class Malformed(val reason: String) : DeviceLightEventApplyResult
}

private fun List<DeviceLightChannelStatus>.allKnown(keys: Set<String>): Boolean =
    keys.all { key -> any { channel -> channel.key == key } }

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
