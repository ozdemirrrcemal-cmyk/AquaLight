package com.aqua.aqualight.data.devices.access

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceDefinition

sealed class DeviceOpenResult {

    data class Allowed(
        val device: DevicesDataStoreManager.DeviceInfoUi,
        val ip: String,
        val definition: AquaDeviceDefinition
    ) : DeviceOpenResult()

    data class Offline(
        val device: DevicesDataStoreManager.DeviceInfoUi
    ) : DeviceOpenResult()

    data class Unsupported(
        val device: DevicesDataStoreManager.DeviceInfoUi
    ) : DeviceOpenResult()

    object NotFound : DeviceOpenResult()
}
