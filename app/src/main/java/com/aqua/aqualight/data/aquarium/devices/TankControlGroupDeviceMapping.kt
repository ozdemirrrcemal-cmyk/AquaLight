package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.groups.LightGroupCompatibility
import com.aqua.aqualight.application.devices.groups.TankControlGroupDevice
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.toTankDeviceListItem

internal fun DeviceSnapshot.toControlGroupDevice(): TankControlGroupDevice? {
    val item = toTankDeviceListItem()
    if (item.family != OwnerDeviceFamily.LIGHT) return null
    val product = AqlCommercialDeviceCatalog.presentationProduct(this)
    val key = product?.let {
        LightGroupCompatibility(it.productKey.value, it.productId.value, it.model.value,
            it.hardwareRevision.value, it.limits.lightChannelCount)
    }
    return TankControlGroupDevice(item, product?.displayName ?: this.product.displayName, key)
}
