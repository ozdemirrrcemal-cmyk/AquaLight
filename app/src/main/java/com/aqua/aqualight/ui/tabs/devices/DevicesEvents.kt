package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute

sealed class DevicesEvent {
    data class OpenRoute(
        val route: DeviceRoute
    ) : DevicesEvent()

    data class ShowDeviceUnavailable(
        val title: String,
        val reason: DeviceMenuUnavailableReason
    ) : DevicesEvent()

    data class ShowDeletePartialSuccess(
        val succeededCount: Int,
        val failedCount: Int
    ) : DevicesEvent()

    data class ShowDeleteFailed(
        val failedCount: Int
    ) : DevicesEvent()
}
