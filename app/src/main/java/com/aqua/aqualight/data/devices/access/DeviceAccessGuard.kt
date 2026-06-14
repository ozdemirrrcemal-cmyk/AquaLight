package com.aqua.aqualight.data.devices.access

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.runtime.DeviceEndpointResolver
import kotlinx.coroutines.flow.first

class DeviceAccessGuard(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    )
) {

    private val endpointResolver = DeviceEndpointResolver(
        context = context.applicationContext,
        devicesStore = devicesStore
    )

    suspend fun resolveForOpen(
        deviceId: Long
    ): DeviceOpenResult {
        val device = devicesStore.devicesFlow
            .first()
            .firstOrNull { savedDevice ->
                savedDevice.id == deviceId
            } ?: return DeviceOpenResult.NotFound

        val definition = AquaDeviceCatalog.findDefinition(
            productId = device.productId,
            productKey = device.productKey,
            category = device.category
        ) ?: return DeviceOpenResult.Unsupported(
            device = device
        )

        val endpoint = endpointResolver.resolve(
            deviceId = device.id,
            forceDiscovery = true
        )

        return when (endpoint) {
            is ApiResult.Success -> DeviceOpenResult.Allowed(
                device = endpoint.value.device,
                definition = definition
            )

            is ApiResult.Error -> DeviceOpenResult.Offline(
                device = device
            )
        }
    }
}
