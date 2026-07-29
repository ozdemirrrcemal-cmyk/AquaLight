package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toDeviceRootSnapshot(): DeviceRootSnapshot {
    return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(this)) {
        is AqlCommercialCatalogValidation.Valid -> toValidatedDeviceRootSnapshot(validation.product)
        is AqlCommercialCatalogValidation.Invalid -> toInvalidDeviceRootSnapshot()
    }
}

private fun DeviceSnapshot.toValidatedDeviceRootSnapshot(
    product: AqlCommercialCatalogProduct
): DeviceRootSnapshot {
    val menuFeatures = DeviceRootMenuFeatureResolver.resolve(product)
    return DeviceRootSnapshot(
        deviceUid = deviceUid.value,
        title = title,
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        family = product.family.toOwnerDeviceFamily(),
        catalogState = DeviceRootCatalogState.VALID,
        productKey = product.productKey.value,
        productId = product.productId.value,
        model = product.model.value,
        hardwareRevision = product.hardwareRevision.value,
        ipAddress = endpoint.ip.trim(),
        firmwareLabel = firmwareLabel(),
        modelLabel = "${product.model.value} / ${product.hardwareRevision.value}",
        lightChannelCount = product.limits.lightChannelCount,
        timerChannelCount = product.limits.timerChannelCount,
        dosingChannelCount = product.limits.dosingChannelCount,
        fanOutputCount = product.limits.fanOutputCount,
        capabilities = product.profile.capabilities.toRootCapabilities(),
        supportedFeatures = product.profile.supportedFeatures.map { feature -> feature.wireValue },
        supportedScreens = product.profile.supportedScreens.map { screen -> screen.wireValue },
        menuFeatures = menuFeatures,
        allowedRoutes = DeviceRootRoutePolicy.allowedRoutes(product)
    )
}

private fun DeviceSnapshot.toInvalidDeviceRootSnapshot(): DeviceRootSnapshot {
    return DeviceRootSnapshot(
        deviceUid = deviceUid.value,
        title = title,
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        catalogState = DeviceRootCatalogState.INVALID,
        ipAddress = endpoint.ip.trim(),
        firmwareLabel = firmwareLabel()
    )
}

private fun DeviceSnapshot.firmwareLabel(): String {
    return listOf(
        firmwareVersion.ifBlank { null },
        firmwareBuild.ifBlank { null }
    ).filterNotNull().joinToString(separator = " / ")
}
