package com.aqua.aqualight.application.devices.light.adaptation

import kotlinx.coroutines.flow.Flow

/** Firmware-independent boundary for the WRGB Pro Elite acclimation program. */
interface DeviceLightAdaptationOperations {
    fun observe(deviceUid: String): Flow<DeviceLightAdaptationReadResult>

    fun current(deviceUid: String): DeviceLightAdaptationReadResult

    suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult

    suspend fun start(
        deviceUid: String,
        expectedRevision: Long,
        startPercent: Int,
        durationDays: Int
    ): DeviceLightAdaptationMutationResult

    suspend fun stop(
        deviceUid: String,
        expectedRevision: Long
    ): DeviceLightAdaptationMutationResult
}

sealed interface DeviceLightAdaptationReadResult {
    data class Available(
        val snapshot: DeviceLightAdaptationSnapshot
    ) : DeviceLightAdaptationReadResult

    data class Failed(
        val failure: DeviceLightAdaptationFailure
    ) : DeviceLightAdaptationReadResult
}

sealed interface DeviceLightAdaptationMutationResult {
    data class Success(
        val snapshot: DeviceLightAdaptationSnapshot
    ) : DeviceLightAdaptationMutationResult

    data class Failed(
        val failure: DeviceLightAdaptationFailure
    ) : DeviceLightAdaptationMutationResult
}

enum class DeviceLightAdaptationFailure {
    UNAVAILABLE,
    NOT_CONNECTED,
    UNSUPPORTED,
    STALE_REVISION,
    CLOCK_NOT_READY,
    INVALID_REQUEST,
    REJECTED,
    INVALID_DATA
}

data class DeviceLightAdaptationSnapshot(
    val deviceUid: String,
    val revision: Long,
    val state: DeviceLightAdaptationState,
    val clockReady: Boolean,
    val startPercent: Int,
    val currentPermille: Int?,
    val targetPercent: Int,
    val durationDays: Int,
    val startedAtEpochSeconds: Long?,
    val endsAtEpochSeconds: Long?,
    val remainingSeconds: Long?,
    val policy: DeviceLightAdaptationPolicy
)

data class DeviceLightAdaptationPolicy(
    val startPercentMin: Int,
    val startPercentMax: Int,
    val startPercentStep: Int,
    val defaultStartPercent: Int,
    val durationDaysMin: Int,
    val durationDaysMax: Int,
    val durationDaysStep: Int,
    val defaultDurationDays: Int,
    val targetPercent: Int
)

enum class DeviceLightAdaptationState {
    DISABLED,
    ACTIVE,
    COMPLETED
}
