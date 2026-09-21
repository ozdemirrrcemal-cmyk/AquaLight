package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.compatibility.DeviceCommercialCompatibilityEvaluation
import com.aqua.aqualight.data.devices.compatibility.DeviceCommercialCompatibilityEvaluator
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toDeviceRootSnapshot(): DeviceRootSnapshot =
    when (val compatibility = DeviceCommercialCompatibilityEvaluator.evaluate(this)) {
        is DeviceCommercialCompatibilityEvaluation.Compatible ->
            toValidatedDeviceRootSnapshot(compatibility)
        is DeviceCommercialCompatibilityEvaluation.Incompatible ->
            toInvalidDeviceRootSnapshot()
    }

private fun DeviceSnapshot.toValidatedDeviceRootSnapshot(
    compatibility: DeviceCommercialCompatibilityEvaluation.Compatible
): DeviceRootSnapshot {
    val product = compatibility.product
    val menuFeatures = compatibility.menuFeatures
    val channelSlots = DeviceChannelSlotResolver.resolve(product)
    return DeviceRootSnapshot(
        deviceUid = deviceUid.value,
        title = title,
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        family = product.family.toOwnerDeviceFamily(),
        catalogState = DeviceRootCatalogState.VALID,
        productKey = product.productKey.value,
        productId = product.productId.value,
        model = product.model.value,
        serialNumber = identity.serialNumber,
        hardwareRevision = product.hardwareRevision.value,
        ipAddress = endpoint.ip.trim(),
        firmwareLabel = firmwareVersion,
        modelLabel = "${product.model.value} / ${product.hardwareRevision.value}",
        lightChannelCount = channelSlots.lightChannels.size,
        timerChannelCount = channelSlots.timerChannels.size,
        dosingChannelCount = channelSlots.dosingChannels.size,
        fanOutputCount = channelSlots.fanOutputs.size,
        temperatureSensorCount = channelSlots.temperatureSensors.size,
        channelSlots = channelSlots,
        capabilities = product.profile.capabilities.toRootCapabilities(),
        supportedFeatures = supportedFeatures,
        supportedScreens = supportedScreens,
        menuFeatures = menuFeatures,
        allowedRoutes = compatibility.allowedRoutes,
        productDisplayName = product.displayName,
        hasCustomName = identity.customName.isNotBlank()
    )
}

private fun DeviceSnapshot.toInvalidDeviceRootSnapshot(): DeviceRootSnapshot = DeviceRootSnapshot(
    deviceUid = deviceUid.value,
    title = title,
    availability = connectionState.onlineState.toOwnerDeviceAvailability(),
    catalogState = DeviceRootCatalogState.INVALID,
    serialNumber = identity.serialNumber,
    ipAddress = endpoint.ip.trim(),
    productDisplayName = identity.displayName,
    hasCustomName = identity.customName.isNotBlank()
)
