package com.aqua.aqualight.application.devices.dosing

import kotlinx.coroutines.flow.Flow

enum class DeviceDosingProgramDraftMode {
    SINGLE,
    HOURLY_24,
    CUSTOM_PERIODS,
    TIMER
}

data class DeviceDosingProgramCustomPeriodDraft(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val doseCount: Int
)

data class DeviceDosingProgramTimerEventDraft(
    val timeMs: Long,
    val amountMl: Double
)

sealed interface DeviceDosingProgramDraftConfig {
    data class Distributed(
        val dailyDoseMl: Double,
        val startTimeMs: Long
    ) : DeviceDosingProgramDraftConfig

    data class CustomPeriods(
        val dailyDoseMl: Double,
        val periods: List<DeviceDosingProgramCustomPeriodDraft>
    ) : DeviceDosingProgramDraftConfig

    data class Timer(
        val events: List<DeviceDosingProgramTimerEventDraft>
    ) : DeviceDosingProgramDraftConfig
}

data class DeviceDosingProgramDraft(
    val enabled: Boolean,
    val weekdays: List<Boolean>,
    val mode: DeviceDosingProgramDraftMode,
    val missedDoseRecoveryEnabled: Boolean,
    val config: DeviceDosingProgramDraftConfig
)

data class DeviceDosingSchedulingConstraints(
    val amountResolutionMl: Double,
    val maxEventsPerChannel: Int,
    val maxCustomPeriodsPerChannel: Int,
    val missedDoseRecoveryWindowMs: Long,
    val minPumpRunDurationMs: Long,
    val maxPumpRunDurationMs: Long,
    val maxManualDoseMl: Double,
    val supportsMissedDoseRecovery: Boolean,
    val supportsChannelReset: Boolean,
    val effectiveScheduledDoseMinMl: Double?,
    val effectiveScheduledDoseMaxMl: Double?
)

data class DeviceDosingUsageSnapshot(
    val localDate: String?,
    val scheduledDeliveredMl: Double,
    val manualDeliveredMl: Double,
    val totalDeliveredMl: Double
)

data class DeviceDosingReservoirSnapshot(
    val trackingEnabled: Boolean,
    val capacityMl: Double?,
    val remainingMl: Double?,
    val accountingCertain: Boolean,
    val remainingPercent: Double?
)

data class DeviceDosingChannelProgramSnapshot(
    val revision: Long,
    val enabled: Boolean,
    val weekdays: List<Boolean>,
    val mode: DeviceDosingProgramDraftMode,
    val missedDoseRecoveryEnabled: Boolean,
    val config: DeviceDosingProgramDraftConfig
)

data class DeviceDosingChannelSnapshot(
    val deviceUid: String,
    val slotId: String,
    val channelKey: String,
    val revision: Long,
    val channelTitle: String,
    val calibrated: Boolean,
    val lastCalibratedAt: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: String,
    val program: DeviceDosingChannelProgramSnapshot?,
    val scheduling: DeviceDosingSchedulingConstraints,
    val usageToday: DeviceDosingUsageSnapshot,
    val reservoir: DeviceDosingReservoirSnapshot,
    val active: Boolean
)

sealed interface DeviceDosingChannelOperationResult {
    data class Success(val snapshot: DeviceDosingChannelSnapshot) : DeviceDosingChannelOperationResult
    data object Unavailable : DeviceDosingChannelOperationResult
    data object Failed : DeviceDosingChannelOperationResult
}

/** Application boundary for all user-facing operations on one canonical Dosing channel. */
interface DeviceDosingChannelOperations {
    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingChannelSnapshot?>

    suspend fun refresh(deviceUid: String, slotId: String): DeviceDosingChannelOperationResult

    suspend fun saveProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgramDraft
    ): DeviceDosingChannelOperationResult

    suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult

    suspend fun dispenseManualDose(
        deviceUid: String,
        slotId: String,
        amountMl: Double
    ): DeviceDosingChannelOperationResult

    suspend fun resetChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult

    suspend fun saveReservoir(
        deviceUid: String,
        slotId: String,
        trackingEnabled: Boolean,
        capacityMl: Double?
    ): DeviceDosingChannelOperationResult

    suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult
}
