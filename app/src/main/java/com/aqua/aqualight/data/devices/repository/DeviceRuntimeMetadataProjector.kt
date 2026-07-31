package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceCapabilitySet
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeCapabilities
import com.aqua.aqualight.data.devices.model.DeviceRuntimeMetadataGenerationState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/** Atomically materializes only fully validated authenticated runtime metadata. */
object DeviceRuntimeMetadataProjector {

    fun applyReady(
        snapshot: DeviceSnapshot,
        ready: DeviceRuntimeMetadataGenerationState.Ready
    ): DeviceSnapshot {
        require(snapshot.deviceUid == ready.deviceUid) {
            "Ready runtime metadata belongs to another device."
        }
        val envelope = ready.identityEnvelope
        val identity = envelope.identity
        val capabilities = ready.metadata.capabilities
        return snapshot.copy(
            identity = snapshot.identity.copy(
                shortId = envelope.shortId,
                macAddress = envelope.macAddress,
                serialNumber = envelope.serialNumber,
                firmwareSerial = envelope.firmwareSerial,
                displayName = identity.displayName,
                customName = identity.customName,
                setupCode = envelope.setupCode
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
                setupCode = envelope.setupCode,
                hardwareRevision = identity.hardwareRevision.value
            ),
            firmwareVersion = identity.firmwareVersion.value,
            apiVersion = identity.apiVersion.value.toString(),
            protocolVersion = identity.protocolVersion.value.toString(),
            endpoint = snapshot.endpoint.copy(
                runtimeTransport = envelope.runtime.transport,
                wsPort = envelope.runtime.wsPort,
                wsPath = envelope.runtime.wsPath,
                wsProtocol = envelope.runtime.wsSchema,
                wsProtocolVersion = envelope.runtime.wsProtocolVersion
            ),
            capabilities = capabilities.capabilities.toSnapshotCapabilities(),
            limits = capabilities.toSnapshotLimits(),
            supportedFeatures = capabilities.supportedFeatures
                .map { feature -> feature.wireValue }
                .sorted(),
            supportedScreens = capabilities.supportedScreens
                .map { screen -> screen.wireValue }
                .sorted(),
            modules = ready.metadata.modules.enabled
                .map { module -> module.wireValue }
                .sorted(),
            runtimeMetadataGeneration = ready.generation.value
        )
    }

    /** Provisioning consumes the same validated three-response publication as normal runtime. */
    fun applyProvisioningMetadata(
        snapshot: DeviceSnapshot,
        ready: DeviceRuntimeMetadataGenerationState.Ready
    ): DeviceSnapshot = applyReady(snapshot, ready)

    /** Withdraws current-session trust without erasing durable owner presentation metadata. */
    fun invalidate(snapshot: DeviceSnapshot): DeviceSnapshot = snapshot.copy(
        capabilities = DeviceCapabilities(),
        limits = DeviceLimits(),
        supportedFeatures = emptyList(),
        supportedScreens = emptyList(),
        modules = emptyList(),
        runtimeMetadataGeneration = 0L
    )

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
