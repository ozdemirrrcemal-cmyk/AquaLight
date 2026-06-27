package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.Collections

/**
 * One app-session coordinator.
 *
 * Call after WebSocket auth/provisioning handoff succeeds.
 * It prevents spamming phone.sync on every screen redraw.
 */
class DeviceTimeSyncCoordinator(
    private val repository: DeviceTimeRuntimeRepository
) {
    private val syncedDeviceUids = Collections.synchronizedSet(mutableSetOf<String>())

    fun syncPhoneNowIfNeeded(
        deviceUid: DeviceUid,
        force: Boolean = false
    ): DeviceTimeCommandResult {
        val key = deviceUid.value

        if (!force && syncedDeviceUids.contains(key)) {
            return DeviceTimeCommandResult(
                sent = false,
                skipped = true,
                action = DeviceTimeRuntimeContract.Action.PHONE_SYNC
            )
        }

        val result = repository.syncPhoneNow(
            deviceUid = deviceUid,
            save = true
        )

        if (result.isSuccess) {
            syncedDeviceUids.add(key)
        }

        return result
    }

    fun clearSessionMemory(
        deviceUid: DeviceUid
    ) {
        syncedDeviceUids.remove(deviceUid.value)
    }
}
