package com.aqua.aqualight.data.aquarium.devices.light

import android.content.Context
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeDataSource
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeSnapshot
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeDeviceAccessor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Optional tank-detail runtime source for Light devices.
 *
 * This class is intentionally not registered as the default source yet. When we
 * connect live data to TankDetail, TankAssignedDevicesRepository can receive
 * this source (or a CompositeTankDeviceRuntimeDataSource containing it) without
 * changing UI fragments.
 */
class LightTankDeviceRuntimeDataSource(
    context: Context,
    private val runtimeAccessor: LightRuntimeDeviceAccessor = LightRuntimeDeviceAccessor(
        context = context.applicationContext
    ),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS
) : TankDeviceRuntimeDataSource {

    override fun observeRuntimeSnapshots(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Flow<Map<Long, TankDeviceRuntimeSnapshot>> {
        val lightDevices = devices.filter { device ->
            device.category == AquaDeviceCategory.LIGHT
        }

        return flow {
            if (lightDevices.isEmpty()) {
                emit(emptyMap())
                return@flow
            }

            while (currentCoroutineContext().isActive) {
                emit(readRuntimeMap(lightDevices))
                delay(pollIntervalMillis)
            }
        }
    }

    private suspend fun readRuntimeMap(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Map<Long, TankDeviceRuntimeSnapshot> {
        return buildMap {
            devices.forEach { device ->
                val snapshot = when (val result = runtimeAccessor.readSnapshot(device.id)) {
                    is ApiResult.Success -> result.value
                    is ApiResult.Error -> null
                } ?: return@forEach

                put(
                    device.id,
                    LightTankRuntimeMapper.map(
                        deviceId = device.id,
                        snapshot = snapshot
                    )
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}
