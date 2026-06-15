package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.DevicesDataStoreManager

/**
 * Runtime read profile used by the central Light runtime session.
 *
 * LIVE is optimized for visible dashboard/manual/settings polling. STANDARD keeps
 * the normal gateway timeout budget for explicit user actions and slower links.
 */
enum class LightRuntimeReadProfile {
    STANDARD,
    LIVE
}

data class LightRuntimeDeviceSnapshot(
    val device: DevicesDataStoreManager.DeviceInfo,
    val snapshot: LightRuntimeSnapshot
)
