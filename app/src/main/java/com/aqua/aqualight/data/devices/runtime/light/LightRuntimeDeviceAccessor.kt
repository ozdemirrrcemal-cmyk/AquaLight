package com.aqua.aqualight.data.devices.runtime.light

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceApiFactory
import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.runtime.DeviceCommandGateway
import com.aqua.aqualight.data.devices.runtime.DeviceEndpointResolver

/**
 * Single entry point for reading Light runtime from a stored Aqua device.
 *
 * UI code passes only the local deviceId. IP is resolved here through the
 * central gateway. If the cached IP fails, the gateway rediscoveres the same
 * physical device by identity, updates DataStore, and retries once.
 */
class LightRuntimeDeviceAccessor(
    context: Context,
    private val apiFactory: AquaDeviceApiFactory = AquaDeviceApiFactory(),
    private val commandGateway: DeviceCommandGateway = DeviceCommandGateway(
        endpointResolver = DeviceEndpointResolver(
            context = context.applicationContext
        )
    )
) {

    suspend fun readSnapshot(
        deviceId: Long
    ): ApiResult<LightRuntimeSnapshot> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        return commandGateway.execute(
            deviceId = deviceId
        ) { device, connection ->
            if (device.category != AquaDeviceCategory.LIGHT) {
                return@execute ApiResult.failure(
                    code = ApiErrorCode.UNSUPPORTED_DEVICE,
                    message = "Stored device is not a Light controller"
                )
            }

            val deviceApi = when (val apiResult = apiFactory.create(
                identity = device.toIdentity(),
                connection = connection
            )) {
                is ApiResult.Success -> apiResult.value as? AquaLightDeviceApi
                    ?: return@execute ApiResult.failure(
                        code = ApiErrorCode.UNSUPPORTED_DEVICE,
                        message = "Stored device cannot create a Light API"
                    )

                is ApiResult.Error -> return@execute apiResult
            }

            LightRuntimeRepositoryFactory
                .create(deviceApi)
                .readSnapshot(deviceApi.connection)
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
}
