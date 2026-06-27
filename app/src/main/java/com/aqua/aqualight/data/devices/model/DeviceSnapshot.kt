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
    val connectionState: DeviceConnectionState = DeviceConnectionState(),
    val lastSeenAtMillis: Long = 0L
) {
    val deviceUid: DeviceUid
        get() = identity.uid

    val title: String
        get() = identity.customName
            .ifBlank { product.displayName }
            .ifBlank { identity.displayName }
            .ifBlank { deviceUid.value }
}
