package com.aqua.aqualight.data.devices.runtime

import android.content.Context
import com.aqua.aqualight.data.devices.DeviceIdentityMatcher
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.api.AquaDeviceConnection
import com.aqua.aqualight.data.devices.api.model.ApiErrorCode
import com.aqua.aqualight.data.devices.api.model.ApiResult
import com.aqua.aqualight.data.devices.discovery.DeviceDiscoveryService
import com.aqua.aqualight.data.devices.discovery.toDeviceLastSeenUpdate
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.data.devices.setup.AquaDeviceSetupClient
import kotlinx.coroutines.flow.first

class DeviceEndpointResolver(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    )
) {

    private val appContext = context.applicationContext
    private val setupClient = AquaDeviceSetupClient()

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

        // Opening a device must not depend only on UDP discovery. Some routers,
        // Android network handoff states, and AP+STA setup transitions can drop
        // broadcast packets even though the device is reachable at its saved LAN IP.
        // First verify the cached IP with the V1 identity endpoint; use UDP only as
        // a resolver fallback when the cached address is stale or unreachable.
        val directResolved = resolveCachedIp(
            storedDevice = storedDevice,
            cachedHost = cachedHost
        )
        if (directResolved != null) {
            return ApiResult.success(directResolved)
        }

        if (!forceDiscovery && cachedHost.isNotBlank()) {
            return ApiResult.success(
                ResolvedDeviceEndpoint(
                    device = storedDevice,
                    connection = AquaDeviceConnection(
                        host = cachedHost,
                        apiToken = storedDevice.deviceApiToken
                    )
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

        val resolvedHost = discoveredDevice.ip.trim()
        if (resolvedHost.isBlank()) {
            return ApiResult.failure(
                code = ApiErrorCode.NOT_CONNECTED,
                message = "Resolved device address is missing"
            )
        }

        DevicePresenceMonitor.markDeviceOnline(
            deviceId = storedDevice.id,
            ip = resolvedHost
        )

        val updatedDevice = findStoredDevice(deviceId) ?: storedDevice

        return ApiResult.success(
            ResolvedDeviceEndpoint(
                device = updatedDevice.copy(
                    ip = resolvedHost
                ),
                connection = AquaDeviceConnection(
                    host = resolvedHost,
                    apiToken = updatedDevice.deviceApiToken
                )
            )
        )
    }

    private suspend fun resolveCachedIp(
        storedDevice: DevicesDataStoreManager.DeviceInfo,
        cachedHost: String
    ): ResolvedDeviceEndpoint? {
        if (cachedHost.isBlank()) {
            return null
        }

        val discoveredDevice = runCatching {
            setupClient.readLanDeviceIdentity(
                host = cachedHost,
                deviceApiToken = storedDevice.deviceApiToken
            )
        }.getOrNull() ?: return null

        if (!DeviceIdentityMatcher.samePhysicalDevice(storedDevice, discoveredDevice)) {
            return null
        }

        devicesStore.updateDevicesLastSeen(
            discovered = listOf(
                discoveredDevice.toDeviceLastSeenUpdate(
                    storedDeviceId = storedDevice.id
                )
            )
        )

        DevicePresenceMonitor.markDeviceOnline(
            deviceId = storedDevice.id,
            ip = discoveredDevice.ip
        )

        val updatedDevice = findStoredDevice(storedDevice.id) ?: storedDevice
        val resolvedHost = discoveredDevice.ip.trim().ifBlank { cachedHost }

        return ResolvedDeviceEndpoint(
            device = updatedDevice.copy(
                ip = resolvedHost
            ),
            connection = AquaDeviceConnection(
                host = resolvedHost,
                apiToken = updatedDevice.deviceApiToken
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
        const val LIVE_RESOLVE_TIMEOUT_MS = 4_500L
    }
}

data class ResolvedDeviceEndpoint(
    val device: DevicesDataStoreManager.DeviceInfo,
    val connection: AquaDeviceConnection
)
