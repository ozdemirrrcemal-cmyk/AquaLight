package com.aqua.aqualight.data.devices.model

data class DeviceSnapshot(
    val identity: DeviceIdentity,
    val product: DeviceProduct,
    val firmwareVersion: String = "",
    val firmwareBuild: String = "",
    val apiVersion: String = "",
    val protocolVersion: String = "",
    val endpoint: DeviceRuntimeEndpoint = DeviceRuntimeEndpoint(),
    val capabilities: DeviceCapabilities = DeviceCapabilities(),
    val limits: DeviceLimits = DeviceLimits(),
    val supportedFeatures: List<String> = emptyList(),
    val supportedScreens: List<String> = emptyList(),
    val modules: List<String> = emptyList(),
    /**
     * Non-persisted authenticated metadata generation.
     *
     * A value greater than zero proves that identity, capabilities and modules were validated during
     * the current process/runtime session. Durable snapshots intentionally reload with generation 0
     * and must complete a fresh authenticated bootstrap before routing or controls are exposed.
     */
    val runtimeMetadataGeneration: Long = 0L,
    val connectionState: DeviceConnectionState = DeviceConnectionState(),
    val lastSeenAtMillis: Long = 0L
) {
    init {
        require(runtimeMetadataGeneration >= 0L) {
            "runtimeMetadataGeneration must not be negative."
        }
    }

    val deviceUid: DeviceUid
        get() = identity.uid

    val hasValidatedRuntimeMetadata: Boolean
        get() = runtimeMetadataGeneration > 0L

    val title: String
        get() = identity.customName
            .ifBlank { product.displayName }
            .ifBlank { identity.displayName }
            .ifBlank { deviceUid.value }
}
