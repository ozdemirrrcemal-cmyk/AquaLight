package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.groups.LightGroupCompatibility
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import com.aqua.aqualight.data.devices.toTankDeviceListItem

internal fun DeviceSnapshot.toControlGroupDevice(): TankControlGroupDevice? {
    val item = toTankDeviceListItem()
    if (item.family != OwnerDeviceFamily.LIGHT) return null
    val root = toDeviceRootSnapshot()
    val compatibility = if (
        root.catalogState == DeviceRootCatalogState.VALID &&
        root.family == OwnerDeviceFamily.LIGHT && root.lightChannelCount > 0
    ) {
        LightGroupCompatibility(root.productKey, root.productId, root.model,
            root.hardwareRevision, root.lightChannelCount)
    } else null
    return TankControlGroupDevice(item, root.productDisplayName, compatibility)
}
