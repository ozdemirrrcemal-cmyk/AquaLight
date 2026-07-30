package com.aqua.aqualight.data.devices.model

import com.aqua.aqualight.data.devices.contract.AqlDeviceFeatureKey
import com.aqua.aqualight.data.devices.contract.AqlDeviceScreenKey

/** Complete firmware capability object. No field has an implicit default. */
data class DeviceCapabilitySet(
    val light: Boolean,
    val manualLight: Boolean,
    val lightProgram: Boolean,
    val lightPresets: Boolean,
    val lightSimulation: Boolean,
    val fan: Boolean,
    val cooling: Boolean,
    val temperature: Boolean,
    val standaloneTimer: Boolean,
    val dosing: Boolean,
    val timeSync: Boolean,
    val ota: Boolean
)

/** Complete firmware limit object. Absence and zero are intentionally different states. */
data class DeviceLimitSet(
    val lightChannelCount: Int,
    val fanOutputCount: Int,
    val temperatureSensorCount: Int,
    val timerChannelCount: Int,
    val dosingChannelCount: Int
) {
    init {
        require(lightChannelCount >= 0) { "lightChannelCount must not be negative." }
        require(fanOutputCount >= 0) { "fanOutputCount must not be negative." }
        require(temperatureSensorCount >= 0) { "temperatureSensorCount must not be negative." }
        require(timerChannelCount >= 0) { "timerChannelCount must not be negative." }
        require(dosingChannelCount >= 0) { "dosingChannelCount must not be negative." }
    }
}

/**
 * Complete capability metadata received from `device.capabilities.get`.
 *
 * Feature and screen values are already exact typed keys. Unknown wire values cannot be represented
 * by this model and must be rejected by the parser before construction.
 */
data class DeviceRuntimeCapabilities(
    val capabilities: DeviceCapabilitySet,
    val limits: DeviceLimitSet,
    val supportedFeatures: Set<AqlDeviceFeatureKey>,
    val supportedScreens: Set<AqlDeviceScreenKey>
)
