package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Runtime source used until real device modules are connected.
 */
object NoTankDeviceRuntimeDataSource : TankDeviceRuntimeDataSource {

    override fun observeRuntimeSnapshots(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Flow<Map<Long, TankDeviceRuntimeSnapshot>> {
        return flowOf(
            emptyMap()
        )
    }
}
