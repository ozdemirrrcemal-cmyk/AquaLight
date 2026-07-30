package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * One app-session coordinator.
 *
 * It is called only after authenticated runtime metadata has been validated. The phone's current
 * epoch and timezone are applied to the live device session without writing persistent storage.
 * Provisioning owns persistent timezone setup, while firmware NTP keeps the clock corrected.
 */
class DeviceTimeSyncCoordinator internal constructor(
    private val syncPhoneNow: (DeviceUid) -> DeviceTimeCommandResult
) {
    constructor(repository: DeviceTimeRuntimeRepository) : this(
        syncPhoneNow = { deviceUid ->
            repository.syncPhoneNow(
                deviceUid = deviceUid,
                save = false
            )
        }
    )

    private val lock = Any()
    private val syncedDeviceUids = mutableSetOf<String>()
    private val syncingDeviceUids = mutableSetOf<String>()

    fun syncPhoneNowIfNeeded(
        deviceUid: DeviceUid,
        force: Boolean = false
    ): DeviceTimeCommandResult {
        val key = deviceUid.value
        synchronized(lock) {
            if (key in syncingDeviceUids || (!force && key in syncedDeviceUids)) {
                return skippedResult()
            }
            syncingDeviceUids += key
        }

        var result: DeviceTimeCommandResult? = null
        try {
            result = syncPhoneNow(deviceUid)
            return checkNotNull(result)
        } finally {
            synchronized(lock) {
                syncingDeviceUids -= key
                if (result?.isSuccess == true) {
                    syncedDeviceUids += key
                }
            }
        }
    }

    fun clearSessionMemory(deviceUid: DeviceUid) {
        synchronized(lock) {
            syncedDeviceUids -= deviceUid.value
            syncingDeviceUids -= deviceUid.value
        }
    }

    private fun skippedResult(): DeviceTimeCommandResult = DeviceTimeCommandResult(
        sent = false,
        skipped = true,
        action = DeviceTimeRuntimeContract.Action.PHONE_SYNC
    )
}
