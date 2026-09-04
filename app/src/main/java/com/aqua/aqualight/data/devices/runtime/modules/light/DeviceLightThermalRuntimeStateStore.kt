package com.aqua.aqualight.data.devices.runtime.modules.light

import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class DeviceLightThermalRuntimeStateStore {
    private val _statuses = MutableStateFlow<Map<DeviceUid, DeviceLightThermalStatus>>(emptyMap())
    val statuses: StateFlow<Map<DeviceUid, DeviceLightThermalStatus>> = _statuses.asStateFlow()

    fun recordStatus(deviceUid: DeviceUid, status: DeviceLightThermalStatus) {
        _statuses.update { current -> current + (deviceUid to status) }
    }

    fun clear(deviceUid: DeviceUid) {
        _statuses.update { current -> current - deviceUid }
    }

    fun clearAll() {
        _statuses.value = emptyMap()
    }
}
