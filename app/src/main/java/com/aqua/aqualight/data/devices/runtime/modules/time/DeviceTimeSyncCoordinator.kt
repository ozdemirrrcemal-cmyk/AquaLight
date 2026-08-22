package com.aqua.aqualight.data.devices.runtime.modules.time

import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome

/**
 * Authenticated runtime-bootstrap coordinator for the mandatory-RTC firmware.
 *
 * It is called only after authenticated runtime metadata has been validated. The existing v1
 * status is read before any mutation: an RTC-ready device with matching phone timezone policy is
 * left untouched. The existing phone.sync command is used only to establish RTC readiness or to
 * apply a timezone/auto-sync policy change, without writing persistent storage.
 *
 * A completed decision is deliberately not cached across later validated bootstraps. Firmware can
 * reboot, RTC health can change, and the phone timezone can change while this process remains alive.
 * Only one in-flight evaluation for the same device is deduplicated.
 */
class DeviceTimeSyncCoordinator internal constructor(
    private val requestStatus: suspend (DeviceUid) ->
        DeviceRuntimeCommandOutcome<DeviceTimeStatus>,
    private val syncPhoneNow: suspend (DeviceUid) ->
        DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>,
    private val currentTimeZoneSnapshot: () -> DeviceTimeZoneSnapshot =
        DeviceSystemTimePayloadFactory::currentTimeZoneSnapshot
) {
    constructor(repository: DeviceTimeRuntimeRepository) : this(
        requestStatus = repository::requestStatus,
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
            when (val statusOutcome = requestStatus(deviceUid)) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    val status = statusOutcome.value
                    val phoneZone = currentTimeZoneSnapshot()
                    if (status.requiresPhoneDiscipline(phoneZone)) {
                        DeviceTimeSyncDecision.Attempted(syncPhoneNow(deviceUid))
                    } else {
                        DeviceTimeSyncDecision.Skipped
                    }
                }

                else -> DeviceTimeSyncDecision.Skipped
            }
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

    private fun DeviceTimeStatus.requiresPhoneDiscipline(
        phoneZone: DeviceTimeZoneSnapshot
    ): Boolean = !timeSet ||
        timezoneId != phoneZone.timezoneId ||
        posixTimeZone != phoneZone.posixTimeZone ||
        utcOffsetMinutes != phoneZone.utcOffsetMinutes ||
        !autoSyncNtpEnabled ||
        !autoSyncGadgetEnabled
}

sealed interface DeviceTimeSyncDecision {
    /** No mutation was sent: status was current/unavailable, or an evaluation was in flight. */
    data object Skipped : DeviceTimeSyncDecision

    /** The existing v1 phone.sync mutation was required and attempted. */
    data class Attempted(
        val outcome: DeviceRuntimeCommandOutcome<DeviceTimeMutationResult>
    ) : DeviceTimeSyncDecision
}
