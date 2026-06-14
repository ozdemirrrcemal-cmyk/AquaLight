package com.aqua.aqualight.data.devices.runtime

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult

class DeviceCommandGateway(
    private val endpointResolver: DeviceEndpointResolver
) {

    suspend fun <T> execute(
        deviceId: Long,
        block: suspend (
            device: DevicesDataStoreManager.DeviceInfo,
            connection: AquaDeviceConnection
        ) -> ApiResult<T>
    ): ApiResult<T> {
        when (val firstEndpoint = endpointResolver.resolve(
            deviceId = deviceId,
            forceDiscovery = false
        )) {
            is ApiResult.Success -> {
                val firstResult = block(
                    firstEndpoint.value.device,
                    firstEndpoint.value.connection
                )

                if (!firstResult.shouldRediscoverAndRetry()) {
                    return firstResult
                }
            }

            is ApiResult.Error -> {
                if (!firstEndpoint.error.code.canResolveByDiscovery()) {
                    return firstEndpoint
                }
            }
        }

        return when (val refreshedEndpoint = endpointResolver.resolve(
            deviceId = deviceId,
            forceDiscovery = true
        )) {
            is ApiResult.Success -> block(
                refreshedEndpoint.value.device,
                refreshedEndpoint.value.connection
            )

            is ApiResult.Error -> refreshedEndpoint
        }
    }

    private fun <T> ApiResult<T>.shouldRediscoverAndRetry(): Boolean {
        val error = (this as? ApiResult.Error)?.error ?: return false
        return error.code.canResolveByDiscovery()
    }

    private fun ApiErrorCode.canResolveByDiscovery(): Boolean {
        return this == ApiErrorCode.TIMEOUT ||
            this == ApiErrorCode.NETWORK ||
            this == ApiErrorCode.NOT_CONNECTED
    }
}
