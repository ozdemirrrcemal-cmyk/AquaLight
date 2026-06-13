package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import kotlinx.coroutines.flow.Flow

/**
 * Contract for optional device-specific runtime data shown inside tank cards.
 *
 * Implementations must not emit placeholder or fake values. If a device has no
 * real runtime data yet, simply omit its id from the returned map.
 */
interface TankDeviceRuntimeDataSource {

    fun observeRuntimeSnapshots(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Flow<Map<Long, TankDeviceRuntimeSnapshot>>
}
