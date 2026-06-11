package com.aqua.aqualight.data.devices.access

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.flow.first

class DeviceAccessGuard(
    context: Context,
    private val devicesStore: DevicesDataStoreManager = DevicesDataStoreManager.create(
        context.applicationContext
    )
) {

    private val appContext = context.applicationContext

    suspend fun resolveForOpen(
        deviceId: Long
    ): DeviceOpenResult {
        val device = devicesStore.devicesFlow
            .first()
            .firstOrNull { savedDevice ->
                savedDevice.id == deviceId
            } ?: return DeviceOpenResult.NotFound

        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        ) ?: return DeviceOpenResult.Unsupported(
            device = device
        )

        val status = DevicePresenceMonitor.checkDeviceNow(
            context = appContext,
            deviceId = device.id,
            knownIp = device.ip,
            allowRecentOnlineCache = false
        )

        if (status?.isOnline != true) {
            return DeviceOpenResult.Offline(
                device = device
            )
        }

        val resolvedIp = status.ip.ifBlank {
            device.ip
        }

        if (resolvedIp.isBlank()) {
            return DeviceOpenResult.Offline(
                device = device
            )
        }

        return DeviceOpenResult.Allowed(
            device = device,
            ip = resolvedIp,
            definition = definition
        )
    }
}
