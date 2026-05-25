package com.aqua.aqualight.ui.tabs.devices.open

import android.content.Context
import com.aqua.aqualight.data.devices.presence.DevicePresenceMonitor
import com.aqua.aqualight.ui.tabs.devices.detail.initial.DeviceControllerInitialDataPreloader
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi

object DeviceOpenCoordinator {

    sealed class Result {
        data class Ready(
            val ip: String
        ) : Result()

        object Offline : Result()
    }

    suspend fun open(
        context: Context,
        device: DeviceCardUi
    ): Result {
        val status = DevicePresenceMonitor.checkDeviceNow(
            context = context,
            deviceId = device.id,
            knownIp = device.ip
        )

        if (status?.isOnline != true) {
            return Result.Offline
        }

        val resolvedIp = status.ip.ifBlank {
            device.ip
        }

        DeviceControllerInitialDataPreloader.preload(
            context = context,
            device = device,
            ip = resolvedIp
        )

        return Result.Ready(
            ip = resolvedIp
        )
    }
}