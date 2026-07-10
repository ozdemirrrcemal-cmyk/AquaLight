package com.aqua.aqualight.ui.tabs.devices

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper

object DeviceCardMapper {

    fun map(
        snapshot: DeviceSnapshot,
        assignedTankText: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): DeviceCardUi {
        return DeviceCardUi(
            deviceUid = snapshot.deviceUid.value,
            card = DeviceCompactSnapshotMapper.map(
                snapshot = snapshot,
                supportingText = assignedTankText
            )
        )
    }
}
