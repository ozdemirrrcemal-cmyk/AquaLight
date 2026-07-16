package com.aqua.aqualight.ui.common.devicecard

import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.TankDeviceListItem
import java.util.Locale

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
            statusText = (if (isReachable) "Online" else "Offline")
                .uppercase(Locale.US),
            statusStyle = if (isReachable) {
                DeviceCompactStatusStyle.ONLINE
            } else {
                DeviceCompactStatusStyle.OFFLINE
            },
            actionText = actionText,
            showAction = showAction
        )
    }

    fun familyLabel(family: OwnerDeviceFamily): String {
        return when (family) {
            OwnerDeviceFamily.LIGHT -> "Light"
            OwnerDeviceFamily.TIMER -> "Timer"
            OwnerDeviceFamily.DOSING -> "Dosing"
            OwnerDeviceFamily.COOLING -> "Cooling"
            OwnerDeviceFamily.UNKNOWN -> "Device"
        }
    }

    fun defaultSupportingText(device: TankDeviceListItem): String {
        return familyLabel(device.family)
            .takeIf(String::isNotBlank)
            ?: "AquaLight device"
    }
}
