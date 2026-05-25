package com.aqua.aqualight.ui.tabs.devices.detail.initial

import com.aqua.aqualight.ui.tabs.devices.detail.cooling.CoolingDeviceRepository
import com.aqua.aqualight.ui.tabs.devices.detail.timer.TimerDeviceRepository

sealed class DeviceControllerInitialData {

    data class TimerDashboard(
        val dashboardData: TimerDeviceRepository.TimerDashboardData
    ) : DeviceControllerInitialData()

    data class CoolingDashboard(
        val dashboardData: CoolingDeviceRepository.CoolingDashboardData
    ) : DeviceControllerInitialData()
}