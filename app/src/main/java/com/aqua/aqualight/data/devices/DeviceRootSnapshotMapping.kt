package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogFailureCode
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogIdentityResolution
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogValidation
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toDeviceRootSnapshot(): DeviceRootSnapshot {
    if (!hasValidatedRuntimeMetadata) {
        return when (val identity = AqlCommercialDeviceCatalog.resolveSnapshotIdentity(this)) {
            is AqlCommercialCatalogIdentityResolution.Resolved ->
                toPendingDeviceRootSnapshot(identity.product)
            AqlCommercialCatalogIdentityResolution.Pending ->
                toUnavailableDeviceRootSnapshot(DeviceRootCatalogState.PENDING)
            AqlCommercialCatalogIdentityResolution.Unsupported ->
                toUnavailableDeviceRootSnapshot(DeviceRootCatalogState.UNSUPPORTED)
            AqlCommercialCatalogIdentityResolution.Invalid ->
                toUnavailableDeviceRootSnapshot(DeviceRootCatalogState.INVALID)
        }
    }
    return when (val validation = AqlCommercialDeviceCatalog.validateSnapshot(this)) {
        is AqlCommercialCatalogValidation.Valid -> toValidatedDeviceRootSnapshot(validation.product)
        is AqlCommercialCatalogValidation.Invalid -> toUnavailableDeviceRootSnapshot(
            catalogState = if (
                validation.failure.code ==
                AqlCommercialCatalogFailureCode.UNKNOWN_COMPATIBILITY_IDENTITY
            ) {
                DeviceRootCatalogState.UNSUPPORTED
            } else {
                DeviceRootCatalogState.INVALID
            }
        )
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

/**
 * Publishes only immutable, exact catalog topology while current-session metadata is pending.
 * Routes/capabilities remain fail-closed until full authenticated validation reaches VALID.
 */
private fun DeviceSnapshot.toPendingDeviceRootSnapshot(
    product: AqlCommercialCatalogProduct
): DeviceRootSnapshot {
    val channelSlots = DeviceChannelSlotResolver.resolve(product)
    return DeviceRootSnapshot(
        deviceUid = deviceUid.value,
        title = title,
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        family = product.family.toOwnerDeviceFamily(),
        catalogState = DeviceRootCatalogState.PENDING,
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
        productDisplayName = product.displayName,
        hasCustomName = identity.customName.isNotBlank()
    )
}

private fun DeviceSnapshot.toUnavailableDeviceRootSnapshot(
    catalogState: DeviceRootCatalogState
): DeviceRootSnapshot = DeviceRootSnapshot(
    deviceUid = deviceUid.value,
    title = title,
    availability = connectionState.onlineState.toOwnerDeviceAvailability(),
    catalogState = catalogState,
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
