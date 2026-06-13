package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

sealed interface TankDeviceSelectEvent {

    data class DeviceAssigned(
        val deviceId: Long,
        val tankId: Long
    ) : TankDeviceSelectEvent

    data object ShowAssignError : TankDeviceSelectEvent
}
