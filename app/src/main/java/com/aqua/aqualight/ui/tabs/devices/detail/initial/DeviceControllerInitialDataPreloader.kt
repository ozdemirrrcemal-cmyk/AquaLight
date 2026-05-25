package com.aqua.aqualight.ui.tabs.devices.detail.initial

import android.content.Context
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceUiController
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.CoolingDeviceRepository
import com.aqua.aqualight.ui.tabs.devices.detail.timer.TimerDeviceRepository
import com.aqua.aqualight.ui.tabs.devices.model.DeviceCardUi

object DeviceControllerInitialDataPreloader {

    suspend fun preload(
        context: Context,
        device: DeviceCardUi,
        ip: String
    ) {
        val definition = AquaDeviceCatalog.findByType(
            type = device.deviceType
        ) ?: return

        when (definition.uiController) {
            AquaDeviceUiController.GENERIC_TIMER,
            AquaDeviceUiController.CUSTOM_TIMER_MULTI_CONTROL,
            AquaDeviceUiController.CUSTOM_TIMER_SCENE_PRO -> {
                preloadTimerDashboard(
                    deviceId = device.id,
                    ip = ip
                )
            }

            AquaDeviceUiController.GENERIC_COOLING,
            AquaDeviceUiController.CUSTOM_COOLING_ADVANCED -> {
                preloadCoolingDashboard(
                    deviceId = device.id,
                    ip = ip
                )
            }

            else -> {
                DeviceControllerInitialDataStore.clear(
                    deviceId = device.id
                )
            }
        }
    }

    private suspend fun preloadTimerDashboard(
        deviceId: Long,
        ip: String
    ) {
        if (
            ip.isBlank() ||
            ip == "0.0.0.0"
        ) {
            DeviceControllerInitialDataStore.clear(
                deviceId = deviceId
            )
            return
        }

        val result = runCatching {
            TimerDeviceRepository().fetchTimerDashboardData(
                ipAddress = ip
            )
        }

        result.onSuccess { dashboardData ->
            DeviceControllerInitialDataStore.put(
                deviceId = deviceId,
                data = DeviceControllerInitialData.TimerDashboard(
                    dashboardData = dashboardData
                )
            )
        }.onFailure {
            DeviceControllerInitialDataStore.clear(
                deviceId = deviceId
            )
        }
    }

    private suspend fun preloadCoolingDashboard(
        deviceId: Long,
        ip: String
    ) {
        if (
            ip.isBlank() ||
            ip == "0.0.0.0"
        ) {
            DeviceControllerInitialDataStore.clear(
                deviceId = deviceId
            )
            return
        }

        val result = runCatching {
            CoolingDeviceRepository().fetchCoolingDashboardData(
                ipAddress = ip
            )
        }

        result.onSuccess { dashboardData ->
            DeviceControllerInitialDataStore.put(
                deviceId = deviceId,
                data = DeviceControllerInitialData.CoolingDashboard(
                    dashboardData = dashboardData
                )
            )
        }.onFailure {
            DeviceControllerInitialDataStore.clear(
                deviceId = deviceId
            )
        }
    }
}