package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper

object DeviceCardMapper {

    fun map(
        snapshot: DeviceSnapshot,
        assignedTankText: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): DeviceCardUi {
        val supportingText = listOfNotNull(
            DeviceCompactSnapshotMapper.defaultSupportingText(snapshot),
            assignedTankText
                ?.trim()
                ?.takeIf(String::isNotBlank)
        ).joinToString(separator = " • ")

        return DeviceCardUi(
            deviceUid = snapshot.deviceUid.value,
            card = DeviceCompactSnapshotMapper.map(
                snapshot = snapshot,
                supportingText = supportingText
            )
        )
    }
}
