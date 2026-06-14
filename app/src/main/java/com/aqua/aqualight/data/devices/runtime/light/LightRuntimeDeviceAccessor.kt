package com.aqua.aqualight.data.devices.runtime.light

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceApiFactory
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightManualRequest
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.runtime.DeviceCommandGateway
import com.aqua.aqualight.data.devices.runtime.DeviceEndpointResolver

/**
 * Single entry point for Light runtime reads and live commands from a stored
 * Aqua device.
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
            when (val deviceApi = createLightDeviceApi(device, connection)) {
                is ApiResult.Success -> LightRuntimeRepositoryFactory
                    .create(deviceApi.value)
                    .readSnapshot(deviceApi.value.connection)

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun setManualOutput(
        deviceId: Long,
        channelValues: LightChannelValues
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        val normalizedChannels = channelValues.normalized()
        return commandGateway.execute(
            deviceId = deviceId
        ) { device, connection ->
            when (val deviceApi = createLightDeviceApi(device, connection)) {
                is ApiResult.Success -> deviceApi.value.lightApi.setManual(
                    connection = deviceApi.value.connection,
                    request = LightManualRequest(
                        powerOn = !normalizedChannels.isOff,
                        channelValues = normalizedChannels
                    )
                )

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun resumeAuto(
        deviceId: Long
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        return commandGateway.execute(
            deviceId = deviceId
        ) { device, connection ->
            when (val deviceApi = createLightDeviceApi(device, connection)) {
                is ApiResult.Success -> deviceApi.value.lightApi.resumeAuto(
                    connection = deviceApi.value.connection
                )

                is ApiResult.Error -> deviceApi
            }
        }
    }

    private fun createLightDeviceApi(
        device: DevicesDataStoreManager.DeviceInfo,
        connection: AquaDeviceConnection
    ): ApiResult<AquaLightDeviceApi> {
        if (device.category != AquaDeviceCategory.LIGHT) {
            return ApiResult.failure(
                code = ApiErrorCode.UNSUPPORTED_DEVICE,
                message = "Stored device is not a Light controller"
            )
        }

        return when (val apiResult = apiFactory.create(
            identity = device.toIdentity(),
            connection = connection
        )) {
            is ApiResult.Success -> {
                val lightApi = apiResult.value as? AquaLightDeviceApi
                    ?: return ApiResult.failure(
                        code = ApiErrorCode.UNSUPPORTED_DEVICE,
                        message = "Stored device cannot create a Light API"
                    )

                ApiResult.success(lightApi)
            }

            is ApiResult.Error -> apiResult
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
