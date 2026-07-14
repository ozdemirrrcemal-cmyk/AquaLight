package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/**
 * Replaces one stale reusable device session after Android's local network has returned.
 *
 * This is intentionally device-scoped: a stalled socket for one device must not interrupt other
 * devices that have already authenticated successfully after the same Wi-Fi transition.
 */
fun DeviceRuntimeRepository.reconnectAfterNetworkRestore(
    snapshot: DeviceSnapshot
): Result<Unit> {
    return runCatching {
        close(snapshot.deviceUid)
        activate(snapshot.deviceUid)
        connect(snapshot).getOrThrow()
    }
}
