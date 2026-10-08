package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.ui.common.devicepresence.toDeviceConnectionVisualState

object DeviceCompactSnapshotMapper {

    fun map(
        device: TankDeviceListItem,
        supportingText: String = "",
        showAction: Boolean = false,
        actionText: String = ""
    ): DeviceCompactCardUi = DeviceCompactCardUi(
        deviceUid = device.deviceUid,
        displayName = device.displayName.ifBlank { device.deviceUid },
        serialText = device.serialText.ifBlank { device.deviceUid },
        supportingText = supportingText,
        iconRes = DeviceFamilyIconMapper.iconFor(device.family),
        statusStyle = device.availability.toDeviceConnectionVisualState(),
        actionText = actionText,
        showAction = showAction
    )
}
