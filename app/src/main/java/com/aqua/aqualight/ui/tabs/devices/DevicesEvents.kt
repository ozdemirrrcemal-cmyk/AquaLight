package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute

sealed class DevicesEvent {
    data class OpenRoute(val route: DeviceRoute) : DevicesEvent()
}
