package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadata
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/** Materializes validated typed metadata into the existing device snapshot in one copy operation. */
object DeviceRuntimeMetadataProjector {

    fun applyReady(
        snapshot: DeviceSnapshot,
        metadata: DeviceRuntimeMetadata
    ): DeviceSnapshot {
        val identity = metadata.identity
        val capabilities = metadata.capabilities
        return snapshot.copy(
            identity = snapshot.identity.copy(displayName = identity.displayName),
            product = snapshot.product.copy(
                brand = identity.brand,
                productId = identity.productId.value,
                productKey = identity.productKey.value,
                family = identity.family,
                familyRaw = identity.family.wireValue,
                line = identity.line.value,
                model = identity.model.value,
                displayName = identity.displayName,
                skuId = identity.skuId.value,
                skuCode = identity.skuCode.value,
                hardwareRevision = identity.hardwareRevision.value
            ),
            firmwareVersion = identity.firmwareVersion.value,
            apiVersion = identity.apiVersion.value.toString(),
            protocolVersion = identity.protocolVersion.value.toString(),
            capabilities = capabilities.capabilities.toSnapshotCapabilities(),
            limits = capabilities.toSnapshotLimits(),
            supportedFeatures = capabilities.supportedFeatures
                .map { feature -> feature.wireValue }
                .sorted(),
            supportedScreens = capabilities.supportedScreens
                .map { screen -> screen.wireValue }
                .sorted(),
            modules = metadata.modules.enabled
                .map { module -> module.wireValue }
                .sorted()
        )
    }

    fun applyProvisioningMetadata(
        snapshot: DeviceSnapshot,
        parsedIdentity: ParsedDeviceRuntimeIdentity,
        capabilities: DeviceRuntimeCapabilities
    ): DeviceSnapshot {
        val identity = parsedIdentity.identity
        return snapshot.copy(
            identity = snapshot.identity.copy(
                shortId = parsedIdentity.shortId,
                macAddress = parsedIdentity.macAddress,
                serialNumber = parsedIdentity.serialNumber,
                firmwareSerial = parsedIdentity.firmwareSerial,
                displayName = identity.displayName,
                setupCode = parsedIdentity.setupCode
            ),
            product = DeviceProduct(
                brand = identity.brand,
                productId = identity.productId.value,
                productKey = identity.productKey.value,
                family = identity.family,
                familyRaw = identity.family.wireValue,
                line = identity.line.value,
                model = identity.model.value,
                displayName = identity.displayName,
                skuId = identity.skuId.value,
                skuCode = identity.skuCode.value,
                setupCode = parsedIdentity.setupCode,
                hardwareRevision = identity.hardwareRevision.value
            ),
            firmwareVersion = identity.firmwareVersion.value,
            apiVersion = identity.apiVersion.value.toString(),
            protocolVersion = identity.protocolVersion.value.toString(),
            endpoint = snapshot.endpoint.copy(
                runtimeTransport = parsedIdentity.runtime.transport,
                wsPort = parsedIdentity.runtime.wsPort,
                wsPath = parsedIdentity.runtime.wsPath,
                wsProtocol = parsedIdentity.runtime.wsSchema,
                wsProtocolVersion = parsedIdentity.runtime.wsProtocolVersion
            ),
            capabilities = capabilities.capabilities.toSnapshotCapabilities(),
            limits = capabilities.toSnapshotLimits(),
            supportedFeatures = capabilities.supportedFeatures
                .map { feature -> feature.wireValue }
                .sorted(),
            supportedScreens = capabilities.supportedScreens
                .map { screen -> screen.wireValue }
                .sorted()
        )
    }

    private fun DeviceCapabilitySet.toSnapshotCapabilities(): DeviceCapabilities = DeviceCapabilities(
        light = light,
        manualLight = manualLight,
        lightProgram = lightProgram,
        lightPresets = lightPresets,
        lightSimulation = lightSimulation,
        fan = fan,
        cooling = cooling,
        temperature = temperature,
        standaloneTimer = standaloneTimer,
        dosing = dosing,
        timeSync = timeSync,
        ota = ota
    )

    private fun DeviceRuntimeCapabilities.toSnapshotLimits(): DeviceLimits = DeviceLimits(
        lightChannelCount = limits.lightChannelCount,
        fanOutputCount = limits.fanOutputCount,
        temperatureSensorCount = limits.temperatureSensorCount,
        timerChannelCount = limits.timerChannelCount,
        dosingChannelCount = limits.dosingChannelCount
    )
}
