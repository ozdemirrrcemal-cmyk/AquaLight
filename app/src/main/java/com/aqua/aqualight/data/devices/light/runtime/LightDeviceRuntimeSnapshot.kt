package com.aqua.aqualight.data.devices.light.runtime

/**
 * Unified runtime snapshot for light screens.
 *
 * Live telemetry is the authoritative source for values that physically come
 * from the ESP32. Manual runtime is kept for UI continuity and for values that
 * the firmware does not expose yet.
 */
data class LightDeviceRuntimeSnapshot(
    val liveState: LightDeviceLiveState,
    val manualRuntime: LightManualRuntimeState
) {

    val hasAuthoritativeDeviceData: Boolean
        get() = liveState.hasFreshLiveData
}
