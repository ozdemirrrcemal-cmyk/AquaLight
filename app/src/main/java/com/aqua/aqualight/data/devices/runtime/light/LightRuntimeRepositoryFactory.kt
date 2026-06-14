package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.DeviceApiMode

object LightRuntimeRepositoryFactory {

    fun create(
        deviceApi: AquaLightDeviceApi
    ): LightRuntimeRepository {
        val dataSource = when (deviceApi.mode) {
            DeviceApiMode.LEGACY -> LegacyLightRuntimeDataSource(deviceApi.lightApi)
            DeviceApiMode.V1 -> V1LightRuntimeDataSource(deviceApi.lightApi)
        }

        return LightRuntimeRepository(dataSource)
    }
}
