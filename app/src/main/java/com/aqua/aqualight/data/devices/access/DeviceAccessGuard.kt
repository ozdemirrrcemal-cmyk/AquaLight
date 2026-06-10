package com.aqua.aqualight.data.devices.access

import android.content.Context
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import kotlinx.coroutines.flow.first

class DeviceAccessGuard(
    context: Context
) {

    private val appContext =
        context.applicationContext

    private val devicesStore =
        DevicesDataStoreManager.create(appContext)

    suspend fun resolveForOpen(
        deviceId: Long,
        preferredIp: String = ""
    ): DeviceOpenResult {
        val savedDevice = devicesStore.devicesFlow
            .first()
            .firstOrNull { device ->
                device.id == deviceId
            } ?: return DeviceOpenResult.NotFound

        val definition = AquaDeviceCatalog.findByType(
            savedDevice.deviceType
        ) ?: return DeviceOpenResult.Unsupported(
            device = savedDevice
        )

        val knownIp = preferredIp.ifBlank {
            savedDevice.ip
        }

        val checkedState = DevicePresenceMonitor.checkDeviceNow(
            context = appContext,
            deviceId = savedDevice.id,
            knownIp = knownIp,
            allowRecentOnlineCache = false
        )

        if (checkedState?.isOnline != true) {
            return DeviceOpenResult.Offline(
                device = savedDevice
            )
        }

        val resolvedIp = checkedState.ip.ifBlank {
            knownIp
        }

        if (resolvedIp.isBlank()) {
            return DeviceOpenResult.Offline(
                device = savedDevice
            )
        }

        return DeviceOpenResult.Allowed(
            device = savedDevice,
            ip = resolvedIp,
            definition = definition
        )
    }
}
