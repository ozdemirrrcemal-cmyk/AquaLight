package com.aqua.aqualight.data.devices.runtime.light

import com.aqua.aqualight.data.devices.DevicesDataStoreManager

/**
 * Single source of truth for one Light controller's live runtime state.
 */
data class LightRuntimeState(
    val deviceId: Long,
    val device: DevicesDataStoreManager.DeviceInfo? = null,
    val snapshot: LightRuntimeSnapshot? = null,
    val isRefreshing: Boolean = false,
    val isDeviceOnline: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncedAtMillis: Long? = null
) {
    val hasSnapshot: Boolean
        get() = snapshot != null
}
