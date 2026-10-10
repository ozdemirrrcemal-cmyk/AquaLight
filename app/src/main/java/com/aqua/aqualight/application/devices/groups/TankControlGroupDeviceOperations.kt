package com.aqua.aqualight.application.devices.groups

import com.aqua.aqualight.application.devices.TankDeviceListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Read-only, owner- and tank-scoped source. Selection never changes tank assignments. */
interface TankControlGroupDeviceOperations {
    fun start(scope: CoroutineScope): Job
    fun observe(tankId: Long): Flow<TankControlGroupDevices>
    fun refresh()
}

/** Exact catalog identity; model includes the commercial fixture size. */
data class LightGroupCompatibility(
    val productKey: String,
    val productId: String,
    val model: String,
    val hardwareRevision: String,
    val channelCount: Int
)

data class TankControlGroupDevice(
    val device: TankDeviceListItem,
    val productLabel: String,
    val compatibility: LightGroupCompatibility?
) {
    val deviceUid: String get() = device.deviceUid
}

data class TankControlGroupDevices(
    val devices: List<TankControlGroupDevice> = emptyList(),
    val isLoading: Boolean = true
)
