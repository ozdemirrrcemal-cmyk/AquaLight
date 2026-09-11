package com.aqua.aqualight.application.devices.timer

import kotlinx.coroutines.flow.Flow

/** Firmware-independent application boundary for the standalone Timer control surface. */
interface DeviceTimerControlOperations {
    fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult>

    /** Returns a snapshot only when the current runtime generation is authoritative. */
    fun currentControl(deviceUid: String): DeviceTimerControlResult

    /** Refreshes the complete channel summary used to prepare the root control surface. */
    suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult

    /** Refreshes one channel including its complete schedule replacement document. */
    suspend fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceTimerControlResult

    suspend fun setRegime(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime
    ): DeviceTimerControlResult

    suspend fun setTemporaryOverride(
        deviceUid: String,
        slotId: String,
        regime: DeviceTimerChannelRegime,
        durationMillis: Long
    ): DeviceTimerControlResult

    suspend fun setDisplayName(
        deviceUid: String,
        slotId: String,
        update: DeviceTimerDisplayNameUpdate
    ): DeviceTimerControlResult

    /** Replaces the complete firmware-owned schedule set for one stable Timer channel slot. */
    suspend fun replaceSchedules(
        deviceUid: String,
        slotId: String,
        expectedRevision: Long,
        schedules: List<DeviceTimerScheduleDraft>
    ): DeviceTimerControlResult
}

sealed interface DeviceTimerControlResult {
    data class Available(
        val snapshot: DeviceTimerControlSnapshot
    ) : DeviceTimerControlResult

    data class Failed(
        val failure: DeviceTimerControlFailure
    ) : DeviceTimerControlResult
}

sealed interface DeviceTimerControlFailure {
    data object Unavailable : DeviceTimerControlFailure
    data object NotConnected : DeviceTimerControlFailure
    data object Unsupported : DeviceTimerControlFailure

    data class Rejected(
        val reason: DeviceTimerCommandFailure
    ) : DeviceTimerControlFailure

    data object InvalidData : DeviceTimerControlFailure
}
