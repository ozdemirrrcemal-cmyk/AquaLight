package com.aqua.aqualight.application.devices.groups

import com.aqua.aqualight.application.devices.TankDeviceListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/** Read-only projection of the existing owner/tank assignments. */
interface TankControlGroupDeviceOperations {
    fun start(scope: CoroutineScope): Job
    fun observe(tankId: Long): Flow<TankControlGroupDevices>
}

/** Exact commercial model includes fixture size; catalog determines its channel profile. */
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
    val tankName: String = "",
    val devices: List<TankControlGroupDevice> = emptyList(),
    val isLoading: Boolean = true,
    val tankExists: Boolean = true
)
