package com.aqua.aqualight.data.aquarium.devices.light

import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeDataSource
import com.aqua.aqualight.data.aquarium.devices.TankDeviceRuntimeSnapshot
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceApiFactory
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.runtime.light.LightRuntimeRepositoryFactory
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
    private val apiFactory: AquaDeviceApiFactory = AquaDeviceApiFactory(),
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS
) : TankDeviceRuntimeDataSource {

    override fun observeRuntimeSnapshots(
        devices: List<DevicesDataStoreManager.DeviceInfo>
    ): Flow<Map<Long, TankDeviceRuntimeSnapshot>> {
        val lightDevices = devices.filter { device ->
            device.category == AquaDeviceCategory.LIGHT && device.ip.isNotBlank()
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
                val deviceApi = apiFactory.create(
                    identity = device.toIdentity(),
                    connection = AquaDeviceConnection(
                        host = device.ip
                    )
                ).getOrNull() as? AquaLightDeviceApi
                    ?: return@forEach

                val repository = LightRuntimeRepositoryFactory.create(
                    deviceApi = deviceApi
                )

                val snapshot = when (val result = repository.readSnapshot(deviceApi.connection)) {
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

    private fun DevicesDataStoreManager.DeviceInfo.toIdentity(): DeviceIdentity {
        return DeviceIdentity(
            deviceId = id,
            deviceUid = deviceUid,
            macAddress = macAddress,
            serialNumber = serialNumber,
            shortId = shortId,
            productId = productId,
            productKey = productKey,
            category = category,
            productFamily = productFamily,
            productLine = productLine,
            productModel = productModel,
            displayName = displayName,
            customName = customName,
            skuId = skuId,
            skuCode = skuCode,
            hardwareRevision = hardwareRevision,
            firmwareVersion = firmwareVersion,
            firmwareBuild = firmwareBuild,
            apiVersion = apiVersion,
            protocolVersion = protocolVersion,
            supportedFeatures = supportedFeatures,
            supportedScreens = supportedScreens
        )
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 5_000L
    }
}
