package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.TankDeviceListItem

object DeviceCompactSnapshotMapper {

    fun map(
        device: TankDeviceListItem,
        supportingText: String = "",
        showAction: Boolean = false,
        actionText: String = ""
    ): DeviceCompactCardUi {
        val isReachable = device.availability == OwnerDeviceAvailability.REACHABLE
        return DeviceCompactCardUi(
            deviceUid = device.deviceUid,
            displayName = device.displayName.ifBlank { device.deviceUid },
            serialText = device.serialText.ifBlank { device.deviceUid },
            supportingText = supportingText,
            iconRes = DeviceFamilyIconMapper.iconFor(device.family),
            dosingChannelCount = device.dosingChannelCount,
            statusStyle = if (isReachable) {
                DeviceCompactStatusStyle.ONLINE
            } else {
                DeviceCompactStatusStyle.OFFLINE
            },
            actionText = actionText,
            showAction = showAction
        )
    }

}
