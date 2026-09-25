package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState

object DeviceCardMapper {

    fun map(
        device: OwnerDeviceListItem
    ): DeviceCardUi {
        val supportingText = device.assignedTankName
            .trim()
            .takeIf(String::isNotBlank)
            .orEmpty()
        return DeviceCardUi(
            deviceUid = device.deviceUid,
            card = DeviceCompactCardUi(
                deviceUid = device.deviceUid,
                displayName = device.displayName.ifBlank { device.deviceUid },
                serialText = device.serialText.ifBlank { device.deviceUid },
                supportingText = supportingText,
                iconRes = DeviceFamilyIconMapper.iconFor(device.family),
                statusStyle = device.availability.toDeviceConnectionVisualState(),
                actionText = "",
                showAction = false
            )
        )
    }
}
