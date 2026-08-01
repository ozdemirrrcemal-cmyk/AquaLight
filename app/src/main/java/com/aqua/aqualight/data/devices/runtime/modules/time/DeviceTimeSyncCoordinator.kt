package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * One app-session coordinator.
 *
 * It is called only after authenticated runtime metadata has been validated. The phone's current
 * epoch and timezone are applied to the live device session without writing persistent storage.
 * Provisioning owns persistent timezone setup, while firmware NTP keeps the clock corrected.
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
    private val syncedDeviceUids = mutableSetOf<String>()
    private val syncingDeviceUids = mutableSetOf<String>()

    suspend fun syncPhoneNowIfNeeded(
        deviceUid: DeviceUid,
        force: Boolean = false
    ): DeviceTimeSyncDecision {
        val key = deviceUid.value
        synchronized(lock) {
            if (key in syncingDeviceUids || (!force && key in syncedDeviceUids)) {
                return DeviceTimeSyncDecision.Skipped
            }
            syncingDeviceUids += key
        }

        var outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>? = null
        try {
            outcome = syncPhoneNow(deviceUid)
            return DeviceTimeSyncDecision.Attempted(checkNotNull(outcome))
        } finally {
            synchronized(lock) {
                syncingDeviceUids -= key
                if (outcome is DeviceRuntimeCommandOutcome.Success) {
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
}

sealed interface DeviceTimeSyncDecision {
    data object Skipped : DeviceTimeSyncDecision

    data class Attempted(
        val outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
    ) : DeviceTimeSyncDecision
}
