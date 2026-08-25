package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactCardUi
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactStatusStyle
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactVisualKind
import com.aqua.aqualight.ui.common.devicecard.DeviceFamilyIconMapper

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
                visualKind = device.compactVisualKind(),
                statusStyle = if (isReachable) {
                    DeviceCompactStatusStyle.ONLINE
                } else {
                    DeviceCompactStatusStyle.OFFLINE
                },
                actionText = "",
                showAction = false
            )
        )
    }

    private fun OwnerDeviceListItem.compactVisualKind(): DeviceCompactVisualKind =
        if (family == OwnerDeviceFamily.DOSING) {
            DeviceCompactVisualKind.DOSING_IDENTITY
        } else {
            DeviceCompactVisualKind.ICON
        }
}
