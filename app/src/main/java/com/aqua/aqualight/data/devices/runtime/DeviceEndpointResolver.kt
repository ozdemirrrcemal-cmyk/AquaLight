package com.aqua.aqualight.data.devices.runtime

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.toDeviceLastSeenUpdate
import kotlinx.coroutines.flow.first

class DeviceEndpointResolver(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    )
) {

    private val appContext = context.applicationContext

    suspend fun resolve(
        deviceId: Long,
        forceDiscovery: Boolean = false
    ): ApiResult<ResolvedDeviceEndpoint> {
        if (deviceId <= 0L) {
            return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Device id is missing"
            )
        }

        val storedDevice = findStoredDevice(deviceId)
            ?: return ApiResult.failure(
                code = ApiErrorCode.INVALID_REQUEST,
                message = "Device not found"
            )

        val cachedHost = storedDevice.ip.trim()
        if (!forceDiscovery && cachedHost.isNotBlank()) {
            return ApiResult.success(
                ResolvedDeviceEndpoint(
                    device = storedDevice,
                    connection = AquaDeviceConnection(host = cachedHost)
                )
            )
        }

        val scanResult = DeviceDiscoveryService.scanForDevice(
            context = appContext,
            timeoutMs = LIVE_RESOLVE_TIMEOUT_MS,
            savedDevice = storedDevice
        )

        val discoveredDevice = scanResult.devices.firstOrNull()
            ?: return ApiResult.failure(
                code = ApiErrorCode.NOT_CONNECTED,
                message = "Device could not be resolved on the local network",
                cause = scanResult.error
            )

        devicesStore.updateDevicesLastSeen(
            discovered = listOf(
                discoveredDevice.toDeviceLastSeenUpdate(
                    storedDeviceId = storedDevice.id
                )
            )
        )

        val updatedDevice = findStoredDevice(deviceId) ?: storedDevice
        val resolvedHost = discoveredDevice.ip.trim()
        if (resolvedHost.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.NOT_CONNECTED,
                message = "Resolved device address is missing"
            )
        }

        return ApiResult.success(
            ResolvedDeviceEndpoint(
                device = updatedDevice.copy(
                    ip = resolvedHost
                ),
                connection = AquaDeviceConnection(host = resolvedHost)
            )
        )
    }

    private suspend fun findStoredDevice(
        deviceId: Long
    ): DevicesDataStoreManager.DeviceInfo? {
        return devicesStore.devicesFlow
            .first()
            .firstOrNull { device ->
                device.id == deviceId
            }
    }

    private companion object {
        const val LIVE_RESOLVE_TIMEOUT_MS = 1_800L
    }
}

data class ResolvedDeviceEndpoint(
    val device: DevicesDataStoreManager.DeviceInfo,
    val connection: AquaDeviceConnection
)
