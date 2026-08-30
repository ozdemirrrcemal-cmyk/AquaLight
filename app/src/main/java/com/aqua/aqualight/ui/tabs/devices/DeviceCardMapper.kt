package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

object DeviceCardMapper {

    fun map(
        device: OwnerDeviceListItem
    ): DeviceCardUi {
        val supportingText = device.assignedTankName
            .trim()
            .takeIf(String::isNotBlank)
            .orEmpty()
        val isReachable = device.availability == OwnerDeviceAvailability.REACHABLE

        return DeviceCardUi(
            deviceUid = device.deviceUid,
            card = DeviceCompactCardUi(
                deviceUid = device.deviceUid,
                displayName = device.displayName.ifBlank { device.deviceUid },
                serialText = device.serialText.ifBlank { device.deviceUid },
                supportingText = supportingText,
                iconRes = DeviceFamilyIconMapper.iconFor(device.family),
                statusStyle = if (isReachable) {
                    DeviceConnectionVisualState.ONLINE
                } else {
                    DeviceConnectionVisualState.OFFLINE
                },
                actionText = "",
                showAction = false
            )
        )
    }
}
