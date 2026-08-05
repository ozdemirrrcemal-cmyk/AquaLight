package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toDeviceRootSnapshot(): DeviceRootSnapshot {
    if (!hasValidatedRuntimeMetadata) return toInvalidDeviceRootSnapshot()
    return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(this)) {
        is AqlCommercialCatalogValidation.Valid -> toValidatedDeviceRootSnapshot(validation.product)
        is AqlCommercialCatalogValidation.Invalid -> toInvalidDeviceRootSnapshot()
    }
}

private fun DeviceSnapshot.toValidatedDeviceRootSnapshot(
    product: AqlCommercialCatalogProduct
): DeviceRootSnapshot {
    val menuFeatures = DeviceRootMenuFeatureResolver.resolve(product)
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
        firmwareLabel = firmwareLabel(),
        modelLabel = "${product.model.value} / ${product.hardwareRevision.value}",
        lightChannelCount = channelSlots.lightChannels.size,
        timerChannelCount = channelSlots.timerChannels.size,
        dosingChannelCount = channelSlots.dosingChannels.size,
        fanOutputCount = channelSlots.fanOutputs.size,
        temperatureSensorCount = channelSlots.temperatureSensors.size,
        channelSlots = channelSlots,
        capabilities = product.profile.capabilities.toRootCapabilities(),
        supportedFeatures = product.profile.supportedFeatures.map { it.wireValue },
        supportedScreens = product.profile.supportedScreens.map { it.wireValue },
        menuFeatures = menuFeatures,
        allowedRoutes = DeviceRootRoutePolicy.allowedRoutes(product),
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
    firmwareLabel = firmwareLabel(),
    productDisplayName = identity.displayName,
    hasCustomName = identity.customName.isNotBlank()
)

private fun DeviceSnapshot.firmwareLabel(): String = listOf(
    firmwareVersion.ifBlank { null },
    firmwareBuild.ifBlank { null }
).filterNotNull().joinToString(separator = " / ")
