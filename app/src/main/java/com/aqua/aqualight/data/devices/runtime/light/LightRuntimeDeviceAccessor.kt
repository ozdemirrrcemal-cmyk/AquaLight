package com.aqua.aqualight.data.devices.runtime.light

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceApiFactory
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.AquaLightDeviceApi
import com.aqua.aqualight.data.devices.api.DeviceApiMode
import com.aqua.aqualight.data.devices.api.light.LightChannelValues
import com.aqua.aqualight.data.devices.api.light.LightCoolingControllerRequest
import com.aqua.aqualight.data.devices.api.light.LightManualRequest
import com.aqua.aqualight.data.devices.api.light.LightProgramWriteRequest
import com.aqua.aqualight.data.devices.api.light.LightThermalProtectionRequest
import com.aqua.aqualight.data.devices.api.light.LightTimeSyncRequest
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.api.model.DeviceIdentity
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.runtime.DeviceCommandGateway
import com.aqua.aqualight.data.devices.runtime.DeviceEndpointResolver

/**
 * Low-level command gateway for stored Light controllers.
 *
 * This class resolves the current network endpoint and talks to the firmware API.
 * Screen/ViewModel code should use LightRuntimeRepository instead so all visible
 * Light screens share the same runtime StateFlow.
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

    init {
        LightLocalOverrideStore.initialize(
            context = context.applicationContext
        )
    }

    suspend fun readSnapshot(
        deviceId: Long,
        readProfile: LightRuntimeReadProfile = LightRuntimeReadProfile.STANDARD
    ): ApiResult<LightRuntimeSnapshot> {
        return when (val result = readSnapshotWithDevice(
            deviceId = deviceId,
            readProfile = readProfile
        )) {
            is ApiResult.Success -> ApiResult.success(result.value.snapshot)
            is ApiResult.Error -> result
        }
    }

    suspend fun readSnapshotWithDevice(
        deviceId: Long,
        readProfile: LightRuntimeReadProfile = LightRuntimeReadProfile.STANDARD
    ): ApiResult<LightRuntimeDeviceSnapshot> {
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
                is ApiResult.Success -> {
                    val runtimeConnection = deviceApi.value.connection.forReadProfile(
                        readProfile
                    )
                    when (val snapshot = createRuntimeDataSource(deviceApi.value)
                        .readSnapshot(runtimeConnection)) {
                        is ApiResult.Success -> ApiResult.success(
                            LightRuntimeDeviceSnapshot(
                                device = device,
                                snapshot = LightLocalOverrideStore.applyToSnapshot(
                                    deviceId = deviceId,
                                    snapshot = snapshot.value
                                )
                            )
                        )

                        is ApiResult.Error -> snapshot
                    }
                }

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
                is ApiResult.Success -> {
                    when (val result = deviceApi.value.lightApi.setManual(
                        connection = deviceApi.value.connection,
                        request = LightManualRequest(
                            powerOn = !normalizedChannels.isOff,
                            channelValues = normalizedChannels
                        )
                    )) {
                        is ApiResult.Success -> {
                            LightLocalOverrideStore.recordManual(
                                deviceId = deviceId,
                                channels = normalizedChannels
                            )
                            result
                        }

                        is ApiResult.Error -> result
                    }
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun setTemporaryManualOutput(
        deviceId: Long,
        channelValues: LightChannelValues,
        timeoutMillis: Long
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        val normalizedChannels = channelValues.normalized()
        val safeTimeoutMillis = timeoutMillis.coerceIn(
            MIN_TEMPORARY_MANUAL_TIMEOUT_MILLIS,
            MAX_TEMPORARY_MANUAL_TIMEOUT_MILLIS
        )

        return commandGateway.execute(
            deviceId = deviceId
        ) { device, connection ->
            when (val deviceApi = createLightDeviceApi(device, connection)) {
                is ApiResult.Success -> {
                    deviceApi.value.lightApi.setManual(
                        connection = deviceApi.value.connection,
                        request = LightManualRequest(
                            powerOn = true,
                            channelValues = normalizedChannels,
                            overrideTimeoutMillis = safeTimeoutMillis
                        )
                    )
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun setSceneOutput(
        deviceId: Long,
        channelValues: LightChannelValues,
        sceneName: String,
        sceneSource: String?
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
                is ApiResult.Success -> {
                    when (val result = deviceApi.value.lightApi.setManual(
                        connection = deviceApi.value.connection,
                        request = LightManualRequest(
                            powerOn = !normalizedChannels.isOff,
                            channelValues = normalizedChannels
                        )
                    )) {
                        is ApiResult.Success -> {
                            LightLocalOverrideStore.recordScene(
                                deviceId = deviceId,
                                sceneName = sceneName,
                                sceneSource = sceneSource,
                                channels = normalizedChannels
                            )
                            result
                        }

                        is ApiResult.Error -> result
                    }
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun writeProgramSchedule(
        deviceId: Long,
        request: LightProgramWriteRequest
    ): ApiResult<Unit> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light device id is missing"
            )
        }

        if (request.channels.isEmpty()) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Light program schedule is empty"
            )
        }

        return commandGateway.execute(
            deviceId = deviceId
        ) { device, connection ->
            when (val deviceApi = createLightDeviceApi(device, connection)) {
                is ApiResult.Success -> {
                    deviceApi.value.lightApi.writeProgramSchedule(
                        connection = deviceApi.value.connection,
                        request = request
                    )
                }

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
                is ApiResult.Success -> {
                    when (val result = deviceApi.value.lightApi.resumeAuto(
                        connection = deviceApi.value.connection
                    )) {
                        is ApiResult.Success -> {
                            LightLocalOverrideStore.clear(deviceId)
                            result
                        }

                        is ApiResult.Error -> result
                    }
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun setThermalProtection(
        deviceId: Long,
        request: LightThermalProtectionRequest
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
                is ApiResult.Success -> {
                    deviceApi.value.lightApi.setThermalProtection(
                        connection = deviceApi.value.connection,
                        request = request
                    )
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun setCoolingController(
        deviceId: Long,
        request: LightCoolingControllerRequest
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
                is ApiResult.Success -> {
                    deviceApi.value.lightApi.setCoolingController(
                        connection = deviceApi.value.connection,
                        request = request
                    )
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    suspend fun syncTime(
        deviceId: Long,
        request: LightTimeSyncRequest
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
                is ApiResult.Success -> {
                    deviceApi.value.lightApi.syncTime(
                        connection = deviceApi.value.connection,
                        request = request
                    )
                }

                is ApiResult.Error -> deviceApi
            }
        }
    }

    private fun createRuntimeDataSource(
        deviceApi: AquaLightDeviceApi
    ): LightRuntimeDataSource {
        return when (deviceApi.mode) {
            DeviceApiMode.LEGACY -> LegacyLightRuntimeDataSource(deviceApi.lightApi)
            DeviceApiMode.V1 -> V1LightRuntimeDataSource(deviceApi.lightApi)
        }
    }

    private fun AquaDeviceConnection.forReadProfile(
        readProfile: LightRuntimeReadProfile
    ): AquaDeviceConnection {
        return when (readProfile) {
            LightRuntimeReadProfile.STANDARD -> this
            LightRuntimeReadProfile.LIVE -> copy(
                connectTimeoutMillis = minOf(connectTimeoutMillis, LIVE_READ_TIMEOUT_MILLIS),
                readTimeoutMillis = minOf(readTimeoutMillis, LIVE_READ_TIMEOUT_MILLIS)
            )
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

    private companion object {
        const val LIVE_READ_TIMEOUT_MILLIS = 1_500
        const val MIN_TEMPORARY_MANUAL_TIMEOUT_MILLIS = 500L
        const val MAX_TEMPORARY_MANUAL_TIMEOUT_MILLIS = 10_000L
    }
}
