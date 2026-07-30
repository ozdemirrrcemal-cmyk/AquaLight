package com.aqua.aqualight.data.devices.model

/**
 * Fully typed device metadata payload assembled only after all required firmware responses validate.
 * Generation/readiness state is deliberately added by the next reducer slice, not encoded here.
 */
data class DeviceRuntimeMetadata(
    val identity: DeviceRuntimeIdentity,
    val capabilities: DeviceRuntimeCapabilities,
    val modules: DeviceRuntimeModules
)
