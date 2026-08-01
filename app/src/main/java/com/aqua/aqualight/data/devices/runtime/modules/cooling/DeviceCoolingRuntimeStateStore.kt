package com.aqua.aqualight.data.devices.runtime.modules.cooling

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceCoolingRuntimeState(
    val status: DeviceCoolingStatus? = null,
    val config: DeviceCoolingConfigSnapshot? = null,
    val temperature: DeviceCoolingTemperatureSnapshot? = null
)

/** Device-isolated Cooling state reduced from correlated replies and typed events. */
internal class DeviceCoolingRuntimeStateStore {
    private val lock = Any()
    private val _states = MutableStateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>>(emptyMap())
    val states: StateFlow<Map<DeviceUid, DeviceCoolingRuntimeState>> = _states.asStateFlow()

    fun recordStatus(deviceUid: DeviceUid, status: DeviceCoolingStatus) {
        synchronized(lock) {
            _states.value = _states.value + (
                deviceUid to DeviceCoolingRuntimeState(
                    status = status,
                    config = status.toConfigSnapshot(),
                    temperature = status.temperature
                )
                )
        }
    }

    fun recordConfig(deviceUid: DeviceUid, result: DeviceCoolingConfigApplyResult) {
        synchronized(lock) {
            val current = _states.value[deviceUid] ?: DeviceCoolingRuntimeState()
            _states.value = _states.value + (
                deviceUid to current.copy(
                    status = current.status?.applyConfig(result.config),
                    config = result.config
                )
                )
        }
    }

    fun recordTemperature(
        deviceUid: DeviceUid,
        temperature: DeviceCoolingTemperatureSnapshot
    ): Boolean = synchronized(lock) {
        val current = _states.value[deviceUid] ?: DeviceCoolingRuntimeState()
        val fixedSensorIndex = current.status?.fixedSensorIndex ?: current.config?.fixedSensorIndex
        if (fixedSensorIndex != null && fixedSensorIndex != temperature.sensorIndex) {
            return@synchronized false
        }
        _states.value = _states.value + (
            deviceUid to current.copy(
                status = current.status?.copy(temperature = temperature),
                temperature = temperature
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

private fun DeviceCoolingStatus.toConfigSnapshot(): DeviceCoolingConfigSnapshot =
    DeviceCoolingConfigSnapshot(
        supported = supported,
        fanSupported = fanSupported,
        temperatureSupported = temperatureSupported,
        fanOutputCount = fanOutputCount,
        ruleCount = ruleCount,
        mode = mode,
        minTemperatureC = minTemperatureC,
        maxTemperatureC = maxTemperatureC,
        fixedSensorIndex = fixedSensorIndex,
        hardwareEditable = runtime.hardwareEditable,
        fanMappingEditable = runtime.fanMappingEditable,
        sensorMappingEditable = runtime.sensorMappingEditable,
        fans = fans.mapIndexed { index, fan ->
            DeviceCoolingFanConfigSnapshot(listIndex = index, fan = fan)
        },
        rules = rules.mapIndexed { index, rule ->
            DeviceCoolingRuleConfigSnapshot(
                listIndex = index,
                index = rule.index,
                name = rule.name,
                enabled = rule.enabled,
                fanIndex = rule.fanIndex,
                channelKey = rule.channelKey,
                bound = rule.bound,
                minTemperatureC = rule.minTemperatureC,
                maxTemperatureC = rule.maxTemperatureC,
                group = rule.group
            )
        }
    )

private fun DeviceCoolingStatus.applyConfig(
    config: DeviceCoolingConfigSnapshot
): DeviceCoolingStatus {
    val previousRules = rules.associateBy(DeviceCoolingRuleStatus::channelKey)
    return copy(
        supported = config.supported,
        fanSupported = config.fanSupported,
        temperatureSupported = config.temperatureSupported,
        fanOutputCount = config.fanOutputCount,
        ruleCount = config.ruleCount,
        mode = config.mode,
        minTemperatureC = config.minTemperatureC,
        maxTemperatureC = config.maxTemperatureC,
        fixedSensorIndex = config.fixedSensorIndex,
        fans = config.fans.sortedBy(DeviceCoolingFanConfigSnapshot::listIndex).map { it.fan },
        rules = config.rules.sortedBy(DeviceCoolingRuleConfigSnapshot::listIndex).map { rule ->
            DeviceCoolingRuleStatus(
                index = rule.index,
                name = rule.name,
                enabled = rule.enabled,
                fanIndex = rule.fanIndex,
                channelKey = rule.channelKey,
                bound = rule.bound,
                minTemperatureC = rule.minTemperatureC,
                maxTemperatureC = rule.maxTemperatureC,
                group = rule.group,
                sensorBindings = previousRules[rule.channelKey]?.sensorBindings
                    ?: config.fixedSensorIndex.takeIf { it >= COOLING_MIN_INDEX }?.let(::listOf)
                    ?: emptyList()
            )
        }
    )
}
