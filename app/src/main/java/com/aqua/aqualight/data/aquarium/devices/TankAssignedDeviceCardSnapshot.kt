package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.card.DeviceCardUiState

/**
 * Single data-layer snapshot for a device assigned to a tank.
 *
 * The commonCard field contains screen-independent metadata and connection
 * state. The runtime field is optional and is filled only by a registered
 * device-specific runtime data source. No runtime source is registered by
 * default, so no fake live values are produced.
 */
data class TankAssignedDeviceCardSnapshot(
    val device: DevicesDataStoreManager.DeviceInfo,
    val commonCard: DeviceCardUiState,
    val runtime: TankDeviceRuntimeSnapshot? = null
)
