package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Combines multiple runtime providers such as light, timer, dosing and cooling.
 */
class CompositeTankDeviceRuntimeDataSource(
    private val sources: List<TankDeviceRuntimeDataSource>
) : TankDeviceRuntimeDataSource {

    override fun observeRuntimeSnapshots(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Flow<Map<Long, TankDeviceRuntimeSnapshot>> {
        if (sources.isEmpty()) {
            return flowOf(
                emptyMap()
            )
        }

        val flows =
            sources.map { source ->
                source.observeRuntimeSnapshots(
                    devices = devices
                )
            }

        return flows.reduce { mergedFlow, nextFlow ->
            combine(
                mergedFlow,
                nextFlow
            ) { mergedSnapshots, nextSnapshots ->
                mergedSnapshots + nextSnapshots
            }
        }
    }
}
