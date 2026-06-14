package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceApiFactory
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory

/**
 * Single entry point for reading Light runtime from a stored Aqua device.
 *
 * Fragments/ViewModels do not know whether the controller is served by legacy
 * /get or the future V1 API. They provide a stored device record; this accessor
 * builds the correct device API and returns the common LightRuntimeSnapshot.
 */
class LightRuntimeDeviceAccessor(
    private val apiFactory: AquaDeviceApiFactory = AquaDeviceApiFactory()
) {

    suspend fun readSnapshot(
        device: DevicesDataStoreManager.DeviceInfo
    ): ApiResult<LightRuntimeSnapshot> {
        if (device.id <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        if (device.category != AquaDeviceCategory.LIGHT) {
            return ApiResult.failure(
                code = ApiErrorCode.UNSUPPORTED_DEVICE,
                message = "Stored device is not a Light controller"
            )
        }

        val host = device.ip.trim()
        if (host.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.NOT_CONNECTED,
                message = "Light device address is missing"
            )
        }

        val deviceApi = when (val apiResult = apiFactory.create(
            identity = device.toIdentity(),
            connection = AquaDeviceConnection(
                host = host
            )
        )) {
            is ApiResult.Success -> apiResult.value as? AquaLightDeviceApi
                ?: return ApiResult.failure(
                    code = ApiErrorCode.UNSUPPORTED_DEVICE,
                    message = "Stored device cannot create a Light API"
                )

            is ApiResult.Error -> return apiResult
        }

        return LightRuntimeRepositoryFactory
            .create(deviceApi)
            .readSnapshot(deviceApi.connection)
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
