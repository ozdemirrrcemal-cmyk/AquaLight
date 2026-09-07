package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeConnectionGeneration
import com.aqua.aqualight.data.devices.runtime.state.DeviceRuntimeGenerationAuthority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single Light runtime state owner for the legacy Light status and protection documents.
 *
 * Main Light status and temperature-protection status have independent firmware baselines and
 * therefore independent generation authority. Socket transitions revoke authority while retaining
 * the last validated projection for presentation stability.
 */
internal class DeviceLightRuntimeStateStore {
    private val lock = Any()
    private val statusAuthority = DeviceRuntimeGenerationAuthority()
    private val temperatureProtectionAuthority = DeviceRuntimeGenerationAuthority()
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightStatus>>(emptyMap())
    private val _temperatureProtection = MutableStateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        >(emptyMap())

    val statuses: StateFlow<Map<DeviceUid, DeviceLightStatus>> = _statuses.asStateFlow()
    val temperatureProtection: StateFlow<
        Map<DeviceUid, DeviceLightTemperatureProtectionStatus>
        > = _temperatureProtection.asStateFlow()

    fun beginGeneration(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ) {
        statusAuthority.beginGeneration(deviceUid, generation)
        temperatureProtectionAuthority.beginGeneration(deviceUid, generation)
    }

    fun invalidate(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration? = null
    ) {
        statusAuthority.invalidate(deviceUid, generation)
        temperatureProtectionAuthority.invalidate(deviceUid, generation)
    }

    fun isStatusAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = statusAuthority.isAuthoritative(deviceUid, generation)

    fun isTemperatureProtectionAuthoritative(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration
    ): Boolean = temperatureProtectionAuthority.isAuthoritative(deviceUid, generation)

    fun currentAuthoritativeStatus(deviceUid: DeviceUid): DeviceLightStatus? = synchronized(lock) {
        _statuses.value[deviceUid]?.takeIf { statusAuthority.isAuthoritative(deviceUid) }
    }

    fun currentAuthoritativeTemperatureProtection(
        deviceUid: DeviceUid
    ): DeviceLightTemperatureProtectionStatus? = synchronized(lock) {
        _temperatureProtection.value[deviceUid]
            ?.takeIf { temperatureProtectionAuthority.isAuthoritative(deviceUid) }
    }

    fun recordStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightStatus
    ): Boolean = synchronized(lock) {
        if (!statusAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _statuses.value = _statuses.value + (deviceUid to status)
        true
    }

    fun recordTemperatureProtection(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        status: DeviceLightTemperatureProtectionStatus
    ): Boolean = synchronized(lock) {
        if (!temperatureProtectionAuthority.acceptAuthoritativeSnapshot(deviceUid, generation)) {
            return@synchronized false
        }
        _temperatureProtection.value = _temperatureProtection.value + (deviceUid to status)
        true
    }

    /** Permanent owner cleanup only. Socket lifecycle uses [invalidate], never destructive clear. */
    fun clear(deviceUid: DeviceUid) {
        synchronized(lock) {
            _statuses.value = _statuses.value.without(deviceUid)
            _temperatureProtection.value = _temperatureProtection.value.without(deviceUid)
            statusAuthority.clear(deviceUid)
            temperatureProtectionAuthority.clear(deviceUid)
        }
    }

    internal fun updateStatus(
        deviceUid: DeviceUid,
        generation: DeviceRuntimeConnectionGeneration,
        transform: (DeviceLightStatus) -> DeviceLightStatus?
    ): Boolean = synchronized(lock) {
        if (!statusAuthority.acceptsPatch(deviceUid, generation)) return@synchronized false
        val current = _statuses.value[deviceUid] ?: return@synchronized false
        val updated = transform(current) ?: return@synchronized false
        _statuses.value = _statuses.value + (deviceUid to updated)
        true
    }
}

internal fun DeviceLightRuntimeStateStore.recordManual(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    result: DeviceLightManualMutationResult
): Boolean = updateStatus(deviceUid, generation) { current ->
    val replacements = result.channels.associateBy { item -> item.channel.key }
    if (!current.channels.allKnown(replacements.keys)) return@updateStatus null
    current.copy(
        channels = current.channels.map { channel ->
            replacements[channel.key]?.channel ?: channel
        }
    )
}

internal fun DeviceLightRuntimeStateStore.recordChannelRegime(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    result: DeviceLightChannelRegimeMutationResult
): Boolean = updateStatus(deviceUid, generation) { current ->
    if (current.channels.none { channel -> channel.key == result.channelKey }) {
        return@updateStatus null
    }
    current.copy(
        channels = current.channels.map { channel ->
            if (channel.key == result.channelKey) result.channel.channel else channel
        }
    )
}

internal fun DeviceLightRuntimeStateStore.recordProgramApply(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    result: DeviceLightProgramApplyResult
): Boolean = updateStatus(deviceUid, generation) { current ->
    val nextPrograms = (
        current.programs.filterNot { program -> program.index == result.programIndex } +
            result.program
        ).sortedBy(DeviceLightProgramStatus::listIndex)
    current.copy(
        programCount = nextPrograms.size,
        programs = nextPrograms
    )
}

internal fun DeviceLightRuntimeStateStore.recordProgramDelete(
    deviceUid: DeviceUid,
    generation: DeviceRuntimeConnectionGeneration,
    result: DeviceLightProgramDeleteResult
): Boolean = updateStatus(deviceUid, generation) { current ->
    val remaining = current.programs
        .filterNot { program -> program.index == result.programIndex }
        .sortedBy(DeviceLightProgramStatus::listIndex)
    if (remaining.size != result.programCount) return@updateStatus null
    val reindexed = remaining.mapIndexed { index, program ->
        program.copy(listIndex = index, index = index)
    }
    current.copy(programCount = reindexed.size, programs = reindexed)
}

private fun List<DeviceLightChannelStatus>.allKnown(keys: Set<String>): Boolean =
    keys.all { key -> any { channel -> channel.key == key } }

private fun <T> Map<DeviceUid, T>.without(deviceUid: DeviceUid): Map<DeviceUid, T> =
    if (deviceUid !in this) this else toMutableMap().apply { remove(deviceUid) }.toMap()
