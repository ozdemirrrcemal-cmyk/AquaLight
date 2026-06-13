package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi

data class TankDeviceSelectItem(
    val card: DeviceCompactCardUi
) {
    val deviceId: Long
        get() = card.deviceId
}
