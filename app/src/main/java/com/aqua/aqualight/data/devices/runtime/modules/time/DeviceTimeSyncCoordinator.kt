package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Authenticated runtime-bootstrap coordinator.
 *
 * It is called only after authenticated runtime metadata has been validated. The phone's current
 * epoch and timezone are applied to the live device session without writing persistent storage.
 * Provisioning owns persistent timezone setup, while firmware NTP keeps the clock corrected.
 *
 * A previous successful sync is deliberately not remembered across later validated bootstraps.
 * An RTC-less ESP32 can reboot and lose its software wall clock while the Android process remains
 * alive, so every later bootstrap must be allowed to re-anchor device time. Only a command already
 * in flight for the same device is deduplicated.
 */
class DeviceTimeSyncCoordinator internal constructor(
    private val syncPhoneNow: suspend (DeviceUid) ->
        DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
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
    private val syncingDeviceUids = mutableSetOf<String>()

    suspend fun syncPhoneNowIfNeeded(deviceUid: DeviceUid): DeviceTimeSyncDecision {
        val key = deviceUid.value
        synchronized(lock) {
            if (key in syncingDeviceUids) {
                return DeviceTimeSyncDecision.Skipped
            }
            syncingDeviceUids += key
        }

        return try {
            DeviceTimeSyncDecision.Attempted(syncPhoneNow(deviceUid))
        } finally {
            synchronized(lock) {
                syncingDeviceUids -= key
            }
        }
    }

    fun clearSessionMemory(deviceUid: DeviceUid) {
        synchronized(lock) {
            syncingDeviceUids -= deviceUid.value
        }
    }
}

sealed interface DeviceTimeSyncDecision {
    data object Skipped : DeviceTimeSyncDecision

    data class Attempted(
        val outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
    ) : DeviceTimeSyncDecision
}
