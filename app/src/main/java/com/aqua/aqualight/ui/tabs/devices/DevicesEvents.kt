package com.aqua.aqualight.ui.tabs.devices

import androidx.annotation.StringRes
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute

sealed class DevicesEvent {
    data class OpenRoute(
        val route: DeviceRoute
    ) : DevicesEvent()

    data class ShowDeviceUnavailable(
        val title: String,
        @StringRes val messageRes: Int
    ) : DevicesEvent()

    data class ShowDeviceDeleteFailure(
        val removedCount: Int,
        val failedCount: Int
    ) : DevicesEvent()
}
